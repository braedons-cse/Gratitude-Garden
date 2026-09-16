-- Make the coin economy server-authoritative (Play Store roadmap 0.5).
--
-- Two independent ways to mint coins existed before this migration:
--
--   1. submit_gratitude_entry took the reward as a client argument and validated
--      only `>= 0`; water_plant did the same with the water cost. The anon key
--      ships inside the APK, so any signed-in user could POST straight to
--      /rest/v1/rpc/submit_gratitude_entry with p_coin_reward = 2147483647 and
--      mint max-int coins.
--
--   2. The daily cap counted only `deleted_at is null` entries, but
--      delete_gratitude_entry (05_signup_rpc_rls.sql:145) only stamps deleted_at
--      -- it never claws the coins back. So submit -> delete -> submit -> delete
--      earned unbounded coins no matter what any single entry paid, using nothing
--      but the app's own delete button. Fixing (1) alone would have left the
--      ceiling exactly as broken.
--
-- Both are closed here. Amounts are now derived server-side, following the
-- purchase_item pattern (which has always read items.price_coins server-side).

-- ── 1. The reward formula, as its own pure function ──────────────────
-- Broken out so it is independently testable (select public.entry_reward(...))
-- and so the economy can be retuned in one obvious place.
--
--   base        5   -- the old flat reward, kept as a floor so no existing
--                   -- behaviour becomes stingier and the 50-220 coin item
--                   -- prices stay meaningful
--   effort     0-3  -- +1 per 40 characters, capped at 3 (so it stops paying at
--                   -- 120 chars). Whitespace runs are collapsed first, so
--                   -- padding an entry with newlines buys nothing, and a
--                   -- 10,000-character paste earns exactly what a 120-character
--                   -- paragraph does.
--   streak     0-5  -- +1 per full week, first entry of the day only
--
-- The streak bonus is limited to the day's first entry on purpose: next_streak
-- returns greatest(prev_streak, 1) when the date is unchanged, so a streak never
-- advances within a day, and paying the bonus on all ten allowed entries would
-- multiply it tenfold. Range: 5-13 for the day's first entry, 5-8 thereafter.
create or replace function public.entry_reward(
  p_entry_text   text,
  p_streak       int,
  p_first_of_day boolean
)
returns int
language sql
immutable
set search_path = public, pg_temp
as $$
  select 5
       + least(
           3,
           length(regexp_replace(trim(coalesce(p_entry_text, '')), '\s+', ' ', 'g')) / 40
         )
       + case
           when coalesce(p_first_of_day, false) then least(coalesce(p_streak, 0) / 7, 5)
           else 0
         end;
$$;

comment on function public.entry_reward(text, int, boolean) is
  'Coins awarded for a gratitude entry. Range [5,13]. Pure: no table reads, no randomness, no client input. Never accept this amount from a client.';

-- ── 2. submit_gratitude_entry: drop p_coin_reward ────────────────────
-- The old 3-arg signature MUST go rather than merely be superseded. CREATE OR
-- REPLACE cannot remove a parameter, so it would leave a second overload behind
-- -- and PostgREST picks an overload by matching the JSON body keys against
-- parameter names, meaning a body carrying p_coin_reward would still be routed
-- to the vulnerable version. The exploit payload is itself the overload selector.
drop function if exists public.submit_gratitude_entry(text, public.input_method, int);

