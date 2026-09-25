-- Streak freezes (roadmap 1.3). A missed day is forgiven if a freeze is on hand.
--
--   * Where they come from. One free freeze per calendar month, plus extras bought for
--     coins (buy_streak_freeze). At most 2 held. The monthly grant is computed lazily --
--     freezes_on() adds this month's one if it hasn't been banked yet -- and banked
--     whenever the row is written anyway (a submit or a buy). No cron: a user who never
--     comes back never needed one, and the client shows the same formula.
--
--   * When they're spent. Automatically, by the first entry of a day that follows a
--     gap of 1-2 days, and only when the freezes on hand cover the WHOLE gap. A gap too
--     wide to cover resets the streak as before and spends nothing: half a bridge saves
--     nothing, so it shouldn't cost anything. The covered days are recorded in
--     streak_frozen_days.
--
--   * What a frozen day is worth. It bridges the run but doesn't lengthen it: day 10,
--     a frozen day, then an entry is day 11. The streak still means days written on.
--     streak_through() therefore walks entries AND frozen days to find the run, but
--     counts only the entries.
--
--   * Backdating. An offline entry can arrive up to 36 h late (20260924150000). If it
--     lands on a day a freeze already covered, the freeze was spent for nothing: the
--     frozen day is removed and the freeze handed back.
--
-- Everything is still recomputed from the dates, so a replayed submit -- which returns
-- before any of this -- can't spend a freeze twice.

-- ── 1. Freeze balance on user_stats ─────────────────────────────────
-- user_stats has only a select policy (plus user_stats_admin_all), so, like every other
-- column there, these are writable only through the RPCs below.
alter table public.user_stats
  add column streak_freezes     int  not null default 0 check (streak_freezes between 0 and 2),
  add column freeze_grant_month date;

comment on column public.user_stats.streak_freezes is
  'Freezes banked. Read through freezes_on(): this month''s free one may not be banked yet.';
comment on column public.user_stats.freeze_grant_month is
  'First day of the last month whose free freeze was banked; null = never.';

-- ── 2. The days a freeze covered ────────────────────────────────────
create table public.streak_frozen_days (
  user_id    uuid not null references auth.users(id) on delete cascade,
  day        date not null,
  created_at timestamptz not null default now(),
  primary key (user_id, day)
);

alter table public.streak_frozen_days enable row level security;

-- Read-only to the owner. No write policies: only the security definer RPCs insert or
-- delete, the same shape as user_stats and coin_wallets.
create policy streak_frozen_days_select_own on public.streak_frozen_days
  for select to authenticated using ((select auth.uid()) = user_id);

-- The same grant hygiene 20260916210000 applied to the other nine tables.
revoke all on table public.streak_frozen_days from anon;
revoke truncate, trigger, references on table public.streak_frozen_days from authenticated;

-- ── 3. freezes_on: the balance including this month's free one ──────
-- Mirrored by UserStatsRow.freezesOn in the app, which uses it to show the count and
-- to predict whether a gap is covered. Change both or neither.
create function public.freezes_on(p_freezes int, p_grant_month date, p_today date)
returns int
language sql
immutable
set search_path = public, pg_temp
as $$
  select least(2, coalesce(p_freezes, 0)
    + case when p_grant_month is null
             or p_grant_month < date_trunc('month', p_today::timestamp)::date
           then 1 else 0 end);
$$;

revoke all on function public.freezes_on(int, date, date) from public, anon, authenticated;

-- ── 4. streak_through: frozen days bridge, entries count ────────────
-- Same trick as before over the union of entry dates and frozen days: walking the
-- filled days newest first, day + row_number() stays at p_day + 1 exactly while the run
-- is unbroken. Only the days with an entry are counted. If p_day is neither written nor
-- frozen, nothing matches and the result is 0.
create or replace function public.streak_through(p_user uuid, p_day date)
returns int
language sql
stable
set search_path = public, pg_temp
as $$
  select (count(*) filter (where r.wrote))::int
  from (
    select d.day, d.wrote, row_number() over (order by d.day desc) as rn
    from (
      select f.day, bool_or(f.wrote) as wrote
      from (
        select entry_date as day, true as wrote
        from public.gratitude_entries
        where user_id = p_user and entry_date <= p_day
        union all
        select day, false
        from public.streak_frozen_days
        where user_id = p_user and day <= p_day
      ) f
      group by f.day
    ) d
  ) r
  where r.day + r.rn::int = p_day + 1;
