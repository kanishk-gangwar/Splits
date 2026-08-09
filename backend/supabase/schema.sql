-- Splits — Supabase schema
--
-- Run this once in the Supabase SQL editor (Dashboard → SQL Editor → New query).
-- It is idempotent: re-running it on an existing project is safe and is how you apply updates.
--
-- Security model: the app has no accounts, so there is no auth.uid() to scope rows by.
-- Instead every table has RLS enabled with *no policies at all*, which denies the anon key
-- direct access entirely. All traffic goes through the SECURITY DEFINER functions below,
-- each of which requires the caller to already know an unguessable secret — either a
-- 128-bit group id or an invite code. That makes the invite link itself the capability,
-- which is exactly the trust model the app presents to users.
--
-- The rule that makes that model actually hold, and which an earlier version of this file
-- broke: A SECRET USED FOR AUTHORISATION MUST NEVER BE RETURNED BY ANY FUNCTION. The device
-- id is what proves admin rights to splits_delete_group, so splits_pull must not hand it out
-- — it used to, via to_jsonb(member), which meant anyone holding an invite link could read
-- the admin's device id straight out of the response and delete the group. Every projection
-- below is therefore an explicit column list, never to_jsonb() on a table with a secret in it.

-- ---------------------------------------------------------------------- tables --

create table if not exists public.splits_groups (
    id              text primary key,
    name            text not null,
    emoji           text not null default '🏠',
    currency_code   text not null default 'INR',
    invite_code     text not null unique,
    admin_member_id text,
    created_at      bigint not null,
    updated_at      bigint not null,
    deleted         boolean not null default false
);

create table if not exists public.splits_members (
    id                   text primary key,
    group_id             text not null references public.splits_groups(id) on delete cascade,
    name                 text not null,
    color_index          integer not null default 0,
    claimed_by_device_id text,
    created_at           bigint not null,
    updated_at           bigint not null,
    deleted              boolean not null default false
);

create table if not exists public.splits_expenses (
    id                 text primary key,
    group_id           text not null references public.splits_groups(id) on delete cascade,
    title              text not null,
    amount_minor       bigint not null,
    paid_by_member_id  text not null,
    kind               text not null default 'EXPENSE',
    category_id        text,
    note               text,
    occurred_at        bigint not null,
    created_at         bigint not null,
    updated_at         bigint not null,
    deleted            boolean not null default false
);

create table if not exists public.splits_shares (
    expense_id  text not null references public.splits_expenses(id) on delete cascade,
    member_id   text not null,
    share_minor bigint not null,
    primary key (expense_id, member_id)
);

create index if not exists splits_members_group    on public.splits_members(group_id);
create index if not exists splits_expenses_group   on public.splits_expenses(group_id);
create index if not exists splits_expenses_updated on public.splits_expenses(group_id, updated_at);
create index if not exists splits_groups_invite    on public.splits_groups(invite_code);

-- ------------------------------------------------------------------ size caps --
--
-- Anyone can call splits_push — that is the design, there are no accounts. Without length
-- limits "anyone" includes someone pasting a megabyte into a group name until the project
-- hits its storage quota. These are far above anything the UI can produce.

do $$
begin
    if not exists (select 1 from pg_constraint where conname = 'splits_groups_sane') then
        alter table public.splits_groups add constraint splits_groups_sane check (
            length(id) <= 64 and length(name) <= 120 and length(emoji) <= 16
            and length(currency_code) <= 8 and length(invite_code) <= 32
            and (admin_member_id is null or length(admin_member_id) <= 64)
        );
    end if;

    if not exists (select 1 from pg_constraint where conname = 'splits_members_sane') then
        alter table public.splits_members add constraint splits_members_sane check (
            length(id) <= 64 and length(name) <= 120
            and (claimed_by_device_id is null or length(claimed_by_device_id) <= 64)
        );
    end if;

    if not exists (select 1 from pg_constraint where conname = 'splits_expenses_sane') then
        alter table public.splits_expenses add constraint splits_expenses_sane check (
            length(id) <= 64 and length(title) <= 200
            and (note is null or length(note) <= 2000)
            and (category_id is null or length(category_id) <= 64)
            and length(paid_by_member_id) <= 64 and length(kind) <= 32
            and amount_minor between -1000000000000000 and 1000000000000000
        );
    end if;

    if not exists (select 1 from pg_constraint where conname = 'splits_shares_sane') then
        alter table public.splits_shares add constraint splits_shares_sane check (
            length(expense_id) <= 64 and length(member_id) <= 64
            and share_minor between -1000000000000000 and 1000000000000000
        );
    end if;
