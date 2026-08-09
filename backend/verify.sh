#!/usr/bin/env bash
#
# Checks that a Supabase project is wired up correctly, before you spend time wondering
# why the app isn't syncing.
#
#   ./backend/verify.sh
#
# Reads supabase.url and supabase.anonKey from local.properties. Creates one throwaway group
# on the server, reads it back, then deletes it — so it leaves nothing behind.

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
direct=$(curl -sS -o /dev/null -w '%{http_code}' "$URL/rest/v1/splits_groups?select=*" \
  -H "apikey: $KEY" -H "Authorization: Bearer $KEY" 2>&1)
if [ "$direct" = "200" ]; then
  bad "the anon key can read splits_groups directly — RLS is not doing its job"
  say "        Re-run the 'alter table ... enable row level security' and 'revoke' lines."
else
  ok "direct table reads are refused (HTTP $direct) — only the RPCs are reachable"
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
CODE=$(LC_ALL=C tr -dc 'ABCDEFGHJKMNPQRSTUVWXYZ23456789' </dev/urandom | head -c 8)
NOW=$(( $(date +%s) * 1000 ))
DEVICE="verify-script-device"

push=$(rpc splits_push "{\"p_payload\":{
  \"groups\":[{\"id\":\"$GID\",\"name\":\"Verify script\",\"emoji\":\"🧪\",
    \"currency_code\":\"INR\",\"invite_code\":\"$CODE\",\"admin_member_id\":\"$MID\",
    \"created_at\":$NOW,\"updated_at\":$NOW,\"deleted\":false}],
  \"members\":[{\"id\":\"$MID\",\"group_id\":\"$GID\",\"name\":\"Verifier\",\"color_index\":0,
    \"claimed_by_device_id\":\"$DEVICE\",\"created_at\":$NOW,\"updated_at\":$NOW,\"deleted\":false}],
  \"expenses\":[],\"shares\":[]}}")

if printf '%s' "$push" | grep -q '"ok":[ ]*true'; then
  ok "splits_push accepted a group"
else
  bad "splits_push failed: $push"
fi

back=$(rpc splits_resolve_invite "{\"p_invite_code\":\"$CODE\"}")
if printf '%s' "$back" | grep -q '"Verify script"'; then
  ok "the invite code resolves back to the group — this is what a shared link does"
else
  bad "invite lookup did not return the group: $back"
fi

# The delete RPC must refuse a device that does not own the admin member.
refused=$(rpc splits_delete_group "{\"p_group_id\":\"$GID\",\"p_device_id\":\"not-the-admin\"}")
if printf '%s' "$refused" | grep -q 'not_admin'; then
  ok "delete is refused for a non-admin device — requirement 4 holds server-side"
else
  bad "a non-admin device was not refused: $refused"
fi

gone=$(rpc splits_delete_group "{\"p_group_id\":\"$GID\",\"p_device_id\":\"$DEVICE\"}")
if printf '%s' "$gone" | grep -q '"ok":[ ]*true'; then
  ok "the admin device can delete — test group cleaned up"
else
  bad "cleanup failed, remove group $GID by hand: $gone"
fi

# ------------------------------------------------------------------ deletes --

say ""
say "Deletion"

# The live-id lists are what make hard deletes safe, so their absence is a real failure.
live=$(rpc splits_pull "{\"p_group_ids\":[\"$GID\"],\"p_since\":0}")
if printf '%s' "$live" | grep -q 'live_group_ids'; then
  ok "pull returns live id lists — devices can detect deletions by absence"
else
  bad "pull has no live_group_ids — re-run backend/supabase/schema.sql"
fi

# After the delete above, the group must be gone rather than flagged.
if printf '%s' "$live" | grep -q "\"$GID\""; then
  bad "the deleted group is still present on the server"
else
  ok "the deleted group is gone from the table, not left as a flagged row"
fi

gone_invite=$(rpc splits_resolve_invite "{\"p_invite_code\":\"$CODE\"}")
if printf '%s' "$gone_invite" | grep -q '"found":[ ]*false'; then
  ok "its invite code no longer resolves"
else
  bad "the invite code still resolves after deletion: $gone_invite"
fi

purge=$(rpc splits_purge_deleted '{"p_retention_days":30}')
if printf '%s' "$purge" | grep -q '"ok":[ ]*true'; then
  ok "splits_purge_deleted runs — sweeps up anything older versions left behind"
elif printf '%s' "$purge" | grep -qi 'could not find\|does not exist\|PGRST202'; then
  bad "splits_purge_deleted is missing — re-run backend/supabase/schema.sql"
else
  bad "splits_purge_deleted failed: $purge"
fi

say ""
if [ "$fail" -eq 0 ]; then
  say "All $pass checks passed. Rebuild the app and pull down to refresh."
else
  say "$pass passed, $fail failed."
fi
exit $(( fail > 0 ? 1 : 0 ))
