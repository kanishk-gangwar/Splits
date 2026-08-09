#!/usr/bin/env bash
#
# Checks that a Supabase project is wired up correctly, before you spend time wondering
# why the app isn't syncing.
#
#   ./backend/verify.sh
#
# Reads supabase.url and supabase.anonKey from local.properties. Creates one throwaway group
# on the server, reads it back, then deletes it — so it leaves nothing behind.
#
# The "Attack" section is the important one. It replays, against your real project, the two
# ways an earlier version of schema.sql let anyone holding an invite link take over and destroy
# a group. Every check there must FAIL to achieve anything. If one of them starts succeeding,
# the schema has regressed and the app's whole security model is gone.

set -uo pipefail
cd "$(dirname "$0")/.." || exit 1

PROPS="local.properties"
pass=0
fail=0

say()  { printf '%s\n' "$*"; }
ok()   { printf '  \033[32mPASS\033[0m  %s\n' "$*"; pass=$((pass+1)); }
bad()  { printf '  \033[31mFAIL\033[0m  %s\n' "$*"; fail=$((fail+1)); }

if [ ! -f "$PROPS" ]; then
  say "No local.properties found. Create it with:"
  say ""
  say "  supabase.url=https://YOURPROJECT.supabase.co"
  say "  supabase.anonKey=eyJhbGciOi..."
  exit 1
fi

URL=$(grep -E '^supabase\.url=' "$PROPS" | head -1 | cut -d= -f2- | tr -d '[:space:]')
KEY=$(grep -E '^supabase\.anonKey=' "$PROPS" | head -1 | cut -d= -f2- | tr -d '[:space:]')

if [ -z "$URL" ] || [ -z "$KEY" ]; then
  say "local.properties is missing supabase.url or supabase.anonKey."
  say "Find both under Supabase → Project Settings → API."
  exit 1
fi

URL="${URL%/}"
say "Project: $URL"
say ""

rpc() { # rpc <function> <json-body>
  curl -sS -X POST "$URL/rest/v1/rpc/$1" \
    -H "apikey: $KEY" \
    -H "Authorization: Bearer $KEY" \
    -H "Content-Type: application/json" \
    -d "$2" 2>&1
}

# Device ids are 32 lower-case hex characters and the server now enforces that shape, so the
# script has to look like a real device rather than using a readable label.
hexid() { LC_ALL=C tr -dc '0-9a-f' </dev/urandom | head -c 32; }

# --------------------------------------------------------------- reachability --

say "Connection"
probe=$(rpc splits_pull '{"p_group_ids":[],"p_since":0}')
if printf '%s' "$probe" | grep -q '"groups"'; then
  ok "splits_pull responds — schema.sql has been applied"
else
  bad "splits_pull did not respond as expected"
  say "        $probe"
  if printf '%s' "$probe" | grep -qi 'could not find\|does not exist\|PGRST202'; then
    say ""
    say "  The functions are missing. Open Supabase → SQL Editor → New query,"
    say "  paste all of backend/supabase/schema.sql, and run it."
  elif printf '%s' "$probe" | grep -qi 'invalid.*key\|JWT\|401'; then
    say ""
    say "  The anon key looks wrong. Copy the 'anon public' key from"
    say "  Project Settings → API — not the service_role key, and not the project password."
  fi
  say ""
  say "$pass passed, $fail failed"
  exit 1
fi

# ------------------------------------------------------------------- security --

say ""
say "Security"
for table in splits_groups splits_members splits_expenses splits_shares splits_invite_attempts; do
  direct=$(curl -sS -o /dev/null -w '%{http_code}' "$URL/rest/v1/$table?select=*" \
    -H "apikey: $KEY" -H "Authorization: Bearer $KEY" 2>&1)
  if [ "$direct" = "200" ]; then
    bad "the anon key can read $table directly — RLS is not doing its job"
    say "        Re-run the 'alter table ... enable row level security' and 'revoke' lines."
  else
    ok "direct reads of $table are refused (HTTP $direct)"
  fi
done

# splits_purge_deleted sweeps every group in the project and asks for no secret at all, so the
# publishable key — which ships inside the APK and is public — must not be able to call it.
purge_anon=$(rpc splits_purge_deleted '{"p_retention_days":30}')
if printf '%s' "$purge_anon" | grep -q '"ok":[ ]*true'; then
  bad "anon can call splits_purge_deleted — that is a global delete for anyone with the APK"
  say "        Re-run the 'revoke all on function public.splits_purge_deleted' line."
else
  ok "splits_purge_deleted is not callable with the publishable key"
fi

missing=$(rpc splits_resolve_invite '{"p_invite_code":"ZZZZZZZZ"}')
if printf '%s' "$missing" | grep -q '"found":[ ]*false'; then
  ok "an unknown invite code resolves to not-found rather than leaking anything"
