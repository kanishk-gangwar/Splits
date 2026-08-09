# Setting up sync

The app is fully usable with none of this done — it just stays local to one device. Sync is
additive, and everything below is on a free tier.

There are three pieces: a Supabase project (the database), the app config (where to find it),
and GitHub Pages (so shared invite links open something in a browser).

---

## 1. Supabase project

1. Create a free project at [supabase.com](https://supabase.com). Any region near you is fine.
2. Open **SQL Editor → New query**, paste the whole of
   [`backend/supabase/schema.sql`](backend/supabase/schema.sql), and run it.

   > The script is idempotent — `create table if not exists`, `create or replace function`.
   > **Re-run the whole file after pulling changes** to pick up new functions. `verify.sh` tells
   > you if something is missing.

   > **If your project is already running an older copy of this schema, re-run it now.** The
   > current version closes two holes that let anyone forwarded an invite link take over the
   > admin identity and permanently delete the group — see *Server security model* in
   > [TECHNICAL.md](TECHNICAL.md). It changes function signatures, so it `drop`s the old ones
   > first and app builds older than this one will not be able to claim or release a name until
   > they update. `./backend/verify.sh` proves the fix landed.
3. Go to **Project Settings → API** and copy:
   - **Project URL** — looks like `https://abcdefgh.supabase.co`
   - **anon public** key — a long JWT

### Why the anon key is safe to ship

Every table has row level security enabled with **no policies at all**, and direct grants are
revoked. That means the anon key cannot read or write a single row through PostgREST. The only
things it can call are four `SECURITY DEFINER` functions, and each already requires a secret
the caller must have been given:

| Function | What it needs to know |
|---|---|
| `splits_pull` | the 128-bit group ids |
| `splits_resolve_invite` | the 8-character invite code |
| `splits_push` | the group id it is writing into |
| `splits_delete_group` | the group id **and** ownership of the admin member's device |

The invite link *is* the capability. That is the same trust model the app shows the user, so
the server enforces exactly what the UI promises — including requirement 4, where deleting a
group is re-checked server-side rather than trusted to the client.

> A free Supabase project pauses after ~1 week with zero traffic. It resumes on the next
> request; the app treats the delay as an ordinary offline blip.

## 2. Point the app at it

Add two lines to **`local.properties`** in the project root — the same file that already holds
your Android SDK path. It is gitignored, so nothing lands in the repo:

```properties
supabase.url=https://abcdefgh.supabase.co
supabase.anonKey=eyJhbGciOi...
```

That's it. A Gradle task bakes those into the app at build time, so rebuild after editing:

```bash
./gradlew :composeApp:installDebug
```

Omit the lines entirely and `SyncEngine` reports `Disabled`, the network is never touched, and
the app runs offline-only. Nothing else changes and nothing crashes.

### Check it worked

```bash
./backend/verify.sh
```

This reads the same two properties and exercises the server the way the app does: it confirms
the functions exist, confirms the anon key **cannot** read tables directly, then creates a
throwaway group, resolves it by invite code, checks that a non-admin device is refused a
delete, and cleans up after itself. If something is wrong it tells you which of the three
setup steps to revisit.

## 3. GitHub Pages for invite links

The `docs/` folder is already a complete static site. To publish it:

**Settings → Pages → Source: Deploy from a branch → Branch: `main`, folder: `/docs` → Save.**

Within a minute the invite page is live at:

```
https://kanishk-gangwar.github.io/Splits/join/#ABCD2345
```

which is exactly what `INVITE_WEB_BASE` in
[`DeepLinks.kt`](composeApp/src/commonMain/kotlin/com/kanishk/splits/DeepLinks.kt) already
points at. If you publish under a different name, change that constant to match.

Optionally, paste the same URL and anon key into the two placeholders at the top of the
`<script>` block in `docs/join/index.html`. The page then shows the real group name and
participant list instead of just the code — handy, not required.

---

## How syncing behaves

**Local first, always.** Every write lands in SQLite immediately and the UI renders from
there. The network is never in the path of a user action, so the app is fully usable on a
plane and a failed sync loses nothing.

**Push before pull.** Each sync sends local changes first, then asks for the server's view.
Doing it in that order means last-write-wins resolves against complete information, instead
of a pull overwriting an edit the server had never been told about.

**Per-row dirty flags.** Every table carries a `dirty` column. Only rows the user actually
touched are uploaded, so a refresh on a large group is cheap.

**The known tradeoff: clocks.** `updated_at` is stamped by the device that made the edit, and
the pull watermark is the highest timestamp seen. If one phone's clock is badly wrong, its
writes can sort incorrectly against another's. For a group of friends splitting dinner this
is the right trade — it avoids a round trip for a server timestamp on every single write. If
it ever matters, stamp `updated_at` inside `splits_push` from `now()` instead.

**Deletes are permanent.** A deleted expense is removed from the table outright — nothing is
left behind flagged as deleted. Devices that were offline find out because `splits_pull` returns
the full list of ids the server still holds, and anything missing locally is dropped. This is
why re-running the schema matters: without those lists an offline phone would never learn that
something had been deleted.

**Two things never sync: `archived` and `hidden`.** They are this device's shelving
decisions. If they synced, one person archiving a trip would archive it for everyone, and a
hidden group would reappear on every refresh. `applyRemote` explicitly preserves both when
folding server data in.

**Refresh is a pull-down.** Swipe down on the groups list or inside a group. A failed sync
shows a quiet strip rather than a dialog, because the local data on screen is still correct.


---

## Distributing the app

The APK is published to **GitHub Releases**, and this link always serves the newest build:

```
https://github.com/kanishk-gangwar/Splits/releases/latest/download/Splits.apk
```

That works because every release attaches its APK under the same filename, `Splits.apk`.
Rename the asset and the permalink breaks — so keep the name stable and let the release title
carry the version.

The landing page at `docs/index.html` wraps that link in a download button, so the friendly
thing to share is:

```
https://kanishk-gangwar.github.io/Splits/
```

Nothing needs redeploying when you ship a new version. Cut a release with an asset named
`Splits.apk` and both links point at it immediately.

### If you outgrow this

**Google Play** is the step up: automatic updates, no "unknown sources" warning, and no
scary Play Protect prompt. It costs $25 once and adds a review wait on each release. One thing
to plan for — Play requires either your upload key or Play App Signing, so enrol before you
have real users, since changing an app's signing key after release means everyone reinstalls
from scratch.

**iOS has no free equivalent.** There is no sideloading path comparable to an APK. TestFlight
and the App Store both require the Apple Developer Program at $99/year. Until then the iOS
build runs on a simulator or on your own device from Xcode.
