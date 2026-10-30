-- REVIEWABLE DRAFT ONLY. Not executed against any Supabase project.
-- Supabase CLI discovery failed with ENOTFOUND registry.npmjs.org,
-- so this is deliberately not presented as a generated/applied migration.
-- Intended project: rrhuqwaytpeerirbnhlc.
-- Before applying: create a migration using `supabase migration new`, copy
-- this SQL into that file, apply through the authorized project connection,
-- then verify RLS and compare-and-swap with two separate authenticated users.

create table if not exists public.uniplan_state (
  user_id uuid primary key references auth.users(id) on delete cascade,
  revision bigint not null default 1 check (revision > 0),
  payload jsonb not null check (jsonb_typeof(payload) = 'object'),
  updated_at timestamptz not null default now()
);

alter table public.uniplan_state enable row level security;
revoke all on public.uniplan_state from public, anon, authenticated;
grant select, insert, update on public.uniplan_state to authenticated;

create policy uniplan_read_own on public.uniplan_state
  for select to authenticated
  using ((select auth.uid()) = user_id);

create policy uniplan_insert_own on public.uniplan_state
  for insert to authenticated
  with check ((select auth.uid()) = user_id);

create policy uniplan_update_own on public.uniplan_state
  for update to authenticated
  using ((select auth.uid()) = user_id)
  with check ((select auth.uid()) = user_id);

create or replace function public.save_uniplan_state(expected_revision bigint, data jsonb)
returns jsonb
language plpgsql
security invoker
set search_path = ''
as $$
declare
  current_owner uuid := auth.uid();
  new_revision bigint;
  existing_revision bigint;
begin
  if current_owner is null then
    raise exception 'Authentication required' using errcode = '42501';
  end if;
  if expected_revision is null or expected_revision < 0 then
    raise exception 'Invalid expected revision' using errcode = '22023';
  end if;
  if data is null or jsonb_typeof(data) <> 'object'
     or data->>'schemaVersion' is distinct from '1'
     or jsonb_typeof(data->'profile') is distinct from 'object'
     or jsonb_typeof(data->'subjects') is distinct from 'array'
     or jsonb_typeof(data->'tasks') is distinct from 'array'
     or jsonb_typeof(data->'lessons') is distinct from 'array'
     or jsonb_typeof(data->'notes') is distinct from 'array'
     or octet_length(data::text) > 2097152 then
    raise exception 'Invalid snapshot' using errcode = '22023';
  end if;

  if expected_revision = 0 then
    insert into public.uniplan_state(user_id, revision, payload, updated_at)
      values(current_owner, 1, data, now())
      on conflict (user_id) do nothing
      returning revision into new_revision;
  else
    update public.uniplan_state
      set payload = data, revision = revision + 1, updated_at = now()
      where user_id = current_owner and revision = expected_revision
      returning revision into new_revision;
  end if;

  if new_revision is not null then
    return jsonb_build_object('ok', true, 'revision', new_revision);
  end if;
  select revision into existing_revision
    from public.uniplan_state where user_id = current_owner;
  return jsonb_build_object('ok', false, 'conflict', true,
    'revision', coalesce(existing_revision, 0));
end;
$$;

revoke all on function public.save_uniplan_state(bigint, jsonb) from public, anon;
grant execute on function public.save_uniplan_state(bigint, jsonb) to authenticated;
notify pgrst, 'reload schema';
