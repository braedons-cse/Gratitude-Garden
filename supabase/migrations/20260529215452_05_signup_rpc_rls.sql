-- ============================================================
-- Signup trigger
-- ============================================================
create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public, auth
as $$
declare
  v_display_name text;
  v_garden_id    uuid;
  v_backdrop_id  uuid;
begin
  v_display_name := coalesce(
    nullif(trim(new.raw_user_meta_data->>'display_name'), ''),
    nullif(trim(new.raw_user_meta_data->>'full_name'), ''),
    nullif(split_part(new.email, '@', 1), ''),
    'Gardener'
  );

  insert into public.profiles      (id, display_name) values (new.id, v_display_name);
  insert into public.user_settings (user_id)          values (new.id);
  insert into public.user_stats    (user_id)          values (new.id);
  insert into public.coin_wallets  (user_id)          values (new.id);
  insert into public.gardens       (user_id)          values (new.id) returning id into v_garden_id;

  insert into public.user_inventory (user_id, item_id)
  select new.id, i.id from public.items i where i.is_starter
  on conflict do nothing;

  select i.id into v_backdrop_id
  from public.items i
  where i.is_starter and i.category = 'backdrop'
  order by i.created_at
  limit 1;

  if v_backdrop_id is not null then
    update public.gardens set active_backdrop_item_id = v_backdrop_id where id = v_garden_id;
  end if;

  return new;
end;
$$;

create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();

-- ============================================================
-- RPCs
-- ============================================================
create or replace function public.next_streak(prev_date date, prev_streak int, new_date date)
returns int
language plpgsql
immutable
as $$
begin
  if prev_date is null then
    return 1;
  elsif prev_date = new_date then
    return greatest(prev_streak, 1);
  elsif prev_date + interval '1 day' = new_date then
    return prev_streak + 1;
  else
    return 1;
  end if;
end;
$$;

create or replace function public.submit_gratitude_entry(
  p_entry_text   text,
  p_input_method public.input_method,
  p_coin_reward  int default 5
)
returns public.gratitude_entries
language plpgsql
security definer
set search_path = public, auth
as $$
declare
  v_user        uuid := auth.uid();
  v_today       date := (now() at time zone 'UTC')::date;
  v_count_today int;
  v_cap         int;
  v_entry       public.gratitude_entries;
  v_new_balance int;
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
   where user_id = v_user
   returning balance into v_new_balance;

  insert into public.coin_transactions (user_id, amount, type, balance_after, source_entry_id)
  values (v_user, p_coin_reward, 'earned_from_entry', v_new_balance, v_entry.id);

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
$$;

create or replace function public.delete_gratitude_entry(p_entry_id uuid)
returns void
language plpgsql
security definer
set search_path = public, auth
as $$
declare v_user uuid := auth.uid();
begin
  if v_user is null then raise exception 'not authenticated' using errcode = '42501'; end if;

  update public.gratitude_entries
     set deleted_at = now()
   where id = p_entry_id and user_id = v_user and deleted_at is null;

  if not found then raise exception 'entry not found'; end if;
end;
$$;

create or replace function public.edit_gratitude_entry(p_entry_id uuid, p_new_text text)
returns public.gratitude_entries
language plpgsql
security definer
set search_path = public, auth
as $$
declare
  v_user  uuid := auth.uid();
  v_entry public.gratitude_entries;
begin
  if v_user is null then raise exception 'not authenticated' using errcode = '42501'; end if;
  if p_new_text is null or length(trim(p_new_text)) = 0 then raise exception 'entry text required'; end if;

  update public.gratitude_entries
     set entry_text = trim(p_new_text)
   where id = p_entry_id and user_id = v_user and deleted_at is null
   returning * into v_entry;

  if v_entry.id is null then raise exception 'entry not found'; end if;
  return v_entry;
end;
$$;

