-- Lock the client write surface (Play Store roadmap 0.5).
--
-- The economy migration (20260910051500) made the *amounts* server-authoritative.
-- It did not close the other half of the problem: three tables let the client
-- write privileged columns directly, no RPC involved.
--
-- Root cause, and the reason this was easy to miss: an RLS policy constrains
-- WHICH ROW, never WHICH COLUMNS. All three policies below were written as
-- `using (auth.uid() = <owner>) with check (auth.uid() = <owner>)`, which is a
-- correct ownership check and a complete non-answer to column safety. Postgres
-- WITH CHECK cannot reference OLD, so a policy *cannot* express "this column may
-- not change" -- the mechanism simply has no column awareness. Combined with the
-- table-wide UPDATE grant `authenticated` holds, that is a column-level
-- free-for-all on every row the user owns.
--
--   V1  profiles_update_own
--       PATCH /rest/v1/profiles?id=eq.<own uid> {"is_admin": true} succeeded.
--       That activates the nine additive <table>_admin_all policies, handing the
--       caller full read/write over every table in the schema -- including every
--       other user's gratitude_entries, which are free-text personal reflections.
--       It also trivially defeats the economy migration: an admin writes
--       coin_wallets.balance directly and never touches an RPC. level and xp were
--       self-writable the same way.
--
--   V2  user_settings_update_own
--       submit_gratitude_entry reads its ceiling from the caller's own row
--       (select daily_entry_cap ... where user_id = v_user), and this policy let
--       the caller PATCH that column. 20260910051500 hardened how the cap is
--       COUNTED (soft-deleted entries now count) but not where the cap COMES
--       FROM, so the submit -> delete -> submit printer was closed while the
--       ceiling itself was still a client-supplied number. Third coin hole, same
--       family as p_coin_reward and p_water_cost.
--
--   V3  gardens_update_own
--       set_active_backdrop() already validates ownership AND category correctly.
--       The hole was that this policy let the client skip the RPC and PATCH
--       active_backdrop_item_id straight in, equipping an unowned item and
--       bypassing purchase_item. grid_rows/grid_cols were writable too -- free
--       garden expansion, and a pre-broken monetisation surface for roadmap 1.3.
--
-- WHY NOT COLUMN-LEVEL GRANTS, the otherwise-narrowest fix. A revoke of
-- update(is_admin) on profiles from authenticated would close V1 in one line. It
-- also breaks the admin dashboard: grants are scoped to a ROLE, and admins ARE
-- members of `authenticated` -- there is no separate Postgres role for them, only
-- the is_current_user_admin() predicate. Rescuing it would mean a real app_admin
-- role plus a custom access token hook plus a twin of every `to authenticated`
-- policy. Far larger and more fragile than the bug.
--
-- WHY DROPPING THE POLICY IS SAFE FOR ADMINS. Admin write access does not come
-- from these policies. It comes from the separate, permissive <table>_admin_all
-- policies (20260630205012), and permissive policies are OR'd. Dropping the
-- non-admin branch of a disjunction cannot affect the admin branch, which
-- references neither the row's owner nor anything these policies supplied. The
-- live proof is already in this schema: NO table has any INSERT policy, yet admin
-- create works today -- solely because <table>_admin_all is FOR ALL.
--
-- CONSEQUENCE TO KNOW BEFORE YOU TEST: with the policy gone a non-admin PATCH
-- does NOT 403. RLS matches zero rows and PostgREST returns 204 No Content (or []
-- under Prefer: return=representation). The exploits above become SILENT no-ops.
-- Verify by re-reading the row, never by asserting on the status code.

-- -- 1. profiles: drop the policy, then lock the keystone twice --------
drop policy if exists profiles_update_own on public.profiles;

-- is_admin is the single value that unlocks all nine _admin_all policies, so it
-- gets a second, independent lock that does not depend on the policy set being
-- right. A trigger fires regardless of which policy let the row through, so this
-- survives someone re-adding a permissive UPDATE policy later -- which is exactly
-- how V1 was created in the first place.
--
-- The discriminator is current_user, NOT is_current_user_admin() alone. PostgREST
-- switches role to `authenticated` for a user JWT, `anon` for none, and
-- `service_role` for the service key; the SQL editor runs as `postgres`. Gating
-- on the admin predicate by itself would break the README's documented way to
-- grant an admin (update public.profiles set is_admin = true ... in the SQL
-- editor), because auth.uid() is null there and the function returns false --
-- locking everyone out of the bootstrap path. Checking current_user pins this to
-- end-user requests, which is the only thing being defended against.
create or replace function public.profiles_guard_is_admin()
returns trigger
language plpgsql
set search_path = public, pg_temp
as $function$
begin
  if new.is_admin is distinct from old.is_admin
     and current_user = 'authenticated'
     and not public.is_current_user_admin() then
    raise exception 'is_admin is not self-assignable' using errcode = '42501';
  end if;
  return new;
end;
$function$;