else
  bad "unexpected reply for an unknown invite code: $missing"
fi

# ---------------------------------------------------------------- round trip --

say ""
say "Round trip"
SUFFIX=$(date +%s)
GID="verify0000000000000000000000$SUFFIX"
MID="verifymember00000000000000$SUFFIX"
CODE=$(LC_ALL=C tr -dc 'ABCDEFGHJKMNPQRSTUVWXYZ23456789' </dev/urandom | head -c 12)
NOW=$(( $(date +%s) * 1000 ))
ADMIN_DEVICE=$(hexid)
ATTACKER_DEVICE=$(hexid)

push=$(rpc splits_push "{\"p_device_id\":\"$ADMIN_DEVICE\",\"p_payload\":{
  \"groups\":[{\"id\":\"$GID\",\"name\":\"Verify script\",\"emoji\":\"🧪\",
    \"currency_code\":\"INR\",\"invite_code\":\"$CODE\",\"admin_member_id\":\"$MID\",
    \"created_at\":$NOW,\"updated_at\":$NOW,\"deleted\":false}],
  \"members\":[{\"id\":\"$MID\",\"group_id\":\"$GID\",\"name\":\"Verifier\",\"color_index\":0,
    \"claimed_by_device_id\":\"$ADMIN_DEVICE\",\"created_at\":$NOW,\"updated_at\":$NOW,\"deleted\":false}],
  \"expenses\":[],\"shares\":[]}}")

if printf '%s' "$push" | grep -q '"ok":[ ]*true'; then
  ok "splits_push accepted a group"
else
  bad "splits_push failed: $push"
fi

back=$(rpc splits_resolve_invite "{\"p_invite_code\":\"$CODE\",\"p_device_id\":\"$ADMIN_DEVICE\"}")
if printf '%s' "$back" | grep -q '"Verify script"'; then
  ok "the invite code resolves back to the group — this is what a shared link does"
else
  bad "invite lookup did not return the group: $back"
fi

if printf '%s' "$back" | grep -q "$ADMIN_DEVICE"; then
  ok "a device sees its own claim, so it can still tell which participant it is"
else
  bad "the admin device did not get its own claim back — identity would break: $back"
fi

# ---------------------------------------------------------------------- attack --
#
# Everything below is the attacker's point of view: somebody who was forwarded the invite link
# and nothing else. That is a realistic amount to know — invites travel through group chats.

say ""
say "Attack (all of these must be refused)"

stolen=$(rpc splits_resolve_invite "{\"p_invite_code\":\"$CODE\",\"p_device_id\":\"$ATTACKER_DEVICE\"}")
if printf '%s' "$stolen" | grep -q "$ADMIN_DEVICE"; then
  bad "resolving the invite handed out the admin's device id — group deletion is wide open"
  say "        splits_pull must project members column by column, never to_jsonb(m)."
else
  ok "the admin's device id is not disclosed to anyone else"
fi

if printf '%s' "$stolen" | grep -q 'someone-else'; then
  ok "other devices' claims come back as an opaque marker, so names still read as taken"
else
  bad "expected the claim sentinel in the invite response: $stolen"
fi

refused=$(rpc splits_delete_group "{\"p_group_id\":\"$GID\",\"p_device_id\":\"$ATTACKER_DEVICE\"}")
if printf '%s' "$refused" | grep -q 'not_admin'; then
  ok "delete is refused for a device that holds nothing"
else
  bad "a non-admin device was not refused: $refused"
fi

sentinel_delete=$(rpc splits_delete_group "{\"p_group_id\":\"$GID\",\"p_device_id\":\"someone-else\"}")
if printf '%s' "$sentinel_delete" | grep -q 'not_admin'; then
  ok "the sentinel cannot be replayed as a device id"
else
  bad "the claim sentinel was accepted as a device id: $sentinel_delete"
fi

# Take over the admin's name by pushing a claim for it, then delete. This is the second path,
# and it works even if the device id is never disclosed.
LATER=$(( NOW + 60000 ))
rpc splits_push "{\"p_device_id\":\"$ATTACKER_DEVICE\",\"p_payload\":{
  \"members\":[{\"id\":\"$MID\",\"group_id\":\"$GID\",\"name\":\"Verifier\",\"color_index\":0,
    \"claimed_by_device_id\":\"$ATTACKER_DEVICE\",\"created_at\":$NOW,\"updated_at\":$LATER,
    \"deleted\":false}]}}" >/dev/null

hijacked=$(rpc splits_delete_group "{\"p_group_id\":\"$GID\",\"p_device_id\":\"$ATTACKER_DEVICE\"}")
if printf '%s' "$hijacked" | grep -q 'not_admin'; then
  ok "a claim already held by another device cannot be seized by pushing over it"