$$;

-- Unchanged signature keeps its ACL; re-asserted anyway.
revoke all on function public.streak_through(uuid, date) from public, anon, authenticated;

-- ── 5. submit_gratitude_entry spends (and refunds) freezes ──────────
-- Same signature as 20260924150000, so replace in place. Unchanged apart from the
-- freeze block and the two extra columns in the final update.
create or replace function public.submit_gratitude_entry(
  p_id           uuid,
  p_entry_text   text,
  p_input_method public.input_method,
  p_time_zone    text        default null,
  p_written_at   timestamptz default null
)
returns public.gratitude_entries
language plpgsql
security definer
set search_path to 'public', 'auth'
as $function$
declare
  v_user        uuid := auth.uid();
  v_tz          text;
  v_written     timestamptz;
  v_day         date;
  v_today       date;
  v_count_day   int;
  v_cap         int;
  v_entry       public.gratitude_entries;
  v_prev_date   date;
  v_freezes     int;
  v_grant_month date;
  v_last_filled date;
  v_gap         int;
  v_streak      int;
  v_reward      int;
  v_balance     int;
  v_text        text := trim(p_entry_text);
begin
  if v_user is null then raise exception 'not authenticated' using errcode = '42501'; end if;
  if p_id is null then raise exception 'entry id required'; end if;
  if v_text is null or length(v_text) = 0 then raise exception 'entry text required'; end if;

  -- Same lock order as water_plant, purchase_item and buy_streak_freeze. Taking them
  -- before the id check serializes two in-flight copies of the same submit: the second
  -- waits, then finds the first one's row.
  select balance into v_balance
  from public.coin_wallets where user_id = v_user for update;

  select last_entry_date, streak_freezes, freeze_grant_month
    into v_prev_date, v_freezes, v_grant_month
  from public.user_stats where user_id = v_user for update;

  select * into v_entry from public.gratitude_entries where id = p_id;
  if found then
    if v_entry.user_id = v_user then
      return v_entry;  -- a replay: already saved and paid
    end if;
    raise exception 'entry id already used';
  end if;

  -- least(..., 50): the cap is read from a table, so it is only as trustworthy
  -- as every write path into that table.
  select daily_entry_cap, time_zone into v_cap, v_tz
  from public.user_settings where user_id = v_user;
  v_cap := least(coalesce(v_cap, 10), 50);

  -- Adopt the zone the entry was written in when it's a real one. An unknown name is
  -- ignored rather than rejected: the entry still saves, in the last known zone.
  if p_time_zone is not null and p_time_zone is distinct from v_tz
     and exists (select 1 from pg_timezone_names where name = p_time_zone) then
    v_tz := p_time_zone;
    update public.user_settings set time_zone = v_tz, updated_at = now() where user_id = v_user;
  end if;
  v_tz := coalesce(v_tz, 'UTC');

  v_written := case
    when p_written_at between now() - interval '36 hours' and now() + interval '5 minutes'
      then p_written_at
    else now()
  end;
  v_day   := (v_written at time zone v_tz)::date;
  v_today := (now() at time zone v_tz)::date;

  select count(*) into v_count_day
  from public.gratitude_entries
  where user_id = v_user and entry_date = v_day;

  if v_count_day >= v_cap then raise exception 'daily entry cap (%) reached', v_cap; end if;

  -- Freezes. The month is the one it is now, not the entry's: a late entry from last
  -- month doesn't get to claim last month's free one.
  v_freezes := public.freezes_on(v_freezes, v_grant_month, v_today);

  -- A backdated entry on a day a freeze covered: the freeze wasn't needed after all.
  delete from public.streak_frozen_days where user_id = v_user and day = v_day;
  if found then
    v_freezes := least(2, v_freezes + 1);
  end if;

  -- The day's first entry after a gap: bridge it if the freezes cover all of it.
  if v_count_day = 0 then
    select max(f.day) into v_last_filled
    from (
      select entry_date as day from public.gratitude_entries
      where user_id = v_user and entry_date < v_day
      union all
      select day from public.streak_frozen_days
      where user_id = v_user and day < v_day
    ) f;

    v_gap := v_day - v_last_filled - 1;
    if v_gap between 1 and v_freezes then
      insert into public.streak_frozen_days (user_id, day)
      select v_user, v_last_filled + i
      from generate_series(1, v_gap) i;
      v_freezes := v_freezes - v_gap;
    end if;
  end if;

  -- The streak this entry extends: the run through yesterday (a frozen yesterday
  -- included), plus its own day. Constant within a day, so entry_reward's first-of-day
  -- rule still pays the bonus once.
  v_streak := public.streak_through(v_user, v_day - 1) + 1;
  v_reward := public.entry_reward(v_text, v_streak, v_count_day = 0);

  insert into public.gratitude_entries
    (id, user_id, entry_text, input_method, coins_awarded, entry_date, created_at)
  values
    (p_id, v_user, v_text, p_input_method, v_reward, v_day, v_written)
  returning * into v_entry;

  update public.coin_wallets
     set balance = balance + v_reward
   where user_id = v_user;

  -- A backdated entry can join two runs, so current_streak is recomputed at the latest
  -- date rather than incremented.
  v_prev_date := greatest(v_prev_date, v_day);
  v_streak    := public.streak_through(v_user, v_prev_date);

  update public.user_stats
     set total_entries      = total_entries + 1,
         total_coins_earned = total_coins_earned + v_reward,
         current_streak     = v_streak,
         longest_streak     = greatest(longest_streak, v_streak),
         last_entry_date    = v_prev_date,
         streak_freezes     = v_freezes,
         freeze_grant_month = date_trunc('month', v_today::timestamp)::date
   where user_id = v_user;

  return v_entry;