comment on function public.profiles_guard_is_admin() is
  'Blocks a signed-in non-admin from changing profiles.is_admin. Second lock behind the dropped profiles_update_own policy; exempts postgres/service_role so the SQL-editor bootstrap path still works.';

-- `before update of is_admin` fires when the column is MENTIONED, not only when
-- it changes, and AdminTableSpec.buildPayload() sends every field on every save --
-- so this runs on each admin profile edit and the `is distinct from` guard makes
-- the no-op case free. This is the one write path where a loud 403 is wanted.
drop trigger if exists profiles_guard_is_admin_trg on public.profiles;
create trigger profiles_guard_is_admin_trg
  before update of is_admin on public.profiles
  for each row execute function public.profiles_guard_is_admin();

revoke all on function public.profiles_guard_is_admin() from public, anon;

-- -- 2. user_settings: drop the policy, add the one real write back ----
-- This policy existed to serve exactly ONE client write in the entire app:
-- GardenRepository.markNotifPromptSeen(). Everything else it permitted --
-- daily_entry_cap above all -- was attack surface with no caller. Reminder
-- preferences (on/off + time) are on-device DataStore, not this table.
drop policy if exists user_settings_update_own on public.user_settings;

-- Zero parameters, deliberately. notif_prompt_seen is a one-way latch, so there
-- is nothing for the client to name -- not a value, not a user, not a column.
-- Same reasoning that removed p_coin_reward: a parameter the client controls is
-- a parameter the client rewrites.
--
-- If this ever does need an argument, add it by DROP + CREATE, never CREATE OR
-- REPLACE: replace cannot add a parameter without leaving the 0-arg overload
-- reachable, and PostgREST selects an overload by matching body keys to
-- parameter names, so the weaker version stays callable by anyone who sends the
-- right body.
create or replace function public.mark_notif_prompt_seen()
returns void
language plpgsql
security definer
set search_path to 'public', 'auth'
as $function$
declare
  v_user uuid := auth.uid();
begin
  if v_user is null then raise exception 'not authenticated' using errcode = '42501'; end if;

  -- No `if not found then raise`: handle_new_user() provisions this row at
  -- signup, and a miss must not break the first-entry flow over a flag.
  update public.user_settings
     set notif_prompt_seen = true
   where user_id = v_user;
end;
$function$;

comment on function public.mark_notif_prompt_seen() is
  'Latches user_settings.notif_prompt_seen to true for the caller. One-way, idempotent, no parameters. The only user-initiated write to user_settings; every other column is admin-only.';

-- -- 3. gardens: drop the policy ---------------------------------------
-- set_active_backdrop() already checks ownership (user_inventory) and category
-- (assert_item_category) and is now the ONLY non-admin path to
-- active_backdrop_item_id, which is what makes the category-only trigger on this
-- table sufficient.
--
-- Deliberately NOT adding an ownership check to gardens_check_backdrop_category():
-- an admin comping a user a backdrop they do not own is a legitimate admin
-- action, and a trigger cannot tell that apart from the exploit. Ownership is an
-- RPC concern and it already lives there.
--
-- grid_rows/grid_cols become admin-only, which is the precondition for ever
-- charging for garden expansion (roadmap 1.3).
drop policy if exists gardens_update_own on public.gardens;

-- -- 4. Make the daily cap true regardless of table state --------------
-- Belt and braces behind section 2. The policy drop means a user can no longer
-- raise their own cap, but the value still comes out of a table an admin can
-- edit, so clamp it at read time: a stored value can only ever LOWER the
-- ceiling. 50 sits well above the design cap of 10, so per-user tuning stays
-- useful within a bounded range and no stored number is a coin printer.
--
-- CREATE OR REPLACE here, NOT the drop-and-recreate that 20260910051500 used --
-- the deliberate inverse. That migration had to drop because it was REMOVING a
-- parameter and a surviving overload would still have been reachable. This
-- signature is unchanged, and CREATE OR REPLACE preserves the ACL, so replacing
-- avoids re-inheriting the pg_default_acl anon grant entirely.
create or replace function public.submit_gratitude_entry(
  p_entry_text   text,
  p_input_method public.input_method
)
returns public.gratitude_entries
language plpgsql
security definer
set search_path to 'public', 'auth'
as $function$
declare
  v_user        uuid := auth.uid();
  v_today       date := (now() at time zone 'UTC')::date;
  v_count_today int;
  v_cap         int;
  v_entry       public.gratitude_entries;
  v_prev_date   date;
  v_prev_streak int;
  v_new_streak  int;
  v_reward      int;
  v_balance     int;
  v_text        text := trim(p_entry_text);