create or replace function public.purchase_item(p_item_id uuid)
returns public.user_inventory
language plpgsql
security definer
set search_path = public, auth
as $$
declare
  v_user          uuid := auth.uid();
  v_item          public.items;
  v_balance       int;
  v_new_balance   int;
  v_inv           public.user_inventory;
  v_tx_type       public.transaction_type;
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
   where user_id = v_user returning balance into v_new_balance;

  v_tx_type := case v_item.category
                 when 'seed'     then 'spent_on_seed'::public.transaction_type
                 when 'decor'    then 'spent_on_decor'::public.transaction_type
                 when 'backdrop' then 'spent_on_backdrop'::public.transaction_type
               end;

  insert into public.coin_transactions (user_id, amount, type, balance_after, source_item_id)
  values (v_user, -v_item.price_coins, v_tx_type, v_new_balance, p_item_id);

  insert into public.user_inventory (user_id, item_id) values (v_user, p_item_id)
  returning * into v_inv;

  return v_inv;
end;
$$;

create or replace function public.purchase_bundle(p_bundle_id uuid)
returns int
language plpgsql
security definer
set search_path = public, auth
as $$
declare
  v_user        uuid := auth.uid();
  v_bundle      public.bundles;
  v_balance     int;
  v_new_balance int;
  v_granted     int := 0;
begin
  if v_user is null then raise exception 'not authenticated' using errcode = '42501'; end if;

  select * into v_bundle from public.bundles where id = p_bundle_id;
  if v_bundle.id is null then raise exception 'bundle not found'; end if;
  if v_bundle.available_from  is not null and now() < v_bundle.available_from  then raise exception 'bundle not yet available'; end if;
  if v_bundle.available_until is not null and now() > v_bundle.available_until then raise exception 'bundle no longer available'; end if;

  select balance into v_balance from public.coin_wallets where user_id = v_user for update;
  if v_balance < v_bundle.price_coins then
    raise exception 'insufficient coins (need %, have %)', v_bundle.price_coins, v_balance;
  end if;

  update public.coin_wallets set balance = balance - v_bundle.price_coins
   where user_id = v_user returning balance into v_new_balance;

  insert into public.coin_transactions (user_id, amount, type, balance_after, source_bundle_id)
  values (v_user, -v_bundle.price_coins, 'spent_on_bundle', v_new_balance, p_bundle_id);

  insert into public.user_inventory (user_id, item_id)
  select v_user, bi.item_id from public.bundle_items bi where bi.bundle_id = p_bundle_id
  on conflict do nothing;

  get diagnostics v_granted = row_count;
  return v_granted;
end;
$$;

create or replace function public.place_plant(p_item_id uuid, p_grid_x int, p_grid_y int)
returns public.garden_plants
language plpgsql
security definer
set search_path = public, auth
as $$
declare
  v_user   uuid := auth.uid();
  v_garden public.gardens;
  v_owned  boolean;
  v_plant  public.garden_plants;
begin
  if v_user is null then raise exception 'not authenticated' using errcode = '42501'; end if;

  select * into v_garden from public.gardens where user_id = v_user;
  if v_garden.id is null then raise exception 'garden not found'; end if;

  if p_grid_x < 0 or p_grid_x >= v_garden.grid_cols or p_grid_y < 0 or p_grid_y >= v_garden.grid_rows then
    raise exception 'cell (%,%) out of bounds (cols=%, rows=%)', p_grid_x, p_grid_y, v_garden.grid_cols, v_garden.grid_rows;
  end if;

  select exists(select 1 from public.user_inventory where user_id = v_user and item_id = p_item_id) into v_owned;
  if not v_owned then raise exception 'you do not own this seed'; end if;

  perform public.assert_item_category(p_item_id, 'seed');

  insert into public.garden_plants (garden_id, item_id, grid_x, grid_y)
  values (v_garden.id, p_item_id, p_grid_x, p_grid_y)
  returning * into v_plant;

  return v_plant;
end;
$$;