end
$$;

-- ------------------------------------------------ invite brute-force throttle --
--
-- Defence in depth for the 8-character codes minted by app versions before invite codes were
-- lengthened. Guessing one specific code was never realistic; finding *some* valid code by
-- scanning was cheaper than it should have been, because resolving an invite is unauthenticated
-- by design.
--
-- The client address comes from the request headers Supabase's gateway sets, so a determined
-- attacker can rotate it. This raises the cost of naive scanning; it is not the thing standing
-- between an attacker and your data — the length of the code is. It fails OPEN: any problem
-- reading the header or the counter lets the lookup through rather than locking users out.

create table if not exists public.splits_invite_attempts (
    client       text primary key,
    window_start bigint not null,
    misses       integer not null default 0
);

alter table public.splits_invite_attempts enable row level security;
revoke all on public.splits_invite_attempts from anon, authenticated;

-- ------------------------------------------------------------------------- rls --

alter table public.splits_groups   enable row level security;
alter table public.splits_members  enable row level security;
alter table public.splits_expenses enable row level security;
alter table public.splits_shares   enable row level security;

-- Deliberately no policies: the anon key cannot read or write these tables directly.
revoke all on public.splits_groups   from anon, authenticated;
revoke all on public.splits_members  from anon, authenticated;
revoke all on public.splits_expenses from anon, authenticated;
revoke all on public.splits_shares   from anon, authenticated;

-- ---------------------------------------------------------------------- helpers --

-- The one value the app treats as proof of identity, so it gets checked like one. Device ids
-- are 32 hex characters (data/Ids.kt); anything else is not a device id and gets no rights.
-- This is also what stops the sentinel below from ever being replayed as a device id.
create or replace function public.splits_device(p_device_id text)
returns text
language sql
immutable
as $$
    select case when p_device_id ~ '^[0-9a-f]{32}$' then p_device_id else null end;
$$;

-- What splits_pull reports in place of another device's id: enough for the client to see that
-- a name is taken (Member.isClaimed is a null check, GroupDetail.me is an equality check
-- against this device's own id), and useless to anybody trying to impersonate that device.
create or replace function public.splits_claim_sentinel()
returns text
language sql
immutable
as $$
    select 'someone-else'::text;
$$;

-- Whether this member row is the one a group points at as its admin. Removing that row would
-- leave splits_is_group_admin below permanently false — nobody could ever delete the group
-- again — so deleting it is itself an admin-only act. The app already refuses to offer it
-- (GroupSettingsScreen disables remove for the admin); this is the same rule where it counts.
create or replace function public.splits_is_admin_member(p_member_id text)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select exists (
        select 1 from public.splits_groups g where g.admin_member_id = p_member_id
    );
$$;

-- Admin is not a user, it is "the device holding the group's admin member" (TECHNICAL.md,
-- Identity). Both splits_push and splits_delete_group ask this same question, so it lives in
-- one place rather than being spelled out twice and drifting.
create or replace function public.splits_is_group_admin(p_group_id text, p_device_id text)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select p_device_id is not null and exists (
        select 1
        from public.splits_groups g
        join public.splits_members m on m.id = g.admin_member_id
        where g.id = p_group_id
          and m.claimed_by_device_id = p_device_id
    );
$$;

-- ------------------------------------------------------------------ pull by id --

-- Returns everything in the given groups that changed after p_since.
-- p_since = 0 pulls the full history, which is what a fresh install does.
--
-- p_device_id decides whose claims are readable: this device sees its own device id on the
-- names it holds, and the sentinel on everyone else's. Callers that omit it (an app build
-- older than this schema) see the sentinel everywhere, which reads as "every name is taken by
-- somebody else" — those installs have to update to claim or release a name again. That is the
-- cost of no longer publishing the secret, and it is worth paying.
drop function if exists public.splits_resolve_invite(text);
drop function if exists public.splits_pull(text[], bigint);

