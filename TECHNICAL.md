# Splits — technical notes

Context for anyone (including future you) picking this codebase up. It covers how the thing is
put together, and more importantly *why* the awkward bits are the way they are.

- [Shape of the project](#shape-of-the-project)
- [Identity: there are no accounts](#identity-there-are-no-accounts)
- [Money](#money)
- [Splitting](#splitting)
- [Balances and settle-up](#balances-and-settle-up)
- [Data model](#data-model)
- [Sync](#sync)
- [Server security model](#server-security-model)
- [Archived means read-only](#archived-means-read-only)
- [Filtering by participant](#filtering-by-participant)
- [Notifications](#notifications)
- [UI conventions](#ui-conventions)
- [Build setup and its sharp edges](#build-setup-and-its-sharp-edges)
- [Testing](#testing)
- [Known gaps](#known-gaps)

---

## Shape of the project

One Gradle module, `:composeApp`, holding everything. Android and iOS share not just the logic
but the entire UI — there are no platform-specific screens.

```
composeApp/src/
  commonMain/kotlin/com/kanishk/splits/
    App.kt                  composition root: database, sync engine, theme, NavHost
    DeepLinks.kt            invite link parsing and formatting
    model/                  pure Kotlin. No Compose, no SQL, no coroutines
      Models.kt             Group, Member, Expense, Split, GroupDetail
      Money.kt              minor-unit arithmetic and formatting
      SplitPlan.kt          turning form input into per-member shares
      Balances.kt           net positions and settle-up suggestions
      Categories.kt         built-in + custom categories
      Dates.kt              day grouping and relative formatting
      GroupCard.kt          the home screen's row model
    data/
      SplitsRepository.kt   the only thing that touches the database
      Ids.kt                id and invite-code generation
      Platform.kt           expect: driver, share sheet, clipboard, device label
      remote/               Supabase RPC client and wire types
      sync/SyncEngine.kt    push-then-pull reconciliation
    ui/
      theme/                colour, type, shapes, palettes
      components/           avatars, cards, money text, refresh, top bars
      groups/ group/ expense/ join/ settings/     one package per screen
    sqldelight/…/Splits.sq  schema and every query
  androidMain/              Activity, Application, Android actuals
  iosMain/                  ComposeUIViewController entry point, iOS actuals
  commonTest/               pure-logic tests: money, splits, balances, filtering

iosApp/                     Xcode project hosting the shared UI
backend/supabase/schema.sql tables, RLS, and the four RPC functions
backend/verify.sh           end-to-end check of a Supabase project
docs/                       invite landing page (GitHub Pages)
```

The `model` package is deliberately dependency-free. That is what makes the interesting parts
testable without an emulator, which matters a lot here — see [Testing](#testing).

---

## Identity: there are no accounts

This is the decision everything else bends around.

There is no login, no email, no phone number. On first launch the app generates a random
128-bit device id and stores it in the `prefEntity` table. Joining a group means claiming a
participant row: `memberEntity.claimedByDeviceId = <this device>`.

Consequences, all intentional:

- **An invite link is a capability.** Anyone holding the 8-character code can see the group and
  claim an unclaimed name. There is nothing else guarding it, and the server enforces exactly
  that and no more.
- **Claiming is exclusive per group.** `claimMember` releases any other name this device holds
  in that group first, so one device can never be two people in the same group.
- **"Admin" is a member id, not a user.** `groupEntity.adminMemberId` points at a member row;
  you are admin if your device has claimed that row. This is what
  `splits_delete_group(p_group_id, p_device_id)` re-checks server-side.
- **Losing the device loses the identity.** There is no recovery. This was the accepted cost of
  not asking for a phone number. Adding optional email recovery later would mean introducing
  Supabase Auth and a `user_id` column alongside `claimed_by_device_id`, not replacing it.

---

## Money

Every amount in the system is a `Long` count of **minor units** — paise, cents. There is no
`Double`, `Float`, or `BigDecimal` anywhere in the money path. Floating-point drift on split
arithmetic is the one class of bug users actually notice ("why is this 0.01 off?").

`Money.kt` owns:

- `formatMinor` — renders with a currency symbol, and groups digits the **Indian** way for INR,
  NPR and LKR (`12,34,567`) versus the western way otherwise (`1,234,567`).
- `parseAmountToMinor` — tolerant of half-typed input. Returns `null` for "not a usable number
  yet", which is meaningfully different from `0`. `SplitPlan` leans on that distinction.
- `minorToEditText` — the inverse, for pre-filling fields.
- `splitEvenly(total, n)` and `splitByWeights(total, weights)` — both guarantee the parts sum
  back to the total exactly. The leftover units go to whoever was rounded down hardest, rather
  than being dropped.

---

## Splitting

`model/SplitPlan.kt`, `planSplits(...)`. Lives outside the Compose layer specifically so it can
be tested; `ExpenseEditorScreen` is a thin shell over it.

Three modes, and one rule that ties them together:

> **A row the user has not typed into is an automatic row.** Whatever is left over after the
> pinned rows is spread evenly across the floating ones.

So typing `300` against one of three people on a ₹900 expense yields 300/300/300, not
300/0/0. In the UI an empty field shows its computed share as a greyed placeholder, so you can
see at a glance what you pinned versus what the app worked out around you.

This is why `parseAmountToMinor` returning `null` for an empty string matters: blank means "no
opinion", not zero. Clearing a field hands that row back to automatic. Unchecking a participant
also drops their pinned value, otherwise a number from a row that is no longer in the split
keeps consuming part of the total.

Percentages are held in **hundredths of a percent** (`FULL_PERCENT = 10_000`), so 33.33% is
representable. When the weights land on exactly 100% the shares are reconciled to the paisa;
when they do not, shares are computed literally so the over- or under-allocation stays visible
rather than being silently normalised into a plausible-looking split. The editor surfaces the
difference as "₹X still unassigned" / "₹X over the total".

---

## Balances and settle-up

`model/Balances.kt`. `summarise(members, expenses)` does one pass and returns everything the
group screen needs.

For every entry, regardless of kind:

- the payer is credited the full amount
- each split member is debited their share

The **only** difference for a reimbursement is that it does not add to `totalSpentMinor`:

```kotlin
if (expense.kind == ExpenseKind.EXPENSE) {
    totalSpent += expense.amountMinor
}
```

That single condition is requirement 2 in its entirety. A settlement is modelled as a one-sided
split — the whole amount lands on the person being paid — which is exactly what cancels the
debt without counting as new spending. `BalancesTest` pins this down: pay for dinner, settle
up, and the group total stays at the dinner while both balances go to zero.

`suggestSettlements` is greedy min-cash-flow: repeatedly match the largest debtor against the
largest creditor. It produces at most *n − 1* transfers, which is what people want to see —
not a literal edge for every pairwise debt. A test applies every suggested transfer and asserts
nobody is left owing anything.

Balances across a group always net to zero. That is a useful invariant to assert against if you
change any of this.

---

## Data model

SQLite via SQLDelight. `commonMain/sqldelight/…/Splits.sq` is the single source of truth for
both schema and queries.

| Table | Notes |
|---|---|
| `groupEntity` | carries `archived`, `hidden`, `deleted`, `dirty` |
| `memberEntity` | `claimedByDeviceId` is the whole identity system |
| `expenseEntity` | `kind` is `EXPENSE` or `REIMBURSEMENT` |
| `splitEntity` | `(expenseId, memberId)` composite key, no timestamps of its own |
| `prefEntity` | device id, display name, theme, sync watermark |

Two details worth knowing before you edit the schema:

- **`colorIndex` is a plain `INTEGER`, not `INTEGER AS Int`.** The `AS Int` form makes
  SQLDelight demand a `ColumnAdapter` on the database constructor. It is stored as `Long` and
  narrowed in the mapper.
- **Splits have no `updatedAt`.** They travel with their expense: on both push and pull the
  whole share set for an expense is deleted and rewritten. A row-by-row upsert would leave a
  stale share behind when an edit removes a participant.

`SplitsRepository` is the only class that touches queries. It exposes `Flow`s built with
`asFlow().mapToList(dispatcher)`, so the UI re-renders whenever the database changes — there is
no manual refresh path inside the app.

`observeGroupCards()` loads all groups, members, expenses and splits and folds them in memory
rather than issuing a query per card. At this data size (a handful of groups, a few hundred
expenses) that is both simpler and faster. It would need revisiting at a few thousand expenses.

---

## Sync

`data/sync/SyncEngine.kt`. Optional: with no Supabase credentials the engine reports `Disabled`
and never touches the network. The app is a complete offline app in that state, which is also
how it ships when someone clones the repo without secrets.

**Local first, always.** Every write lands in SQLite immediately and the UI renders from there.
The network is never in the path of a user action, so a failed sync loses nothing and the app
works fine on a plane.

**Push before pull.** Each sync uploads local changes first, then asks for the server's view.
Order matters: pulling first would let the server overwrite an edit it had never been told
about. Pushing first means last-write-wins resolves against complete information.

**Per-row dirty flags.** Every table has a `dirty` column, set on write and cleared after a
successful push. Only touched rows go up.

**Deletes are tombstones, then reclaimed.** Rows are marked `deleted` rather than removed, so
other devices learn about a deletion on their next pull instead of keeping a ghost group
forever. The tombstone *is* the message: a device that has not synced since before the deletion
pulls `updated_at > watermark`, and if the row were simply gone, nothing in that response would
tell it to drop its local copy.

So storage is reclaimed on a delay rather than immediately:

- **Share rows go straight away.** They carry no deletion signal of their own — the expense's
  tombstone already says everything — so they are deleted the moment the expense is, on both
  the device and the server. In practice this is most of the row count.
- **Tombstones are purged after 30 days**, by `splits_purge_deleted(retention_days)` on the
  server and `purgeLocalTombstones` on the device. `SyncEngine` calls both at most once a day,
  after a successful pull, and swallows failures — reclaiming disk is housekeeping and is not
  worth failing a sync over. The server function clamps the window to a minimum of 7 days so a
  caller cannot purge tombstones nobody has seen yet.
- **Locally, `dirty = 0` guards every purge.** A deletion this device has not managed to push
  yet is the only record that it happened; purging it would resurrect the row on the next pull.

**`archived` and `hidden` never sync.** They are this device's shelving decisions. If they
synced, one person archiving a trip would archive it for everyone, and a hidden group would
reappear on every refresh. `setGroupArchived` and `setGroupHidden` deliberately do not set
`dirty`, and `applyRemote` explicitly preserves both when folding server data in:

```kotlin
archived = local?.archived ?: false,
hidden   = local?.hidden ?: false,
```

That is easy to break by "tidying up" the upsert. Do not.

**The known tradeoff: clocks.** `updatedAt` is stamped by the device that made the edit, and
the pull watermark is the highest timestamp seen. A badly wrong device clock can sort its
writes incorrectly against another's. For friends splitting dinner this is the right trade —
it avoids a server round trip for a timestamp on every write. If it ever matters, stamp
`updated_at` inside `splits_push` from `now()` instead.

---

## Server security model

The app has no accounts, so there is no `auth.uid()` to scope rows by. Rather than write
permissive RLS policies and hope, the schema does the opposite:

1. Every table has **RLS enabled with no policies at all**, and direct grants are revoked from
   `anon` and `authenticated`. The publishable key cannot read or write a single row through
   PostgREST. `backend/verify.sh` asserts this — a direct table read returns HTTP 401.
2. All traffic goes through four `SECURITY DEFINER` functions, each of which already requires a
   secret the caller must have been given:

| Function | Requires |
|---|---|
| `splits_pull(group_ids, since)` | the 128-bit group ids |
| `splits_resolve_invite(code)` | the 8-character invite code |
| `splits_push(payload)` | the group id it writes into |
| `splits_delete_group(group_id, device_id)` | the group id **and** ownership of the admin member's device |

This means **the publishable key is safe inside the shipped APK** — and it is in there, so
treat it as public. The security rests on those grants. Never add a permissive policy to those
tables, and never put the `sb_secret_…` / `service_role` key in the app.

`splits_delete_group` is the one place the server refuses to trust the client. The UI hides the
delete action from non-admins, but the function independently checks that the calling device id
matches the device that claimed `admin_member_id`. Requirement 4 therefore holds even against a
tampered client.

---

## Archived means read-only

Archiving parks a group with its history intact. From 1.0.3 it also freezes it: no new
expenses, no edits, no participant changes, no settle-up recording, and the group's name,
emoji and currency are locked.

What stays available is everything that gets you *out* of the state — unarchive, hide, share
the invite, and (for the admin) delete. Locking those would be a trap.

The check is `group.archived`, applied at three levels rather than one, because hiding a button
is not the same as preventing an action:

- `GroupScreen` hides the FAB, drops the click handler on every expense row, and hides the
  settle-up buttons.
- `GroupSettingsScreen` disables the detail fields and the participant controls, and skips the
  name autosave.
- `ExpenseEditorScreen` refuses to enable Save at all. A stale back stack could still land
  someone on that form, and it is the only place that actually writes.

Archiving is per-device (see [Sync](#sync)), so one person archiving does not freeze the group
for everyone else.

## Filtering by participant

The expenses tab carries a chip row: *Everyone* plus one chip per member, the device owner
first because "what was I part of" is the common question.

"Involved" deliberately means **either** direction — they paid for it, or they owe a share of
it:

```kotlin
expense.paidByMemberId == filterMemberId ||
    expense.splits.any { it.memberId == filterMemberId }
```

Each expense row also shows an avatar stack of everyone in that entry, so you can see who is
involved without opening it. When the split covers the whole group it collapses to the word
"everyone" rather than listing every name.

## Notifications

Local notifications, raised by `SyncEngine.announce` after a pull discovers expense changes.

`SplitsRepository.applyRemote` returns a list of `ExpenseNotice` describing what actually
changed, gathered *before* each row is overwritten so the added-versus-updated distinction
survives. Three guards keep them from becoming noise:

- Expenses this device just pushed are passed in as `ignoreExpenseIds` and skipped — you should
  never be notified about your own edit coming back.
- Nothing fires when `lastPulledAt == 0`. A first sync pulls the entire history, and announcing
  all of it would be useless.
- One change gets a detailed line; several collapse into a single summary.

Notification ids are derived from the expense id, so editing an entry replaces its earlier
notification instead of stacking a second one.

**The limitation, stated plainly: these are local notifications, not push.** They arrive when
the app syncs — on launch, or on pull-to-refresh. A phone with the app closed will not be told
anything. Real push needs FCM and APNs plus a server holding device tokens, which is beyond
what a free Supabase project provides. Delivering it would mean a Supabase Edge Function
triggered on insert, an FCM project, an Apple push key, and a token table.

Android needs the runtime `POST_NOTIFICATIONS` permission from API 33; `MainActivity` asks on
launch and `showNotification` checks before posting, so a refusal degrades to silence rather
than a crash. iOS asks through `UNUserNotificationCenter` from the shared entry point. There is
a toggle in Settings.

## UI conventions

- **Material 3 with a custom scheme.** Violet primary, mint for "you are owed", coral for "you
  owe". Material has no slot for "this is good news", so `MoneyColors` is carried alongside the
  colour scheme via `LocalMoneyColors`, and `balanceColor(net)` is the single place that
  decides what a signed balance means.
- **Cards are flat.** One treatment everywhere: `SplitsCard` — surface fill, hairline outline,
  20dp radius. No elevation.
- **State lives in `remember` + repository `Flow`s.** There are no ViewModels. At this size a
  screen reading flows directly is less indirection, and persistence is handled by the database
  rather than by surviving process death.
- **Navigation is type-safe.** `@Serializable` route objects in `ui/Navigation.kt` with
  `composable<Route>` and `entry.toRoute<Route>()`.
- **Keyboard insets: use one combined inset, not two.** `WindowInsets.ime` already spans the
  navigation bar, so `.navigationBarsPadding().imePadding()` lifts a bottom bar a nav-bar's
  height too high and drops it on top of the content. Both bottom bars use
  `.windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))`. This was a real
  shipped bug.
- **The amount field is pinned outside the scrolling area** in the expense editor. It is the
  one field you always want visible while typing; inside the `LazyColumn` it could scroll away
  or end up behind the save button.
- **A scrollable list under a bottom bar must include `padding.calculateBottomPadding()` in its
  `contentPadding`.** Otherwise its viewport runs on behind the bar and the keyboard, and
  Compose's automatic "scroll the focused field into view" concludes the field is already
  visible and does nothing. This is what left the exact-split fields hidden behind the keyboard
  even after the bottom bar itself was fixed — two separate bugs with the same symptom.

---

## Build setup and its sharp edges

Kotlin 2.3.21 · Compose Multiplatform 1.11.1 · AGP 9.1.1 · Gradle 9.3.1 · SQLDelight 2.3.2 ·
Ktor 3.5.2 · compileSdk 37 · minSdk 26.

Four things that will confuse you if you hit them cold:

1. **`android.builtInKotlin=false` and `android.newDsl=false` in `gradle.properties`.** AGP 9
   refuses to apply `com.android.application` alongside `org.jetbrains.kotlin.multiplatform` in
   one module without these. The documented alternative is splitting into a KMP library module
   plus a thin Android app module; these two flags keep the single-module layout the KMP wizard
   produces. If a future AGP drops them, that split is the migration.
2. **No `iosX64` target.** Compose Multiplatform 1.11 stopped shipping Intel-simulator
   artifacts. Only `iosArm64` and `iosSimulatorArm64` are declared.
3. **Supabase credentials are generated, not committed.** A Gradle task reads `supabase.url`
   and `supabase.anonKey` from `local.properties` (gitignored) and writes `GeneratedConfig.kt`
   into `build/`. Absent values produce empty strings, so a fresh clone builds and simply runs
   offline. `SupabaseConfig` reads through `val … get()` rather than `const val` because the
   values are no longer compile-time constants of that file.
4. **Release signing comes from `keystore.properties`**, also gitignored, alongside
   `splits-release.keystore`. **Back both up.** Losing them means never being able to ship an
   update over an installed copy. If the file is missing the release build still succeeds and
   produces unsigned output.

R8 is off. The Compose/SQLDelight/Ktor combination needs keep rules that have not been
exercised here, and a smaller build that crashes is worse than a larger one that works. Turning
it on is a real task with real testing, not a flag flip.

---

## Testing

All tests in `commonTest` are pure logic:

| Suite | Covers |
|---|---|
| `MoneyTest` | formatting, parsing, even and weighted splits reconciling across many shapes |
| `SplitPlanTest` | pinned-versus-automatic rows, percent handling, settlement shape |
| `ExpenseFilterTest` | participant filtering matches both payers and share-holders |
| `BalancesTest` | reimbursement excluded from total, balances netting to zero, settle-up clearing every debt |

```bash
./gradlew :composeApp:testDebugUnitTest
```

`backend/verify.sh` covers the server: that the functions exist, that direct table access is
refused, and a full push → invite-resolve → admin-check → delete round trip that cleans up
after itself.

**What is not covered:** there are no UI tests and no instrumented tests. Screens, navigation,
and the client half of sync are verified only by compiling and by running the app manually. If
you are adding logic worth trusting, put it in `model/` where it can be tested, and keep the
composable a shell over it — that is exactly how `SplitPlan` came to exist.

---

## Known gaps

- **No UI or instrumentation tests.** The riskiest untested surface is the sync client.
- **`observeGroupCards()` loads everything.** Fine now; revisit at a few thousand expenses.
- **No conflict UI.** Sync resolves silently by last-write-wins. Two people editing the same
  expense offline means one loses without being told.
- **Notifications only arrive while the app syncs.** See [Notifications](#notifications).
- **No multi-currency conversion.** Groups are single-currency, and the home screen lists
  positions per currency rather than summing them, because summing rupees and dollars would
  produce a confident wrong number.
- **Deleting a member is blocked once they appear in any expense.** There is no reassign flow.
- **iOS is unexercised.** The target compiles as part of the Kotlin build, but the Xcode project
  was authored without Xcode available and has had no runtime testing.
- **Identity cannot move devices.** See [Identity](#identity-there-are-no-accounts).
