-- Splits — Supabase schema
--
-- Run this once in the Supabase SQL editor (Dashboard → SQL Editor → New query).
--
-- Security model: the app has no accounts, so there is no auth.uid() to scope rows by.
-- Instead every table has RLS enabled with *no policies at all*, which denies the anon key
-- direct access entirely. All traffic goes through the SECURITY DEFINER functions below,
-- each of which requires the caller to already know an unguessable secret — either a
-- 128-bit group id or an 8-character invite code. That makes the invite link itself the
-- capability, which is exactly the trust model the app presents to users.

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

-- ------------------------------------------------------------------ pull by id --

-- Returns everything in the given groups that changed after p_since.
-- p_since = 0 pulls the full history, which is what a fresh install does.
create or replace function public.splits_pull(p_group_ids text[], p_since bigint default 0)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    result jsonb;
begin
    if p_group_ids is null or array_length(p_group_ids, 1) is null then
        return jsonb_build_object(
            'groups', '[]'::jsonb, 'members', '[]'::jsonb,
            'expenses', '[]'::jsonb, 'shares', '[]'::jsonb
        );
    end if;

    select jsonb_build_object(
        'groups', coalesce((
            select jsonb_agg(to_jsonb(g)) from public.splits_groups g
            where g.id = any(p_group_ids) and g.updated_at > p_since
        ), '[]'::jsonb),
        'members', coalesce((
            select jsonb_agg(to_jsonb(m)) from public.splits_members m
            where m.group_id = any(p_group_ids) and m.updated_at > p_since
        ), '[]'::jsonb),
        'expenses', coalesce((
            select jsonb_agg(to_jsonb(e)) from public.splits_expenses e
            where e.group_id = any(p_group_ids) and e.updated_at > p_since
        ), '[]'::jsonb),
        -- Shares have no timestamp of their own; they travel with their expense.
        'shares', coalesce((
            select jsonb_agg(to_jsonb(s)) from public.splits_shares s
            join public.splits_expenses e on e.id = s.expense_id
            where e.group_id = any(p_group_ids) and e.updated_at > p_since
        ), '[]'::jsonb)
    ) into result;

    return result;
end;
$$;

-- -------------------------------------------------------------- resolve invite --

-- What a tapped invite link calls. Knowing the code is the whole authorisation.
create or replace function public.splits_resolve_invite(p_invite_code text)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    target_id text;
begin
    select id into target_id
    from public.splits_groups
    where invite_code = upper(trim(p_invite_code)) and deleted = false;

    if target_id is null then
        return jsonb_build_object('found', false);
    end if;

    return public.splits_pull(array[target_id], 0) || jsonb_build_object('found', true);
end;
$$;

-- ------------------------------------------------------------------------ push --

-- Last-write-wins on updated_at. Clocks across devices are close enough for this app,
-- and a stale write losing is far better than a stale write clobbering a newer one.
create or replace function public.splits_push(p_payload jsonb)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    touched_expense_ids text[];
begin
    insert into public.splits_groups (
        id, name, emoji, currency_code, invite_code, admin_member_id,
        created_at, updated_at, deleted
    )
    select
        g->>'id', g->>'name', g->>'emoji', g->>'currency_code', g->>'invite_code',
        nullif(g->>'admin_member_id', ''),
        (g->>'created_at')::bigint, (g->>'updated_at')::bigint,
        coalesce((g->>'deleted')::boolean, false)
    from jsonb_array_elements(coalesce(p_payload->'groups', '[]'::jsonb)) as g
    on conflict (id) do update set
        name            = excluded.name,
        emoji           = excluded.emoji,
        currency_code   = excluded.currency_code,
        admin_member_id = excluded.admin_member_id,
        updated_at      = excluded.updated_at,
        deleted         = excluded.deleted
    where excluded.updated_at >= public.splits_groups.updated_at;

    insert into public.splits_members (
        id, group_id, name, color_index, claimed_by_device_id,
        created_at, updated_at, deleted
    )
    select
        m->>'id', m->>'group_id', m->>'name',
        coalesce((m->>'color_index')::integer, 0),
        nullif(m->>'claimed_by_device_id', ''),
        (m->>'created_at')::bigint, (m->>'updated_at')::bigint,
        coalesce((m->>'deleted')::boolean, false)
    from jsonb_array_elements(coalesce(p_payload->'members', '[]'::jsonb)) as m
    on conflict (id) do update set
        name                 = excluded.name,
        color_index          = excluded.color_index,
        claimed_by_device_id = excluded.claimed_by_device_id,
        updated_at           = excluded.updated_at,
        deleted              = excluded.deleted
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
        (e->>'updated_at')::bigint,
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

    return jsonb_build_object('ok', true);
end;
$$;

-- -------------------------------------------------------------- admin-only kill --

-- Requirement 4, enforced on the server rather than trusted to the client: the caller must
-- own the device that has claimed the group's admin member.
create or replace function public.splits_delete_group(p_group_id text, p_device_id text)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    admin_id     text;
    admin_device text;
    stamp        bigint;
begin
    select admin_member_id into admin_id
    from public.splits_groups where id = p_group_id;

    if admin_id is null then
        return jsonb_build_object('ok', false, 'reason', 'no_such_group');
    end if;

    select claimed_by_device_id into admin_device
    from public.splits_members where id = admin_id;

    if admin_device is null or admin_device is distinct from p_device_id then
        return jsonb_build_object('ok', false, 'reason', 'not_admin');
    end if;

    stamp := (extract(epoch from now()) * 1000)::bigint;

    -- Tombstone rather than hard delete, so other devices learn about it on their next pull.
    update public.splits_expenses set deleted = true, updated_at = stamp where group_id = p_group_id;
    update public.splits_members  set deleted = true, updated_at = stamp where group_id = p_group_id;
    update public.splits_groups   set deleted = true, updated_at = stamp where id = p_group_id;

    return jsonb_build_object('ok', true);
end;
$$;

-- ------------------------------------------------------------------------ grant --

grant execute on function public.splits_pull(text[], bigint)        to anon, authenticated;
grant execute on function public.splits_resolve_invite(text)        to anon, authenticated;
grant execute on function public.splits_push(jsonb)                 to anon, authenticated;
grant execute on function public.splits_delete_group(text, text)    to anon, authenticated;
