-- 1. Admin flag on profiles
alter table public.profiles
  add column if not exists is_admin boolean not null default false;

-- 2. Helper: is the currently-authenticated user an admin?
-- SECURITY DEFINER so the lookup bypasses RLS (avoids recursion when used
-- inside the profiles admin policy). search_path pinned for safety.
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

-- 3. Admin-aware "do anything" policies on all nine app tables.
--    Normal per-user policies remain untouched; these are additive (permissive),
--    so they only ever GRANT access to admins and never widen normal-user access.
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
