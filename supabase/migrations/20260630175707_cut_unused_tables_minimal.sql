-- Minimal feature cut: drop unused/stub tables + the coin ledger.
-- Keep items / user_inventory / garden_plants (working shop+plant loop).

-- 1. submit_gratitude_entry: stop writing coin_transactions ledger
CREATE OR REPLACE FUNCTION public.submit_gratitude_entry(p_entry_text text, p_input_method input_method, p_coin_reward integer DEFAULT 5)
 RETURNS gratitude_entries
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'auth'
AS $function$
declare
  v_user        uuid := auth.uid();
  v_today       date := (now() at time zone 'UTC')::date;
  v_count_today int;
  v_cap         int;
  v_entry       public.gratitude_entries;
  v_prev_date   date;
  v_prev_streak int;
  v_new_streak  int;
begin
  if v_user is null then raise exception 'not authenticated' using errcode = '42501'; end if;
  if p_entry_text is null or length(trim(p_entry_text)) = 0 then raise exception 'entry text required'; end if;
  if p_coin_reward < 0 then raise exception 'coin reward must be non-negative'; end if;

  select daily_entry_cap into v_cap from public.user_settings where user_id = v_user;
  v_cap := coalesce(v_cap, 10);

  select count(*) into v_count_today
  from public.gratitude_entries
  where user_id = v_user and entry_date = v_today and deleted_at is null;

  if v_count_today >= v_cap then raise exception 'daily entry cap (%) reached', v_cap; end if;

  insert into public.gratitude_entries (user_id, entry_text, input_method, coins_awarded, entry_date)
  values (v_user, trim(p_entry_text), p_input_method, p_coin_reward, v_today)
  returning * into v_entry;

  update public.coin_wallets
     set balance = balance + p_coin_reward
   where user_id = v_user;

  select last_entry_date, current_streak
    into v_prev_date, v_prev_streak
  from public.user_stats where user_id = v_user for update;

  v_new_streak := public.next_streak(v_prev_date, coalesce(v_prev_streak, 0), v_today);

  update public.user_stats
     set total_entries      = total_entries + 1,
         total_coins_earned = total_coins_earned + p_coin_reward,
         current_streak     = v_new_streak,
         longest_streak     = greatest(longest_streak, v_new_streak),
         last_entry_date    = v_today
   where user_id = v_user;

  return v_entry;
end;
$function$;

-- 2. purchase_item: stop writing coin_transactions ledger
CREATE OR REPLACE FUNCTION public.purchase_item(p_item_id uuid)
 RETURNS user_inventory
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'auth'
AS $function$
declare
  v_user          uuid := auth.uid();
  v_item          public.items;
  v_balance       int;
  v_inv           public.user_inventory;
  v_profile_level int;
begin
  if v_user is null then raise exception 'not authenticated' using errcode = '42501'; end if;

  select * into v_item from public.items where id = p_item_id;
  if v_item.id is null then raise exception 'item not found'; end if;
  if not v_item.is_purchasable then raise exception 'item not purchasable'; end if;
  if v_item.available_from  is not null and now() < v_item.available_from  then raise exception 'item not yet available';     end if;
  if v_item.available_until is not null and now() > v_item.available_until then raise exception 'item no longer available';   end if;

  select level into v_profile_level from public.profiles where id = v_user;
  if v_profile_level < v_item.level_required then
    raise exception 'level % required (you are %)', v_item.level_required, v_profile_level;
  end if;

  select * into v_inv from public.user_inventory where user_id = v_user and item_id = p_item_id;
  if v_inv.user_id is not null then return v_inv; end if;

  select balance into v_balance from public.coin_wallets where user_id = v_user for update;
  if v_balance < v_item.price_coins then
    raise exception 'insufficient coins (need %, have %)', v_item.price_coins, v_balance;
  end if;

  update public.coin_wallets set balance = balance - v_item.price_coins
   where user_id = v_user;

  insert into public.user_inventory (user_id, item_id) values (v_user, p_item_id)
  returning * into v_inv;

  return v_inv;
end;
$function$;

-- 3. water_plant: stop writing coin_transactions ledger
CREATE OR REPLACE FUNCTION public.water_plant(p_plant_id uuid, p_water_cost integer DEFAULT 10)
 RETURNS garden_plants
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'auth'
AS $function$
declare
  v_user        uuid := auth.uid();
  v_plant       public.garden_plants;
  v_garden_user uuid;
  v_balance     int;
  v_next_stage  public.growth_stage;
  v_was_mature  boolean := false;
begin
  if v_user is null then raise exception 'not authenticated' using errcode = '42501'; end if;
  if p_water_cost < 0 then raise exception 'water cost must be non-negative'; end if;

  select * into v_plant from public.garden_plants where id = p_plant_id for update;
  if v_plant.id is null then raise exception 'plant not found'; end if;

  select user_id into v_garden_user from public.gardens where id = v_plant.garden_id;
  if v_garden_user <> v_user then raise exception 'not your plant'; end if;

  select balance into v_balance from public.coin_wallets where user_id = v_user for update;
  if v_balance < p_water_cost then
    raise exception 'insufficient coins (need %, have %)', p_water_cost, v_balance;
  end if;

  update public.coin_wallets set balance = balance - p_water_cost
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

-- 4. Drop RPCs tied to dropped features
DROP FUNCTION IF EXISTS public.place_decor(uuid, integer, integer);
DROP FUNCTION IF EXISTS public.purchase_bundle(uuid);

-- 5. Drop the six tables (nothing kept references them)
DROP TABLE IF EXISTS public.coin_transactions CASCADE;
DROP TABLE IF EXISTS public.garden_decor      CASCADE;
DROP TABLE IF EXISTS public.bundle_items      CASCADE;
DROP TABLE IF EXISTS public.bundles           CASCADE;
DROP TABLE IF EXISTS public.user_achievements CASCADE;
DROP TABLE IF EXISTS public.achievements      CASCADE;

-- 6. Clean up orphans
DROP FUNCTION IF EXISTS public.garden_decor_check_category();
DROP TYPE     IF EXISTS public.transaction_type;
