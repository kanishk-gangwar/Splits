## Your entries, your name, your way of settling up

**Only you can change what you added.** An expense or settlement can now be edited or deleted
only by the person who added it, or by the group admin. Everyone else sees it as a plain row,
and opening it says who added it. Entries from before this version count as the payer's.

**Pick a name before you write.** Having the invite link lets you see a group. Adding expenses,
recording settlements or renaming the group now needs you to pick which participant you are
first.

**Reinstalling no longer loses your name.** Your device id used to live in the app's own data,
so uninstalling took it — and every name you had claimed — with it. It is now tied to the
device itself. Reinstall, open the invite again, and your name is still yours. This install
moves over automatically on its next sync. Names already lost to an earlier reinstall still
need the admin to free them once.

**Simplify debts, or don't.** Group settings has a new *Simplify debts* switch for the admin.
On (the default) the Settle up tab shows the fewest payments that clear the group. Off, it
shows every debt between each pair of people, so you only ever pay someone you actually split
with.

## Also

- The note field on the expense and settlement screens no longer hides behind the keyboard.
- The cursor in the amount field is full size from the start instead of growing on the first
  digit.

**Server update required.** Re-run `backend/supabase/schema.sql` in the Supabase SQL editor.
It adds two columns and one function, and moves the new rules server-side. The app keeps
working against the old schema, but the settle-up setting will not sync and a reinstall will
not recover names until it is applied.