else
  bad "the admin's identity was stolen through splits_push: $hijacked"
  say "        Check the claimed_by_device_id CASE in splits_push's ON CONFLICT clause."
fi

# Flagging the group deleted is as destructive as deleting it — every other device honours the
# flag on its next pull — so it has to be gated the same way.
rpc splits_push "{\"p_device_id\":\"$ATTACKER_DEVICE\",\"p_payload\":{
  \"groups\":[{\"id\":\"$GID\",\"name\":\"Verify script\",\"emoji\":\"🧪\",
    \"currency_code\":\"INR\",\"invite_code\":\"$CODE\",\"admin_member_id\":\"$MID\",
    \"created_at\":$NOW,\"updated_at\":$LATER,\"deleted\":true}]}}" >/dev/null

flagged=$(rpc splits_pull "{\"p_group_ids\":[\"$GID\"],\"p_since\":0,\"p_device_id\":\"$ADMIN_DEVICE\"}")
if printf '%s' "$flagged" | grep -q '"deleted":[ ]*true'; then
  bad "a non-admin flagged the group deleted — every device would honour that on next pull"
else
  ok "a non-admin cannot flag the group deleted"
fi

# Deleting the admin member would leave nobody able to prove admin rights, locking even the
# real admin out of deleting their own group.
rpc splits_push "{\"p_device_id\":\"$ATTACKER_DEVICE\",\"p_payload\":{
  \"members\":[{\"id\":\"$MID\",\"group_id\":\"$GID\",\"name\":\"Verifier\",\"color_index\":0,
    \"claimed_by_device_id\":null,\"created_at\":$NOW,\"updated_at\":$LATER,
    \"deleted\":true}]}}" >/dev/null

survived=$(rpc splits_pull "{\"p_group_ids\":[\"$GID\"],\"p_since\":0,\"p_device_id\":\"$ADMIN_DEVICE\"}")
if printf '%s' "$survived" | grep -q "\"$MID\""; then
  ok "a non-admin cannot delete the admin member out from under the group"
else
  bad "the admin member was removed by a non-admin — the group can never be deleted now"
fi

# admin_member_id is frozen after creation; the app sets it once and has no transfer flow.
rpc splits_push "{\"p_device_id\":\"$ATTACKER_DEVICE\",\"p_payload\":{
  \"groups\":[{\"id\":\"$GID\",\"name\":\"Verify script\",\"emoji\":\"🧪\",
    \"currency_code\":\"INR\",\"invite_code\":\"$CODE\",\"admin_member_id\":\"attacker-member\",
    \"created_at\":$NOW,\"updated_at\":$LATER,\"deleted\":false}]}}" >/dev/null

admin_now=$(rpc splits_pull "{\"p_group_ids\":[\"$GID\"],\"p_since\":0,\"p_device_id\":\"$ADMIN_DEVICE\"}")
if printf '%s' "$admin_now" | grep -q 'attacker-member'; then
  bad "admin_member_id was reassigned by a non-admin — that is admin rights for the taking"
else
  ok "admin_member_id cannot be reassigned after the group is created"
fi

# ------------------------------------------------------------------- cleanup --

say ""
say "Deletion"

gone=$(rpc splits_delete_group "{\"p_group_id\":\"$GID\",\"p_device_id\":\"$ADMIN_DEVICE\"}")
if printf '%s' "$gone" | grep -q '"ok":[ ]*true'; then
  ok "the admin device can delete — test group cleaned up"
else
  bad "cleanup failed, remove group $GID by hand: $gone"
fi

# The live-id lists are what make hard deletes safe, so their absence is a real failure.
live=$(rpc splits_pull "{\"p_group_ids\":[\"$GID\"],\"p_since\":0,\"p_device_id\":\"$ADMIN_DEVICE\"}")
if printf '%s' "$live" | grep -q 'live_group_ids'; then
  ok "pull returns live id lists — devices can detect deletions by absence"
else
  bad "pull has no live_group_ids — re-run backend/supabase/schema.sql"
fi

if printf '%s' "$live" | grep -q "\"$GID\""; then
  bad "the deleted group is still present on the server"
else
  ok "the deleted group is gone from the table, not left as a flagged row"
fi

gone_invite=$(rpc splits_resolve_invite "{\"p_invite_code\":\"$CODE\",\"p_device_id\":\"$ADMIN_DEVICE\"}")
if printf '%s' "$gone_invite" | grep -q '"found":[ ]*false'; then
  ok "its invite code no longer resolves"
else
  bad "the invite code still resolves after deletion: $gone_invite"
fi

say ""
if [ "$fail" -eq 0 ]; then
  say "All $pass checks passed. Rebuild the app and pull down to refresh."
else
  say "$pass passed, $fail failed."
fi
exit $(( fail > 0 ? 1 : 0 ))
