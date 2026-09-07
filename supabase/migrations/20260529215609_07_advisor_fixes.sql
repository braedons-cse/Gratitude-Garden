-- Pin search_path on helper/trigger functions (security advisor 0011)
alter function public.set_updated_at()                          set search_path = public, pg_temp;
alter function public.next_streak(date, int, date)              set search_path = public, pg_temp;
alter function public.assert_item_category(uuid, public.item_category) set search_path = public, pg_temp;
alter function public.garden_plants_check_category()            set search_path = public, pg_temp;
alter function public.garden_decor_check_category()             set search_path = public, pg_temp;
alter function public.gardens_check_backdrop_category()         set search_path = public, pg_temp;

-- handle_new_user is meant to fire from an AFTER INSERT trigger on auth.users
-- only, never via REST. Lock it down.
revoke all on function public.handle_new_user() from public, anon, authenticated;

-- Also belt-and-suspenders: explicitly revoke RPC execute from anon
-- (revoke from public was already in 05_signup_rpc_rls but advisors flag anon explicitly).
revoke all on function public.submit_gratitude_entry(text, public.input_method, int) from anon;
revoke all on function public.delete_gratitude_entry(uuid)                            from anon;
revoke all on function public.edit_gratitude_entry(uuid, text)                        from anon;
revoke all on function public.purchase_item(uuid)                                     from anon;
revoke all on function public.purchase_bundle(uuid)                                   from anon;
revoke all on function public.place_plant(uuid, int, int)                             from anon;
revoke all on function public.place_decor(uuid, int, int)                             from anon;
revoke all on function public.water_plant(uuid, int)                                  from anon;
revoke all on function public.set_active_backdrop(uuid)                               from anon;

-- Add covering indexes for unindexed FKs (perf advisor 0001)
create index coin_transactions_source_item_idx
  on public.coin_transactions (source_item_id)
  where source_item_id is not null;

create index coin_transactions_source_bundle_idx
  on public.coin_transactions (source_bundle_id)
  where source_bundle_id is not null;

create index gardens_active_backdrop_idx
  on public.gardens (active_backdrop_item_id)
  where active_backdrop_item_id is not null;

-- Rewrite RLS policies to use (select auth.uid()) so the call is evaluated once
-- per query, not once per row (perf advisor 0003).
drop policy profiles_select_own            on public.profiles;
drop policy profiles_update_own            on public.profiles;
drop policy user_settings_select_own       on public.user_settings;
drop policy user_settings_update_own       on public.user_settings;
drop policy user_stats_select_own          on public.user_stats;
drop policy coin_wallets_select_own        on public.coin_wallets;
drop policy coin_transactions_select_own   on public.coin_transactions;
drop policy gratitude_entries_select_own   on public.gratitude_entries;
drop policy gardens_select_own             on public.gardens;
drop policy gardens_update_own             on public.gardens;
drop policy user_inventory_select_own      on public.user_inventory;
drop policy garden_plants_select_own       on public.garden_plants;
drop policy garden_plants_delete_own       on public.garden_plants;
drop policy garden_decor_select_own        on public.garden_decor;
drop policy garden_decor_delete_own        on public.garden_decor;
drop policy user_achievements_select_own   on public.user_achievements;

create policy profiles_select_own on public.profiles for select to authenticated
  using ((select auth.uid()) = id);
create policy profiles_update_own on public.profiles for update to authenticated
  using ((select auth.uid()) = id) with check ((select auth.uid()) = id);

create policy user_settings_select_own on public.user_settings for select to authenticated
  using ((select auth.uid()) = user_id);
create policy user_settings_update_own on public.user_settings for update to authenticated
  using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);

create policy user_stats_select_own        on public.user_stats        for select to authenticated
  using ((select auth.uid()) = user_id);
create policy coin_wallets_select_own      on public.coin_wallets      for select to authenticated
  using ((select auth.uid()) = user_id);
create policy coin_transactions_select_own on public.coin_transactions for select to authenticated
  using ((select auth.uid()) = user_id);
create policy gratitude_entries_select_own on public.gratitude_entries for select to authenticated
  using ((select auth.uid()) = user_id);

create policy gardens_select_own on public.gardens for select to authenticated
  using ((select auth.uid()) = user_id);
create policy gardens_update_own on public.gardens for update to authenticated
  using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);

create policy user_inventory_select_own on public.user_inventory for select to authenticated
  using ((select auth.uid()) = user_id);

create policy garden_plants_select_own on public.garden_plants for select to authenticated
  using (exists (select 1 from public.gardens g where g.id = garden_id and g.user_id = (select auth.uid())));
create policy garden_plants_delete_own on public.garden_plants for delete to authenticated
  using (exists (select 1 from public.gardens g where g.id = garden_id and g.user_id = (select auth.uid())));

create policy garden_decor_select_own on public.garden_decor for select to authenticated
  using (exists (select 1 from public.gardens g where g.id = garden_id and g.user_id = (select auth.uid())));
create policy garden_decor_delete_own on public.garden_decor for delete to authenticated
  using (exists (select 1 from public.gardens g where g.id = garden_id and g.user_id = (select auth.uid())));

create policy user_achievements_select_own on public.user_achievements for select to authenticated
  using ((select auth.uid()) = user_id);
