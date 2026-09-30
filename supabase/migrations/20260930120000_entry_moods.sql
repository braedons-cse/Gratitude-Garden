-- A mood on a journal entry (roadmap 1.4). Optional: 1 = rough, 2 = low, 3 = okay,
-- 4 = good, 5 = great, null = none given. Ordered, so 1.5's insights can average and
-- trend it.
--
--   * It travels with the text. submit_gratitude_entry takes it when the entry is written,
--     and edit_gratitude_entry sets text and mood together: an edit always sends the
--     entry's whole state, so a replayed or folded edit leaves the same result.
--
--   * Only those two RPCs write the column. gratitude_entries has no owner UPDATE policy
--     (20260916210000), and this adds none.
--
--   * No reward. Text is what makes an entry; a mood changes nothing about coins, XP, the
--     cap or the streak. A replayed submit still returns the saved row untouched, so a
--     retried entry's current mood arrives by the edit that follows it.
--
--   * Both RPCs gain a parameter, so each is dropped and created again rather than
--     replaced: PostgREST picks an overload by the body's keys, and the old one would stay
--     reachable. A fresh function inherits EXECUTE for anon from the default ACL, so the
--     revokes below are required.

-- ── 1. The column ───────────────────────────────────────────────────
alter table public.gratitude_entries
  add column mood smallint,
  add constraint gratitude_entries_mood_range check (mood between 1 and 5);

comment on column public.gratitude_entries.mood is
  '1 rough, 2 low, 3 okay, 4 good, 5 great; null when none was given. Set by submit_gratitude_entry and edit_gratitude_entry.';

-- ── 2. submit_gratitude_entry takes a mood ──────────────────────────
-- Unchanged from 20260925120000 apart from p_mood and the mood column in the insert.
drop function public.submit_gratitude_entry(uuid, text, public.input_method, text, timestamptz);

create function public.submit_gratitude_entry(
  p_id           uuid,
  p_entry_text   text,
  p_input_method public.input_method,
  p_time_zone    text        default null,
  p_written_at   timestamptz default null,
  p_mood         smallint    default null
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
    (id, user_id, entry_text, input_method, mood, coins_awarded, xp_awarded, entry_date, created_at)
  values
    (p_id, v_user, v_text, p_input_method, p_mood, v_reward, v_xp, v_day, v_written)
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

revoke all     on function public.submit_gratitude_entry(uuid, text, public.input_method, text, timestamptz, smallint) from public, anon;
grant  execute on function public.submit_gratitude_entry(uuid, text, public.input_method, text, timestamptz, smallint) to authenticated;

-- ── 3. edit_gratitude_entry sets text and mood ──────────────────────
-- p_mood has no default: an edit always says what the mood is now, null meaning none.
drop function public.edit_gratitude_entry(uuid, text);

create function public.edit_gratitude_entry(p_entry_id uuid, p_new_text text, p_mood smallint)
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
     set entry_text = trim(p_new_text),
         mood       = p_mood
   where id = p_entry_id and user_id = v_user and deleted_at is null
   returning * into v_entry;

  if v_entry.id is null then raise exception 'entry not found'; end if;
  return v_entry;
end;
$$;

revoke all     on function public.edit_gratitude_entry(uuid, text, smallint) from public, anon;
grant  execute on function public.edit_gratitude_entry(uuid, text, smallint) to authenticated;

notify pgrst, 'reload schema';