create or replace function public.splits_pull(
    p_group_ids text[],
    p_since     bigint default 0,
    p_device_id text default null
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    result   jsonb;
    v_device text := public.splits_device(p_device_id);
    v_other  text := public.splits_claim_sentinel();
begin
    if p_group_ids is null or array_length(p_group_ids, 1) is null then
        return jsonb_build_object(
            'live_group_ids', '[]'::jsonb, 'live_member_ids', '[]'::jsonb,
            'live_expense_ids', '[]'::jsonb,
            'groups', '[]'::jsonb, 'members', '[]'::jsonb,
            'expenses', '[]'::jsonb, 'shares', '[]'::jsonb
        );
    end if;

    -- A pull is one round trip per device per sync; nobody legitimately asks about hundreds of
    -- groups at once, and an unbounded array is a free way to make the server do work.
    if array_length(p_group_ids, 1) > 200 then
        raise exception 'too many group ids in one pull (max 200)';
    end if;

    select jsonb_build_object(
        -- The complete set of ids that still exist, regardless of p_since. This is what makes
        -- hard deletes safe: a device that has been offline compares its local rows against
        -- these lists and drops anything missing. Absence *is* the deletion signal, so no
        -- tombstone has to be kept around to carry it.
        'live_group_ids', coalesce((
            select jsonb_agg(g.id) from public.splits_groups g
            where g.id = any(p_group_ids)
        ), '[]'::jsonb),
        'live_member_ids', coalesce((
            select jsonb_agg(m.id) from public.splits_members m
            where m.group_id = any(p_group_ids)
        ), '[]'::jsonb),
        'live_expense_ids', coalesce((
            select jsonb_agg(e.id) from public.splits_expenses e
            where e.group_id = any(p_group_ids)
        ), '[]'::jsonb),
        'groups', coalesce((
            select jsonb_agg(jsonb_build_object(
                'id', g.id, 'name', g.name, 'emoji', g.emoji,
                'currency_code', g.currency_code, 'invite_code', g.invite_code,
                'admin_member_id', g.admin_member_id,
                'created_at', g.created_at, 'updated_at', g.updated_at, 'deleted', g.deleted
            ))
            from public.splits_groups g
            where g.id = any(p_group_ids) and g.updated_at > p_since
        ), '[]'::jsonb),
        -- Explicit columns, not to_jsonb(m): claimed_by_device_id is an authorisation secret
        -- and only its owner gets to see it. This is the fix for the invite-link-to-delete path.
        'members', coalesce((
            select jsonb_agg(jsonb_build_object(
                'id', m.id, 'group_id', m.group_id, 'name', m.name,
                'color_index', m.color_index,
                'claimed_by_device_id', case
                    when m.claimed_by_device_id is null then null
                    when v_device is not null and m.claimed_by_device_id = v_device
                        then m.claimed_by_device_id
                    else v_other
                end,
                'created_at', m.created_at, 'updated_at', m.updated_at, 'deleted', m.deleted
            ))
            from public.splits_members m
            where m.group_id = any(p_group_ids) and m.updated_at > p_since
        ), '[]'::jsonb),
        'expenses', coalesce((
            select jsonb_agg(jsonb_build_object(
                'id', e.id, 'group_id', e.group_id, 'title', e.title,
                'amount_minor', e.amount_minor, 'paid_by_member_id', e.paid_by_member_id,
                'kind', e.kind, 'category_id', e.category_id, 'note', e.note,
                'occurred_at', e.occurred_at,
                'created_at', e.created_at, 'updated_at', e.updated_at, 'deleted', e.deleted
            ))
            from public.splits_expenses e
            where e.group_id = any(p_group_ids) and e.updated_at > p_since
        ), '[]'::jsonb),
        -- Shares have no timestamp of their own; they travel with their expense.
        'shares', coalesce((
            select jsonb_agg(jsonb_build_object(
                'expense_id', s.expense_id, 'member_id', s.member_id,
                'share_minor', s.share_minor
            ))
            from public.splits_shares s
            join public.splits_expenses e on e.id = s.expense_id
            where e.group_id = any(p_group_ids) and e.updated_at > p_since
        ), '[]'::jsonb)
    ) into result;

    return result;
end;
$$;

-- -------------------------------------------------------------- resolve invite --

-- What a tapped invite link calls. Knowing the code is the whole authorisation.
create or replace function public.splits_resolve_invite(
    p_invite_code text,
    p_device_id   text default null
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    target_id text;
    v_client  text;
    v_now     bigint := (extract(epoch from now()) * 1000)::bigint;
    v_misses  integer := 0;
begin
    -- Who is asking, as far as the gateway can tell. Best effort by design — see the note on
    -- splits_invite_attempts. Any failure here leaves v_client null and skips the throttle.
    begin
        v_client := nullif(trim(split_part(
            nullif(current_setting('request.headers', true), '')::json ->> 'x-forwarded-for',
            ',', 1
        )), '');
    exception when others then
        v_client := null;
    end;

    if v_client is not null then
        begin
            select case when a.window_start > v_now - 3600000 then a.misses else 0 end
            into v_misses
            from public.splits_invite_attempts a
            where a.client = v_client;

            if coalesce(v_misses, 0) >= 30 then
                -- Indistinguishable from a wrong code, so scanning learns nothing from being
                -- throttled. A real user who fat-fingers a code 30 times in an hour is not a
                -- case worth optimising for.
                return jsonb_build_object('found', false);
            end if;
        exception when others then
            v_misses := 0;
        end;
    end if;

    select id into target_id
    from public.splits_groups
    where invite_code = upper(trim(p_invite_code)) and deleted = false;

    if target_id is null then
        if v_client is not null then
            begin
                insert into public.splits_invite_attempts (client, window_start, misses)
                values (v_client, v_now, 1)
                on conflict (client) do update set
                    window_start = case
                        when public.splits_invite_attempts.window_start > v_now - 3600000
                            then public.splits_invite_attempts.window_start
                        else v_now
                    end,
                    misses = case
                        when public.splits_invite_attempts.window_start > v_now - 3600000
                            then public.splits_invite_attempts.misses + 1
                        else 1
                    end;
            exception when others then
                null;
            end;
        end if;
        return jsonb_build_object('found', false);
    end if;

    -- A hit clears the counter: whoever this is holds a real invite.
    if v_client is not null then
        begin
            delete from public.splits_invite_attempts where client = v_client;
        exception when others then
            null;
        end;
    end if;

    return public.splits_pull(array[target_id], 0, p_device_id)
        || jsonb_build_object('found', true);
end;
$$;

-- ------------------------------------------------------------------------ push --

-- Last-write-wins on updated_at. Clocks across devices are close enough for this app,
-- and a stale write losing is far better than a stale write clobbering a newer one.
--
-- Everyone holding the invite can edit the group's expenses and names. That is the product,
-- not an oversight: these are people who agreed to split a dinner. Two things are NOT ordinary
-- edits, because they grant power rather than change data, and both are enforced here rather
-- than trusted to the client:
--
--   * claimed_by_device_id — a device may claim only an unheld name, and only for itself, and
--     may release only a name it actually holds. Without this, anyone with the invite could
--     take over the admin's identity and then delete the group.
--   * admin_member_id — frozen after the group row is first inserted. The app sets it once at
--     creation and has no transfer flow, so there is nothing legitimate to allow here.
drop function if exists public.splits_push(jsonb);

create or replace function public.splits_push(
    p_payload   jsonb,
    p_device_id text default null
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    touched_expense_ids text[];
    v_device text := public.splits_device(p_device_id);
    -- A device with a badly wrong clock should lose a merge, not win every future one. Without
    -- a ceiling, updated_at = 9223372036854775807 pins a row so no honest edit can ever
    -- overwrite it again. Clamping keeps device-stamped last-write-wins (see TECHNICAL.md)
    -- while taking away the pin.
    v_max_ts bigint := (extract(epoch from now()) * 1000)::bigint + 300000;
begin
    if jsonb_array_length(coalesce(p_payload->'groups', '[]'::jsonb)) > 50
        or jsonb_array_length(coalesce(p_payload->'members', '[]'::jsonb)) > 1000
        or jsonb_array_length(coalesce(p_payload->'expenses', '[]'::jsonb)) > 2000
        or jsonb_array_length(coalesce(p_payload->'shares', '[]'::jsonb)) > 20000 then
        raise exception 'push payload too large';
    end if;

    insert into public.splits_groups (
        id, name, emoji, currency_code, invite_code, admin_member_id,
        created_at, updated_at, deleted
    )
    select
        g->>'id', g->>'name', g->>'emoji', g->>'currency_code', g->>'invite_code',
        nullif(g->>'admin_member_id', ''),
        (g->>'created_at')::bigint, least((g->>'updated_at')::bigint, v_max_ts),
        coalesce((g->>'deleted')::boolean, false)
    from jsonb_array_elements(coalesce(p_payload->'groups', '[]'::jsonb)) as g
    on conflict (id) do update set
        name            = excluded.name,
        emoji           = excluded.emoji,
        currency_code   = excluded.currency_code,
        -- admin_member_id and invite_code are deliberately absent: neither may be changed
        -- after creation. Allowing admin_member_id here let anyone with the invite point it
        -- at a name they had claimed and become admin of somebody else's group.
        updated_at      = excluded.updated_at,
        -- Flagging a group deleted is how the client tells everyone else it is gone, so it is
        -- an admin act too. Without this check, blocking splits_delete_group would achieve
        -- nothing: anyone with the invite could push deleted = true and every other device
        -- would honour it on the next pull.
        deleted         = case
            when excluded.deleted
                 and not public.splits_is_group_admin(public.splits_groups.id, v_device)
                then public.splits_groups.deleted
            else excluded.deleted
        end
    where excluded.updated_at >= public.splits_groups.updated_at;

    insert into public.splits_members (
        id, group_id, name, color_index, claimed_by_device_id,
        created_at, updated_at, deleted
    )
    select
        m->>'id', m->>'group_id', m->>'name',
        coalesce((m->>'color_index')::integer, 0),
        -- A brand-new member row may arrive already claimed, but only by the caller itself.
        case when v_device is not null
                  and nullif(m->>'claimed_by_device_id', '') = v_device
             then v_device else null end,
        (m->>'created_at')::bigint, least((m->>'updated_at')::bigint, v_max_ts),
        coalesce((m->>'deleted')::boolean, false)
    from jsonb_array_elements(coalesce(p_payload->'members', '[]'::jsonb)) as m
    on conflict (id) do update set
        name                 = excluded.name,
        color_index          = excluded.color_index,
        claimed_by_device_id = case
            -- Releasing: only the device actually holding the name may hand it back.
            when excluded.claimed_by_device_id is null
                 and v_device is not null
                 and public.splits_members.claimed_by_device_id = v_device
                then null
            -- Claiming: for yourself only, and only a name nobody else is holding.
            when v_device is not null
                 and excluded.claimed_by_device_id = v_device
                 and (public.splits_members.claimed_by_device_id is null
                      or public.splits_members.claimed_by_device_id = v_device)
                then v_device
            -- Everything else — another device's id, the sentinel echoed back from a pull,
            -- an attempt to seize a held name — leaves the claim exactly as it was.
            else public.splits_members.claimed_by_device_id
        end,
        updated_at           = excluded.updated_at,
        -- Removing a participant is an ordinary group act, with one exception: the admin
        -- member. Deleting that row leaves the group with no admin anybody can prove, so its
        -- own admin would lose the ability to delete it — a lockout available to anyone
        -- holding the invite.
        deleted              = case
            when excluded.deleted
                 and public.splits_is_admin_member(public.splits_members.id)
                 and not public.splits_is_group_admin(public.splits_members.group_id, v_device)
                then public.splits_members.deleted
            else excluded.deleted
        end
    where excluded.updated_at >= public.splits_members.updated_at;

    insert into public.splits_expenses (
        id, group_id, title, amount_minor, paid_by_member_id, kind,
        category_id, note, occurred_at, created_at, updated_at, deleted
    )
    select
        e->>'id', e->>'group_id', e->>'title',
        (e->>'amount_minor')::bigint, e->>'paid_by_member_id',
        coalesce(e->>'kind', 'EXPENSE'),
        nullif(e->>'category_id', ''), nullif(e->>'note', ''),
        (e->>'occurred_at')::bigint, (e->>'created_at')::bigint,
        least((e->>'updated_at')::bigint, v_max_ts),
        coalesce((e->>'deleted')::boolean, false)
    from jsonb_array_elements(coalesce(p_payload->'expenses', '[]'::jsonb)) as e
    on conflict (id) do update set
        title             = excluded.title,
        amount_minor      = excluded.amount_minor,
        paid_by_member_id = excluded.paid_by_member_id,
        kind              = excluded.kind,
        category_id       = excluded.category_id,
        note              = excluded.note,
        occurred_at       = excluded.occurred_at,
        updated_at        = excluded.updated_at,
        deleted           = excluded.deleted
    where excluded.updated_at >= public.splits_expenses.updated_at;

    -- Shares are replaced wholesale for every expense in this push, because an edit can
    -- remove a participant and a row-by-row upsert would leave that stale share behind.
    select coalesce(array_agg(e->>'id'), '{}')
    into touched_expense_ids
    from jsonb_array_elements(coalesce(p_payload->'expenses', '[]'::jsonb)) as e;

    if array_length(touched_expense_ids, 1) is not null then
        delete from public.splits_shares where expense_id = any(touched_expense_ids);

        insert into public.splits_shares (expense_id, member_id, share_minor)
        select s->>'expense_id', s->>'member_id', (s->>'share_minor')::bigint
        from jsonb_array_elements(coalesce(p_payload->'shares', '[]'::jsonb)) as s
        where s->>'expense_id' = any(touched_expense_ids)
        on conflict (expense_id, member_id) do update set
            share_minor = excluded.share_minor;

    end if;

    -- Deletions are permanent. The upsert above has already applied last-write-wins, so any
    -- row now sitting at deleted = true is one the client's delete legitimately won — and
    -- pull's live-id lists are what tell every other device about it. Nothing has to linger.
    delete from public.splits_expenses
    where deleted = true
      and id in (
          select x->>'id'
          from jsonb_array_elements(coalesce(p_payload->'expenses', '[]'::jsonb)) as x
      );

    delete from public.splits_members
    where deleted = true
      and id in (
          select x->>'id'
          from jsonb_array_elements(coalesce(p_payload->'members', '[]'::jsonb)) as x
      );

    -- Same reclaim for groups, but only the admin's own device gets to finish the job. The
    -- app's delete flow calls splits_delete_group first and then pushes the soft-deleted row,
    -- so without this the push would resurrect the group it had just removed.
    delete from public.splits_groups
    where deleted = true
      and public.splits_is_group_admin(id, v_device)
      and id in (
          select x->>'id'
          from jsonb_array_elements(coalesce(p_payload->'groups', '[]'::jsonb)) as x
      );

    return jsonb_build_object('ok', true);
end;
$$;

-- -------------------------------------------------------------- admin-only kill --

-- Requirement 4, enforced on the server rather than trusted to the client: the caller must
-- own the device that has claimed the group's admin member.
--
-- This check is only worth anything because splits_pull no longer discloses
-- claimed_by_device_id. Re-exposing that column anywhere re-opens this door.
create or replace function public.splits_delete_group(p_group_id text, p_device_id text)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    admin_id     text;
    admin_device text;
    v_device     text := public.splits_device(p_device_id);
begin
    if v_device is null then
        return jsonb_build_object('ok', false, 'reason', 'not_admin');
    end if;

    select admin_member_id into admin_id
    from public.splits_groups where id = p_group_id;

    if admin_id is null then
        return jsonb_build_object('ok', false, 'reason', 'no_such_group');
    end if;

    select claimed_by_device_id into admin_device
    from public.splits_members where id = admin_id;

    if admin_device is null or admin_device is distinct from v_device then
        return jsonb_build_object('ok', false, 'reason', 'not_admin');
    end if;

    -- Gone for good. Members, expenses and shares cascade off the group row, and other
    -- devices find out because the group id stops appearing in pull's live_group_ids.
    delete from public.splits_groups where id = p_group_id;

    return jsonb_build_object('ok', true);
end;
$$;


-- ----------------------------------------------------------------------- purge --

-- Sweeps up any soft-deleted rows, across every group in the project.
--
-- Deletions are permanent from the moment they sync, so in normal operation this finds
-- nothing. It exists to clear tombstones written by earlier versions of the app, and as a
-- safety net for rows orphaned by a push that failed midway.
--
-- It takes no secret and touches every group, so it is NOT granted to anon — an earlier
-- version was, which handed anyone on the internet a global delete. Run it yourself from the
-- SQL editor, or schedule it:
--
--   select cron.schedule('splits-purge', '0 4 * * *', $cron$ select public.splits_purge_deleted(); $cron$);
--
-- The retention argument is kept for compatibility with older clients and is ignored: there is
-- no window to wait out any more, because pull's live-id lists carry the deletion signal
-- instead of the tombstone.
create or replace function public.splits_purge_deleted(p_retention_days integer default 30)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    removed_shares  integer;
    removed_expense integer;
    removed_member  integer;
    removed_group   integer;
begin
    with gone as (
        delete from public.splits_shares s
        using public.splits_expenses e
        where s.expense_id = e.id and e.deleted = true
        returning 1
    )
    select count(*) into removed_shares from gone;

    with gone as (
        delete from public.splits_expenses where deleted = true returning 1
    )
    select count(*) into removed_expense from gone;

    with gone as (
        delete from public.splits_members where deleted = true returning 1
    )
    select count(*) into removed_member from gone;

    -- Groups last: their children cascade, and counting them separately above keeps the
    -- report honest about what was actually reclaimed.
    with gone as (
        delete from public.splits_groups where deleted = true returning 1
    )
    select count(*) into removed_group from gone;

    return jsonb_build_object(
        'ok', true,
        'shares', removed_shares,
        'expenses', removed_expense,
        'members', removed_member,
        'groups', removed_group
    );
end;
$$;

-- ------------------------------------------------------------------------ grant --
--
-- Postgres grants EXECUTE on new functions to PUBLIC by default, so listing what anon may call
-- is not enough — anything not explicitly revoked stays callable by the publishable key. Revoke
-- first, then grant back only the four the app needs.

revoke all on function public.splits_pull(text[], bigint, text)      from public, anon, authenticated;
revoke all on function public.splits_resolve_invite(text, text)      from public, anon, authenticated;
revoke all on function public.splits_push(jsonb, text)               from public, anon, authenticated;
revoke all on function public.splits_delete_group(text, text)        from public, anon, authenticated;
revoke all on function public.splits_purge_deleted(integer)          from public, anon, authenticated;
revoke all on function public.splits_device(text)                    from public, anon, authenticated;
revoke all on function public.splits_claim_sentinel()                from public, anon, authenticated;
revoke all on function public.splits_is_group_admin(text, text)      from public, anon, authenticated;
revoke all on function public.splits_is_admin_member(text)           from public, anon, authenticated;

grant execute on function public.splits_pull(text[], bigint, text)   to anon, authenticated;
grant execute on function public.splits_resolve_invite(text, text)   to anon, authenticated;
grant execute on function public.splits_push(jsonb, text)            to anon, authenticated;
grant execute on function public.splits_delete_group(text, text)     to anon, authenticated;
-- splits_purge_deleted is intentionally NOT granted. See the note above it.