begin
  if v_user is null then raise exception 'not authenticated' using errcode = '42501'; end if;
  if v_text is null or length(v_text) = 0 then raise exception 'entry text required'; end if;

  -- least(..., 50): the cap is read from a table, so it is only as trustworthy
  -- as every write path into that table. Clamping here makes the ceiling hold
  -- even if a permissive UPDATE policy is ever re-added to user_settings.
  select daily_entry_cap into v_cap from public.user_settings where user_id = v_user;
  v_cap := least(coalesce(v_cap, 10), 50);

  -- Counts EVERY entry written today, soft-deleted ones included. Deleting an
  -- entry does not refund its coins, so a deleted entry has still been paid for
  -- and must still count against the cap -- otherwise submit -> delete -> submit
  -- is an unbounded coin printer. This is what makes daily_entry_cap a real
  -- ceiling on daily earnings rather than just a limit on visible rows.
  -- (The alternative fix -- refunding coins in delete_gratitude_entry -- can
  -- drive a wallet negative and touches a second RPC; this is the tighter one.)
  select count(*) into v_count_today
  from public.gratitude_entries
  where user_id = v_user and entry_date = v_today;

  if v_count_today >= v_cap then raise exception 'daily entry cap (%) reached', v_cap; end if;

  -- Lock order is coin_wallets -> user_stats, matching water_plant and
  -- purchase_item. The streak has to be read before the insert now (the reward
  -- depends on it and is stored on the row), and taking user_stats first would
  -- invert the order against water_plant and open a same-user deadlock window.
  select balance into v_balance
  from public.coin_wallets where user_id = v_user for update;

  select last_entry_date, current_streak
    into v_prev_date, v_prev_streak
  from public.user_stats where user_id = v_user for update;

  v_new_streak := public.next_streak(v_prev_date, coalesce(v_prev_streak, 0), v_today);

  -- The one line that used to be a client parameter.
  v_reward := public.entry_reward(v_text, v_new_streak, v_count_today = 0);

  insert into public.gratitude_entries (user_id, entry_text, input_method, coins_awarded, entry_date)
  values (v_user, v_text, p_input_method, v_reward, v_today)
  returning * into v_entry;

  update public.coin_wallets
     set balance = balance + v_reward
   where user_id = v_user;

  update public.user_stats
     set total_entries      = total_entries + 1,
         total_coins_earned = total_coins_earned + v_reward,
         current_streak     = v_new_streak,
         longest_streak     = greatest(longest_streak, v_new_streak),
         last_entry_date    = v_today
   where user_id = v_user;

  return v_entry;
end;
$function$;

-- -- 5. Table privilege hygiene ----------------------------------------
-- anon holds the FULL grant set on all nine tables. It is default-denied today
-- only because RLS is on and not one policy names anon or public -- which is the
-- table-level twin of the pg_default_acl function footgun documented in
-- 20260910051500. The day someone writes a policy `using (true)` without
-- `to authenticated`, anon inherits it silently. Nothing in the app touches
-- PostgREST before login, so revoking costs nothing.
--
-- TRUNCATE is the sharp one: it is NOT subject to row security. No PostgREST
-- verb reaches it today, so this is not remotely exploitable, but it is a
-- standing grant that would erase every user's journal the instant any other SQL
-- path opened up. TRIGGER and REFERENCES go with it as schema-modifying rights an
-- API role has no use for.
--
-- NOT revoked: SELECT/INSERT/UPDATE/DELETE on authenticated. Admins ARE
-- authenticated -- RLS is the gate, the grant is not. Revoking these is the same
-- trap as the column-grant approach rejected in the header, and it would take the
-- admin dashboard down with it. Do not "tighten" this later.
do $$
declare
  t text;
  tables text[] := array[
    'profiles', 'user_settings', 'user_stats', 'items', 'coin_wallets',
    'gratitude_entries', 'gardens', 'user_inventory', 'garden_plants'
  ];
begin
  foreach t in array tables loop
    execute format('revoke all on table public.%I from anon', t);
    execute format('revoke truncate, trigger, references on table public.%I from authenticated', t);
  end loop;
end $$;

-- Deliberately NOT touched: relforcerowsecurity stays false on all nine tables.
-- FORCE RLS only affects the table OWNER, so it is not why V1-V3 existed and
-- changing it closes nothing. It would actively break things: every RPC here is
-- security definer owned by postgres, and since this schema has NO INSERT
-- policies at all, forcing RLS would make submit_gratitude_entry, purchase_item
-- and place_plant start failing the moment postgres lost BYPASSRLS.

-- -- 6. Re-assert function ACLs ----------------------------------------
-- Insurance, not mandatory: nothing above was dropped, and CREATE OR REPLACE
-- preserves an ACL. Kept because it is idempotent, free, and the one thing in
-- this file that would fail silently and invisibly if it were ever needed and
-- missing.
revoke all     on function public.mark_notif_prompt_seen()                          from public, anon;
revoke all     on function public.submit_gratitude_entry(text, public.input_method)  from public, anon;

grant  execute on function public.mark_notif_prompt_seen()                          to authenticated;
grant  execute on function public.submit_gratitude_entry(text, public.input_method)  to authenticated;

-- -- 7. Let PostgREST see the new function ------------------------------
notify pgrst, 'reload schema';