create or replace function public.place_decor(p_item_id uuid, p_grid_x int, p_grid_y int)
returns public.garden_decor
language plpgsql
security definer
set search_path = public, auth
as $$
declare
  v_user   uuid := auth.uid();
  v_garden public.gardens;
  v_owned  boolean;
  v_decor  public.garden_decor;
begin
  if v_user is null then raise exception 'not authenticated' using errcode = '42501'; end if;

  select * into v_garden from public.gardens where user_id = v_user;
  if v_garden.id is null then raise exception 'garden not found'; end if;

  if p_grid_x < 0 or p_grid_x >= v_garden.grid_cols or p_grid_y < 0 or p_grid_y >= v_garden.grid_rows then
    raise exception 'cell (%,%) out of bounds', p_grid_x, p_grid_y;
  end if;

  select exists(select 1 from public.user_inventory where user_id = v_user and item_id = p_item_id) into v_owned;
  if not v_owned then raise exception 'you do not own this decor'; end if;

  perform public.assert_item_category(p_item_id, 'decor');

  insert into public.garden_decor (garden_id, item_id, grid_x, grid_y)
  values (v_garden.id, p_item_id, p_grid_x, p_grid_y)
  returning * into v_decor;

  return v_decor;
end;
$$;

create or replace function public.water_plant(p_plant_id uuid, p_water_cost int default 10)
returns public.garden_plants
language plpgsql
security definer
set search_path = public, auth
as $$
declare
  v_user        uuid := auth.uid();
  v_plant       public.garden_plants;
  v_garden_user uuid;
  v_balance     int;
  v_new_balance int;
  v_next_stage  public.growth_stage;
  v_was_mature  boolean := false;
begin
  if v_user is null then raise exception 'not authenticated' using errcode = '42501'; end if;
  if p_water_cost < 0 then raise exception 'water cost must be non-negative'; end if;

  -- Lock the plant row, then look up its garden's owner separately
  select * into v_plant from public.garden_plants where id = p_plant_id for update;
  if v_plant.id is null then raise exception 'plant not found'; end if;

  select user_id into v_garden_user from public.gardens where id = v_plant.garden_id;
  if v_garden_user <> v_user then raise exception 'not your plant'; end if;

  select balance into v_balance from public.coin_wallets where user_id = v_user for update;
  if v_balance < p_water_cost then
    raise exception 'insufficient coins (need %, have %)', p_water_cost, v_balance;
  end if;

  update public.coin_wallets set balance = balance - p_water_cost
   where user_id = v_user returning balance into v_new_balance;

  insert into public.coin_transactions (user_id, amount, type, balance_after, source_garden_plant_id)
  values (v_user, -p_water_cost, 'spent_on_water', v_new_balance, p_plant_id);

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
$$;

create or replace function public.set_active_backdrop(p_item_id uuid)
returns public.gardens
language plpgsql
security definer
set search_path = public, auth
as $$
declare
  v_user   uuid := auth.uid();
  v_owned  boolean;
  v_garden public.gardens;
begin
  if v_user is null then raise exception 'not authenticated' using errcode = '42501'; end if;

  select exists(select 1 from public.user_inventory where user_id = v_user and item_id = p_item_id) into v_owned;
  if not v_owned then raise exception 'you do not own this backdrop'; end if;

  perform public.assert_item_category(p_item_id, 'backdrop');

  update public.gardens set active_backdrop_item_id = p_item_id
   where user_id = v_user returning * into v_garden;

  return v_garden;
end;
$$;

-- Lock down RPC execution to authenticated users only
revoke all on function public.submit_gratitude_entry(text, public.input_method, int) from public;
revoke all on function public.delete_gratitude_entry(uuid)                            from public;
revoke all on function public.edit_gratitude_entry(uuid, text)                        from public;
revoke all on function public.purchase_item(uuid)                                     from public;
revoke all on function public.purchase_bundle(uuid)                                   from public;
revoke all on function public.place_plant(uuid, int, int)                             from public;
revoke all on function public.place_decor(uuid, int, int)                             from public;
revoke all on function public.water_plant(uuid, int)                                  from public;
revoke all on function public.set_active_backdrop(uuid)                               from public;

