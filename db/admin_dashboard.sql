-- ============================================================================
-- Checkpoint 4 — Admin CRUD Dashboard: Supabase schema + security changes
-- ----------------------------------------------------------------------------
-- This file documents the SQL that backs the admin dashboard. It was applied to
-- the live project (ref: wllqgdjkkhztbefdvvsf) via the Supabase migration
-- `admin_flag_and_policies`. It is reproduced here so the change is captured in
-- version control. Safe to re-run (idempotent).
--
-- Security model: NO service-role key lives in the Android app. Admin cross-user
-- access is granted purely by RLS that calls public.is_current_user_admin().
-- Normal per-user policies are left untouched and remain scoped to auth.uid().
-- ============================================================================

-- 1) Admin flag lives on the account profile table.
alter table public.profiles
  add column if not exists is_admin boolean not null default false;

-- 2) "Is the caller an admin?" helper.
--    SECURITY DEFINER so it reads profiles without tripping RLS (prevents the
--    profiles admin policy from recursing into itself). search_path pinned.
create or replace function public.is_current_user_admin()
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select coalesce(
    (select p.is_admin from public.profiles p where p.id = auth.uid()),
    false
  );
$$;

revoke all on function public.is_current_user_admin() from public;
grant execute on function public.is_current_user_admin() to authenticated;

-- 3) Additive "admin can do anything" policies on all nine app tables.
--    These are PERMISSIVE, so they only ever OR-in extra access for admins and
--    never widen what a normal user can reach.
do $$
declare
  t text;
  tables text[] := array[
    'profiles', 'user_settings', 'user_stats', 'items', 'coin_wallets',
    'gratitude_entries', 'gardens', 'user_inventory', 'garden_plants'
  ];
begin
  foreach t in array tables loop
    execute format('drop policy if exists %I on public.%I', t || '_admin_all', t);
    execute format(
      'create policy %I on public.%I for all to authenticated '
      || 'using (public.is_current_user_admin()) '
      || 'with check (public.is_current_user_admin())',
      t || '_admin_all', t
    );
  end loop;
end $$;

-- 4) Grant the demo admin account. Change the UUID to whichever account should
--    be the grader-facing admin. (Currently: display_name 'Jeremy2'.)
--    To find ids:  select id, display_name from public.profiles;
update public.profiles
  set is_admin = true
  where id = '3e3a4cdd-dd20-4721-9d8c-51f13ee9341b';
