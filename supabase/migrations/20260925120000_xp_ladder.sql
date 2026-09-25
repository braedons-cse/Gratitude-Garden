-- The XP ladder (roadmap 1.3). profiles.level and profiles.xp have existed since the
-- foundation migration, but nothing ever wrote them: every account was level 1 with 0 XP,
-- so purchase_item's level_required check made every item above level 1 unbuyable, by
-- anyone, forever.
--
--   * What earns XP. Days journaled, not volume: 10 for the day's first entry, 2 for each
--     extra one. The daily cap still bounds the extras, and "first of the day" is counted
--     the way the streak bonus is (soft-deleted entries included), so deleting and
--     rewriting can't re-earn the 10.
--
--   * The curve. level = floor(sqrt(xp / 10 + 1)), so level n starts at 10 * (n^2 - 1):
--     30, 80, 150, 240, 350 ... About 3 days to level 2 and 3-4 weeks to level 5 (the
--     highest the catalog asks for) journaling once a day. No cap.
--
--   * Level follows XP by trigger, not by each writer. The RPC, the backfill below and the
--     admin dashboard's xp field all go through it. Only an xp write fires it, so an admin
--     can still set level directly for testing.
--
--   * Stored per entry, like coins. xp_awarded sits next to coins_awarded, which is how the
--     app hears what an entry earned: submit_gratitude_entry returns the entry row, and the
--     new column rides along without a signature change.
--
--   * Backfilled from history with the same rule, so existing accounts land where their
--     journaling put them.
--
-- A replayed submit returns before any of this, so XP is paid once. Delete refunds nothing,
-- the same as coins.

-- ── 1. level_for_xp ─────────────────────────────────────────────────
-- Mirrored by levelForXp in the app (Levels.kt), which draws the progress bar. Change both
-- or neither. numeric sqrt, so the exact squares at each threshold don't round down.
create function public.level_for_xp(p_xp int)
returns int
language sql
immutable
set search_path = public, pg_temp
as $$
  select greatest(1, floor(sqrt((greatest(p_xp, 0) + 10) / 10.0))::int);
$$;

revoke all on function public.level_for_xp(int) from public, anon, authenticated;

-- ── 2. Level follows XP ─────────────────────────────────────────────
create function public.sync_profile_level()
returns trigger
language plpgsql
set search_path = public, pg_temp
as $$
begin
  new.level := public.level_for_xp(new.xp);
  return new;
end;
$$;

revoke all on function public.sync_profile_level() from public, anon, authenticated;

create trigger profiles_sync_level
  before insert or update of xp on public.profiles
  for each row execute function public.sync_profile_level();

-- ── 3. What each entry earned ───────────────────────────────────────
alter table public.gratitude_entries
  add column xp_awarded int not null default 0 check (xp_awarded >= 0);

comment on column public.gratitude_entries.xp_awarded is
  'XP this entry paid: 10 for the day''s first, 2 after. Set by submit_gratitude_entry.';

-- ── 4. submit_gratitude_entry pays XP ───────────────────────────────
-- Same signature as 20260924200000, so replace in place. Unchanged apart from v_xp, the
-- xp_awarded column in the insert, and the profiles update.
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
  v_xp          int;
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
  v_xp     := case when v_count_day = 0 then 10 else 2 end;

  insert into public.gratitude_entries
    (id, user_id, entry_text, input_method, coins_awarded, xp_awarded, entry_date, created_at)
  values
    (p_id, v_user, v_text, p_input_method, v_reward, v_xp, v_day, v_written)
  returning * into v_entry;

  update public.coin_wallets
     set balance = balance + v_reward
   where user_id = v_user;

  -- profiles_sync_level moves level along with it.
  update public.profiles
     set xp = xp + v_xp
   where id = v_user;

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

-- ── 5. Backfill ─────────────────────────────────────────────────────
-- Every entry, deleted ones included: they were paid, and they still count as the day's
-- first for the rule above.
update public.gratitude_entries e
   set xp_awarded = case when r.rn = 1 then 10 else 2 end
  from (
    select id, row_number() over (partition by user_id, entry_date order by created_at, id) as rn
    from public.gratitude_entries
  ) r
 where e.id = r.id;

update public.profiles p
   set xp = coalesce((select sum(e.xp_awarded) from public.gratitude_entries e
                       where e.user_id = p.id), 0);

notify pgrst, 'reload schema';
