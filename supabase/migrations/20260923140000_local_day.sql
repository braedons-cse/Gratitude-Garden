-- A "day" is the user's local day, not the UTC day.
--
-- Until now submit_gratitude_entry dated every entry (now() at time zone 'UTC')::date.
-- For a user in New York the day therefore flipped at 8 PM local -- the reminder's
-- default time -- so an evening entry was filed under tomorrow, the journal showed today
-- as unwritten right after writing, and Monday-morning + Tuesday-evening broke the streak
-- (UTC days Mon and Wed) although the user wrote on consecutive local days.
--
-- The client now sends its IANA zone with every entry. Sending it with the entry, rather
-- than syncing it separately, means the zone the day is computed in is always the zone
-- the device is in at that moment -- travel included -- with no second round trip.
--
-- The zone is client-supplied, so it is untrusted. What it could buy an attacker, and why
-- that is bounded:
--   * Zones span UTC-12 .. UTC+14, so a zone change can move the local date by at most
--     one day relative to any other zone.
--   * The entry day never moves backwards: it is greatest(local date, last_entry_date).
--     Hopping east to reach "tomorrow" early and then back west does not reopen
--     yesterday for a second cap.
-- Together: the most anyone gains from zone games is one extra day's cap, once. Not
-- worth a rate limit; worth this comment.

-- ── 1. Where the zone lives ─────────────────────────────────────────
-- reminder_timezone was created in 01_foundation and never read or written by anything
-- (reminders are scheduled on-device from DataStore). Repurpose it under an honest name.
alter table public.user_settings rename column reminder_timezone to time_zone;

-- ── 2. submit_gratitude_entry(text, input_method, text) ────────────
-- Drop, don't supersede: CREATE OR REPLACE can't add a parameter, and a leftover
-- two-argument overload would keep dating entries in UTC for any caller that omits it.
drop function if exists public.submit_gratitude_entry(text, public.input_method);

create function public.submit_gratitude_entry(
  p_entry_text   text,
  p_input_method public.input_method,
  p_time_zone    text default null
)
returns public.gratitude_entries
language plpgsql
security definer
set search_path to 'public', 'auth'
as $function$
declare
  v_user        uuid := auth.uid();
  v_tz          text;
  v_today       date;
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
  select daily_entry_cap, time_zone into v_cap, v_tz
  from public.user_settings where user_id = v_user;
  v_cap := least(coalesce(v_cap, 10), 50);

  -- Adopt the device's zone when it's a real one and differs from what's stored. An
  -- unknown name is ignored rather than rejected: the entry still saves, in the last
  -- known zone. pg_timezone_names is only consulted when the zone actually changes.
  if p_time_zone is not null and p_time_zone is distinct from v_tz
     and exists (select 1 from pg_timezone_names where name = p_time_zone) then
    v_tz := p_time_zone;
    update public.user_settings set time_zone = v_tz, updated_at = now() where user_id = v_user;
  end if;
  v_tz := coalesce(v_tz, 'UTC');

  select balance into v_balance
  from public.coin_wallets where user_id = v_user for update;

  select last_entry_date, current_streak
    into v_prev_date, v_prev_streak
  from public.user_stats where user_id = v_user for update;

  -- Monotonic: never date an entry before the previous one (see header).
  v_today := greatest((now() at time zone v_tz)::date, v_prev_date);

  select count(*) into v_count_today
  from public.gratitude_entries
  where user_id = v_user and entry_date = v_today;

  if v_count_today >= v_cap then raise exception 'daily entry cap (%) reached', v_cap; end if;

  v_new_streak := public.next_streak(v_prev_date, coalesce(v_prev_streak, 0), v_today);

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

-- A fresh function picks up pg_default_acl's EXECUTE for anon. Not hygiene -- mandatory.
revoke all     on function public.submit_gratitude_entry(text, public.input_method, text) from public, anon;
grant  execute on function public.submit_gratitude_entry(text, public.input_method, text) to authenticated;
