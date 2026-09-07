-- ============================================================================
-- Account deletion — self-service + admin, wiping the whole account
-- ----------------------------------------------------------------------------
-- Applied to the live project (ref: wllqgdjkkhztbefdvvsf) via the Supabase
-- migration `account_deletion_rpcs`. Reproduced here for version control.
-- Safe to re-run (idempotent).
--
-- Why an RPC and not a client-side delete:
--   Deleting the row in auth.users is what actually removes the login and, via
--   the existing ON DELETE CASCADE foreign keys, wipes every user-owned row
--   (profiles, user_settings, user_stats, coin_wallets, gratitude_entries,
--   gardens -> garden_plants, user_inventory). The Android app only holds the
--   anon key + the signed-in user's JWT and CANNOT touch auth.users directly.
--   These SECURITY DEFINER functions run as their owner (postgres, which has
--   DELETE on auth.users) so the delete — and the cascade — happen server-side.
--   No service-role key ever ships in the app. search_path is pinned.
-- ============================================================================

-- 1) Self-service: the signed-in user deletes their OWN account.
create or replace function public.delete_current_user()
returns void
language plpgsql
security definer
set search_path = public, auth
as $$
declare
  uid uuid := auth.uid();
begin
  if uid is null then
    raise exception 'Not authenticated';
  end if;
  -- Cascades handle profiles, settings, stats, wallet, entries, garden(+plants),
  -- inventory, and the auth session/identity rows.
  delete from auth.users where id = uid;
end;
$$;

revoke all on function public.delete_current_user() from public, anon;
grant execute on function public.delete_current_user() to authenticated;

-- 2) Admin: delete ANY account (auth row + all data) in one shot. Gated by the
--    same is_current_user_admin() helper the dashboard already relies on.
create or replace function public.admin_delete_user(p_user_id uuid)
returns void
language plpgsql
security definer
set search_path = public, auth
as $$
begin
  if not public.is_current_user_admin() then
    raise exception 'Not authorized';
  end if;
  if p_user_id is null then
    raise exception 'user id required';
  end if;
  delete from auth.users where id = p_user_id;
end;
$$;

revoke all on function public.admin_delete_user(uuid) from public, anon;
grant execute on function public.admin_delete_user(uuid) to authenticated;
