## Two people can record the same payment

You and a friend both look at the same line on the Settle up tab — *Aarav pays Bhavna ₹500* —
and you both tap Record. Until now that wrote two settlements for one payment. The group came
out ₹500 wrong, Bhavna looked overpaid, and nothing on either phone said which of the two rows
was the duplicate. Being offline made it worse: neither phone could see the other's entry, so
there was never a moment where a warning could have appeared.

**It now lands as one settlement.** A payment recorded from the Settle up tab no longer gets a
random identity — it gets one worked out from the payment itself: the group, who pays whom, how
much, and the day. Both phones arrive at the same answer independently, so the two saves are
the same entry rather than two, whether they happen a second apart or a day apart on a plane.

Recording a genuine second payment still works exactly as you would expect. If the first one is
already on your phone, the second is a new entry; and if you edit the amount or either person
before saving, it is a different payment and is kept as one.

## The admin can free a name

A name belongs to the phone that claimed it, and only that phone could give it back. That is
the right rule until the phone is lost, sold, or wiped — and then the name is stuck for good.
Its owner cannot take it again on their new phone, nobody else can take it either, and the
group is left with a participant nobody can be.

**Group settings now lets the admin hand any claimed name back to the pool.** Tap the unlock
icon on a participant, confirm, and the name is free for whoever holds the invite to claim —
including its original owner on their new phone.

Nothing is deleted. The participant, every expense they are part of, and their balance are
exactly as they were; the only thing that changes is who is holding the name. The phone that
held it drops back to "pick who you are" the next time it syncs.

Like renaming and removing, this is the admin's alone, and the server checks it independently —
hiding a button is not the same as preventing an action. Giving up **your own** name is still
its own separate button, and still says so.

## Also

If you run your own Supabase project, re-apply `backend/supabase/schema.sql` before installing
this build: `splits_push` carries the new rule that lets an admin release a claim, and without
it the freed name reappears as claimed on the next sync. Re-running the file is safe — no table
or row is touched. `backend/verify.sh` now checks both halves of that rule: that an ordinary
member cannot free somebody else's name, and that a name the admin frees really can be claimed
again.