create function public.submit_gratitude_entry(
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

  select daily_entry_cap into v_cap from public.user_settings where user_id = v_user;
  v_cap := coalesce(v_cap, 10);

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

-- ── 3. water_plant: drop p_water_cost ────────────────────────────────
-- Held flat at 10, matching the previous cost, so the "Water · 10 coins" label
-- and the coins >= 10 affordability gate in GardenScreen stay truthful and no
-- balance drifts. Deliberately NOT scaled by growth_stage: the ladder ends at
-- mature and watering a mature plant already does nothing, so stage pricing
-- would make the most expensive waterings the ones that buy no progress. If
-- variable pricing is ever wanted, add items.water_cost_coins and read it the
-- way purchase_item reads price_coins -- a balance change, not a security fix.
drop function if exists public.water_plant(uuid, int);

create function public.water_plant(p_plant_id uuid)
returns public.garden_plants
language plpgsql
security definer
set search_path to 'public', 'auth'
as $function$
declare
  v_cost        constant int := 10;
  v_user        uuid := auth.uid();
  v_plant       public.garden_plants;
  v_garden_user uuid;
  v_balance     int;
  v_next_stage  public.growth_stage;
  v_was_mature  boolean := false;
begin
  if v_user is null then raise exception 'not authenticated' using errcode = '42501'; end if;

  select * into v_plant from public.garden_plants where id = p_plant_id for update;
  if v_plant.id is null then raise exception 'plant not found'; end if;

  select user_id into v_garden_user from public.gardens where id = v_plant.garden_id;
  if v_garden_user <> v_user then raise exception 'not your plant'; end if;

  select balance into v_balance from public.coin_wallets where user_id = v_user for update;
  if v_balance < v_cost then
    raise exception 'insufficient coins (need %, have %)', v_cost, v_balance;
  end if;

  update public.coin_wallets set balance = balance - v_cost
   where user_id = v_user;

  v_next_stage := case v_plant.growth_stage
                    when 'seedling' then 'sapling'::public.growth_stage
                    when 'sapling'  then 'mature'::public.growth_stage
                    else                  'mature'::public.growth_stage
                  end;

  if v_plant.growth_stage = 'mature' then v_was_mature := true; end if;

  update public.garden_plants
     set growth_stage    = v_next_stage,
         health          = 'healthy',
         last_watered_at = now()
   where id = p_plant_id
   returning * into v_plant;

  if not v_was_mature and v_next_stage = 'mature' then
    update public.user_stats set plants_grown = plants_grown + 1 where user_id = v_user;
  end if;

  return v_plant;
end;
$function$;

-- ── 4. Re-apply the ACLs the DROPs discarded ─────────────────────────
-- NOT hygiene -- mandatory. pg_default_acl for schema public grants EXECUTE to
-- anon on every newly created function. These two are anon-proof today only
-- because 07_advisor_fixes revoked it, and CREATE OR REPLACE preserves an ACL --
-- but DROP + CREATE re-inherits the default. Without this block the migration
-- would hand anon execute rights on the very functions it is hardening.
revoke all     on function public.entry_reward(text, int, boolean)                 from public, anon;
revoke all     on function public.submit_gratitude_entry(text, public.input_method) from public, anon;
revoke all     on function public.water_plant(uuid)                                 from public, anon;

grant  execute on function public.entry_reward(text, int, boolean)                 to authenticated;
grant  execute on function public.submit_gratitude_entry(text, public.input_method) to authenticated;
grant  execute on function public.water_plant(uuid)                                 to authenticated;

-- ── 5. Two functions 07_advisor_fixes never covered ──────────────────
-- Added after that migration, so they kept the default anon EXECUTE grant
-- (a revoke from PUBLIC does not remove an explicit role grant -- see the note in
-- 07_advisor_fixes). move_plant is the live proof of the footgun described above.
-- Neither leaks today (move_plant raises 'not authenticated'; is_current_user_admin
-- returns false for anon), but the advisor flags both, and revoking now means a
-- future edit that moves a table read above the auth guard is not anon-reachable.
revoke all     on function public.move_plant(uuid, integer, integer) from public, anon;
revoke all     on function public.is_current_user_admin()            from public, anon;

grant  execute on function public.move_plant(uuid, integer, integer) to authenticated;
grant  execute on function public.is_current_user_admin()            to authenticated;

-- ── 6. Let PostgREST see the new signatures ──────────────────────────
notify pgrst, 'reload schema';
