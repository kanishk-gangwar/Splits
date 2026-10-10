-- One-off: free every claimed name in ONE group, chosen by its invite code.
--
-- When: a group's names are stuck under device ids nobody has any more. 1.7.0 was released
-- without a server configured, and on the phones that installed it the device id switched
-- locally while the server kept every name under the old one. Those people now see their own
-- name as taken. If the admin's name is among them, the admin cannot free the others from
-- inside the app, because admin rights *are* that claim.
--
-- What it does: clears the claim on every member of the group. Participants, expenses,
-- balances and which member is admin are all left exactly as they are.
--
-- Afterwards: the ADMIN opens the invite link first and picks their own name — admin rights go
-- to whichever device claims the admin's name. Everyone else picks theirs after.
--
-- Run it in the Supabase SQL editor once everyone in the group is on 1.7.1 or later.

update public.splits_members m
set claimed_by_device_id = null,
    updated_at = (extract(epoch from now()) * 1000)::bigint
from public.splits_groups g
where g.id = m.group_id
  and g.invite_code = upper(replace('PUT-INVITE-CODE-HERE', ' ', ''))
  and m.claimed_by_device_id is not null
returning m.name;
