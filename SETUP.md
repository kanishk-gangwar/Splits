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

Edit
[`composeApp/src/commonMain/kotlin/com/kanishk/splits/data/remote/SupabaseConfig.kt`](composeApp/src/commonMain/kotlin/com/kanishk/splits/data/remote/SupabaseConfig.kt):

```kotlin
object SupabaseConfig {
    const val URL: String = "https://abcdefgh.supabase.co"
    const val ANON_KEY: String = "eyJhbGciOi..."
}
```

Leave them blank and `SyncEngine` reports `Disabled`, the network is never touched, and the
app runs offline-only. Nothing else changes.

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

**Deletes are tombstones.** Rows are marked `deleted` rather than removed, so other devices
learn about a deletion on their next pull instead of silently keeping a ghost group.

**Two things never sync: `archived` and `hidden`.** They are this device's shelving
decisions. If they synced, one person archiving a trip would archive it for everyone, and a
hidden group would reappear on every refresh. `applyRemote` explicitly preserves both when
folding server data in.

**Refresh is a pull-down.** Swipe down on the groups list or inside a group. A failed sync
shows a quiet strip rather than a dialog, because the local data on screen is still correct.