grant execute on function public.submit_gratitude_entry(text, public.input_method, int) to authenticated;
grant execute on function public.delete_gratitude_entry(uuid)                            to authenticated;
grant execute on function public.edit_gratitude_entry(uuid, text)                        to authenticated;
grant execute on function public.purchase_item(uuid)                                     to authenticated;
grant execute on function public.purchase_bundle(uuid)                                   to authenticated;
grant execute on function public.place_plant(uuid, int, int)                             to authenticated;
grant execute on function public.place_decor(uuid, int, int)                             to authenticated;
grant execute on function public.water_plant(uuid, int)                                  to authenticated;
grant execute on function public.set_active_backdrop(uuid)                               to authenticated;

-- ============================================================
-- RLS
-- ============================================================
alter table public.profiles           enable row level security;
alter table public.user_settings      enable row level security;
alter table public.user_stats         enable row level security;
alter table public.coin_wallets       enable row level security;
alter table public.coin_transactions  enable row level security;
alter table public.gratitude_entries  enable row level security;
alter table public.gardens            enable row level security;
alter table public.user_inventory     enable row level security;
alter table public.garden_plants      enable row level security;
alter table public.garden_decor       enable row level security;
alter table public.user_achievements  enable row level security;
alter table public.items              enable row level security;
alter table public.bundles            enable row level security;
alter table public.bundle_items       enable row level security;
alter table public.achievements       enable row level security;

create policy items_select_authenticated         on public.items         for select to authenticated using (true);
create policy bundles_select_authenticated       on public.bundles       for select to authenticated using (true);
create policy bundle_items_select_authenticated  on public.bundle_items  for select to authenticated using (true);
create policy achievements_select_authenticated  on public.achievements  for select to authenticated using (true);

create policy profiles_select_own on public.profiles for select to authenticated using (auth.uid() = id);
create policy profiles_update_own on public.profiles for update to authenticated
  using (auth.uid() = id) with check (auth.uid() = id);

create policy user_settings_select_own on public.user_settings for select to authenticated using (auth.uid() = user_id);
create policy user_settings_update_own on public.user_settings for update to authenticated
  using (auth.uid() = user_id) with check (auth.uid() = user_id);

create policy user_stats_select_own        on public.user_stats        for select to authenticated using (auth.uid() = user_id);
create policy coin_wallets_select_own      on public.coin_wallets      for select to authenticated using (auth.uid() = user_id);
create policy coin_transactions_select_own on public.coin_transactions for select to authenticated using (auth.uid() = user_id);
create policy gratitude_entries_select_own on public.gratitude_entries for select to authenticated using (auth.uid() = user_id);

create policy gardens_select_own on public.gardens for select to authenticated using (auth.uid() = user_id);
create policy gardens_update_own on public.gardens for update to authenticated
  using (auth.uid() = user_id) with check (auth.uid() = user_id);

create policy user_inventory_select_own on public.user_inventory for select to authenticated using (auth.uid() = user_id);

create policy garden_plants_select_own on public.garden_plants for select to authenticated
  using (exists (select 1 from public.gardens g where g.id = garden_id and g.user_id = auth.uid()));
create policy garden_plants_delete_own on public.garden_plants for delete to authenticated
  using (exists (select 1 from public.gardens g where g.id = garden_id and g.user_id = auth.uid()));

create policy garden_decor_select_own on public.garden_decor for select to authenticated
  using (exists (select 1 from public.gardens g where g.id = garden_id and g.user_id = auth.uid()));
create policy garden_decor_delete_own on public.garden_decor for delete to authenticated
  using (exists (select 1 from public.gardens g where g.id = garden_id and g.user_id = auth.uid()));

create policy user_achievements_select_own on public.user_achievements for select to authenticated using (auth.uid() = user_id);
