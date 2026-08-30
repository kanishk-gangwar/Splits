## What each person actually spent

A group could tell you what it cost in total and what you were owed, and nothing in between.
Those two numbers hide the one most people are actually after — *what did this trip cost me?*
A net balance of zero reads the same whether you spent nothing or spent four thousand and were
paid back every rupee of it.

**The group header now carries three figures.** Total spent, **your share** of it, and **what
you paid**. Your share is your part of the bills; what you paid is what left your pocket at
the till. The difference between them is your balance, which is the strip underneath — now
tinted green or red rather than plain grey, so the direction reads before the number does.

**Every member's spend is listed.** The Balances tab shows `spent … · paid …` under each name,
and the People tab shows each person's share. The per-person spends add up to the group total
exactly; settlements are left out of both, because moving money between two people is not new
spending.

## Settle up in the fewest payments, properly

The settle-up list claimed to be the minimum number of payments and wasn't.

It used greedy min-cash-flow — match the biggest debtor to the biggest creditor, repeat —
which is what most splitting apps ship. It is not minimal, and not rarely: on random groups it
overshoots about one time in six. It cannot see debts that cancel between two people on their
own, so on balances of `+4, +3, −1, −3, −3` it spends four payments where three do the job.

The suggestion is now the true minimum. Members are first split into as many independent
zero-sum groups as possible — each split saves exactly one payment — and matched inside each
group, where greedy is optimal. The result is checked against a brute-force reference across
three thousand randomised groups: the same count every time, and every debt actually cleared.

Groups that were already optimal are unchanged; you will see this on the ones that weren't.

## Smaller things

- Expense rows no longer repeat the participants' names beside their faces — the avatars
  already say who, so the line just says "everyone" or how many people.
- Settle-up rows read `Ambrish pays Ayush` instead of `Ambrish → Ayush`, with the amount at
  full strength rather than muted.
- The People tab drops the check-and-clock icons, which only repeated the section heading
  they sat under.
