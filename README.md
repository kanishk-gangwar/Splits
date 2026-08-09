# Splits

A small, sharp expense-splitting app for Android and iOS, built with **Compose Multiplatform** —
one Kotlin codebase, the same UI on both platforms.

No accounts. No phone numbers. You share a link, and whoever opens it picks their own name.

---

## What it does

**Share a link, pick your name.**
Every group carries an 8-character invite code and a link. Opening it shows the group's
participants; you tap whichever one is you, and that claim is bound to your device. A name
already claimed elsewhere shows as locked, so two people can never be the same person.

**Settlements don't count as spending.**
Every entry is either an *expense* or a *settlement*. A settlement (A pays B) moves the
balance between two people but is deliberately excluded from the group total — it isn't new
spending, it's the same money changing hands. The group header says so out loud.

**Categories, optional and extensible.**
21 built-in categories, or none at all, or type your own. Custom categories keep a stable
colour so the same label always looks the same.

**Admin deletes, everyone else hides.**
Only the member who created the group can delete it for everyone. Anyone else can *hide* it,
which removes it from their own device only — reversible from Settings. Both are separate
from *archive*, which parks a group with its history intact.

**Offline first, synced when you pull down.**
Every write hits the local database immediately — the network is never in the path of a user
action. Swipe down to reconcile with the server. Free Supabase backend; see
[SETUP.md](SETUP.md). Skip that setup entirely and the app is simply a very good offline app.

**Money that adds up.**
Amounts are integer minor units end to end; nothing is ever a `Double`. Split evenly, by
exact amounts, or by percentage — the leftover paise always land on someone rather than
quietly disappearing. This is covered by tests.

---

## Project layout

```
composeApp/
  src/commonMain/       all shared logic and the entire UI
    kotlin/…/model/     money, categories, balances, settle-up maths
    kotlin/…/data/      SQLDelight repository, device identity, platform expects
    kotlin/…/data/remote/  Supabase RPC client and wire types
    kotlin/…/data/sync/    push-then-pull reconciliation
    kotlin/…/ui/        theme, components, screens
    sqldelight/         schema and queries
  src/androidMain/      Activity, Application, Android actuals
  src/iosMain/          UIViewController entry point, iOS actuals
  src/commonTest/       money and balance tests

iosApp/                 Xcode project that hosts the shared Compose UI
backend/supabase/       schema.sql — tables, RLS, and the four RPC functions
docs/                   the invite landing page, served by GitHub Pages
```

## Stack

| | |
|---|---|
| UI | Compose Multiplatform 1.11 |
| Language | Kotlin 2.3.21 |
| Storage | SQLDelight 2.3 (offline-first, sync flags built into the schema) |
| Navigation | Jetpack Navigation (multiplatform), type-safe routes |
| Sync | Supabase (Postgres + RPC), Ktor client |
| Build | AGP 9.1, Gradle 9.3 |

## Running it

**Android** — open the project in Android Studio and run the `composeApp` configuration, or:

```bash
./gradlew :composeApp:installDebug
```

**iOS** — open `iosApp/iosApp.xcodeproj` in Xcode and run. The Kotlin framework is compiled
by a build phase, so no extra setup is needed. To run on a physical device, set `TEAM_ID` in
`iosApp/Configuration/Config.xcconfig`.

**Sync** — optional, free, and additive: see [SETUP.md](SETUP.md).

**Tests**

```bash
./gradlew :composeApp:testDebugUnitTest
```

## Going deeper

[TECHNICAL.md](TECHNICAL.md) covers the architecture, the data model, how sync
reconciles, the server security model, and the build's sharp edges.

## Notes on identity

There is no login. Each install generates a random device id, and claiming a name in a group
writes that id against the member row. This is why the invite flow needs no phone number —
and why hiding a group is a per-device decision rather than something other participants see.