end;
$function$;

revoke all     on function public.submit_gratitude_entry(uuid, text, public.input_method, text, timestamptz) from public, anon;
grant  execute on function public.submit_gratitude_entry(uuid, text, public.input_method, text, timestamptz) to authenticated;

-- ── 6. buy_streak_freeze() ──────────────────────────────────────────
-- A consumable, so not an items row: purchase_item returns the existing row for
-- anything already owned, and a freeze has to stack. Returns the new balance of freezes.
-- The price lives here; the app's STREAK_FREEZE_PRICE only labels the button.
create function public.buy_streak_freeze()
returns int
language plpgsql
security definer
set search_path to 'public', 'auth'
as $function$
declare
  v_price       constant int := 50;
  v_user        uuid := auth.uid();
  v_balance     int;
  v_freezes     int;
  v_grant_month date;
  v_tz          text;
  v_today       date;
begin
  if v_user is null then raise exception 'not authenticated' using errcode = '42501'; end if;

  select balance into v_balance
  from public.coin_wallets where user_id = v_user for update;

  select streak_freezes, freeze_grant_month into v_freezes, v_grant_month
  from public.user_stats where user_id = v_user for update;

  select time_zone into v_tz from public.user_settings where user_id = v_user;
  v_today   := (now() at time zone coalesce(v_tz, 'UTC'))::date;
  v_freezes := public.freezes_on(v_freezes, v_grant_month, v_today);

  if v_freezes >= 2 then raise exception 'streak freeze limit reached'; end if;
  if v_balance < v_price then
    raise exception 'insufficient coins (need %, have %)', v_price, v_balance;
  end if;

  update public.coin_wallets set balance = balance - v_price where user_id = v_user;

  -- Banks this month's free one too, so buying never forfeits it.
  update public.user_stats
     set streak_freezes     = v_freezes + 1,
         freeze_grant_month = date_trunc('month', v_today::timestamp)::date
   where user_id = v_user;

  return v_freezes + 1;
end;
$function$;

-- A fresh function picks up pg_default_acl's EXECUTE for anon. Not hygiene -- mandatory.
revoke all     on function public.buy_streak_freeze() from public, anon;
grant  execute on function public.buy_streak_freeze() to authenticated;

notify pgrst, 'reload schema';
