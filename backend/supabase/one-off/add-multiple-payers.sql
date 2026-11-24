-- Multiple payers per expense (app 1.8.0). Safe to run more than once.
--
-- Easiest is to re-run the whole schema.sql in the Supabase SQL editor, which includes this
-- column and the updated splits_pull / splits_push. This file is only the column + check, for
-- reference; the functions still have to come from schema.sql.
alter table public.splits_expenses add column if not exists payers jsonb;

do $$
begin
    if not exists (select 1 from pg_constraint where conname = 'splits_expenses_payers_sane') then
        alter table public.splits_expenses add constraint splits_expenses_payers_sane check (
            payers is null
            or (jsonb_typeof(payers) = 'array' and jsonb_array_length(payers) <= 200
                and length(payers::text) <= 20000)
        );
    end if;
end $$;
