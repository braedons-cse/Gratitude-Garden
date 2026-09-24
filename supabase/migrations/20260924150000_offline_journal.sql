-- Journal entries can be written offline and synced later (docs/offline-first-plan.md,
-- phase 2). Three things the server assumed until now stop being true:
--
--   * One request = one entry. A queued submit is retried until it gets an answer, and a
--     request whose response is lost reaches the server twice. The client now generates
--     the entry id, and a second submit with the same id returns the first row untouched:
--     no second insert, no second reward.
--
--   * An entry is written when it arrives. An entry written at 11 PM and synced the next
--     morning should count for the night it was written. The client sends that instant,
--     trusted within [now() - 36h, now() + 5 min]; anything outside falls back to now().
--
--   * Entries arrive in date order. next_streak() advanced the streak one arrival at a
--     time, which a backdated entry breaks. The streak is now recomputed from the dates
--     themselves.
--
-- Backdating replaces 20260923140000's monotonic day (greatest(local date,
-- last_entry_date)), which by design refused any date before the latest one. The bound
-- it protected still holds another way: a zone moves the local date by at most one day,
-- and backdating is capped at 36 h, so the most anyone can gain is yesterday's unused
-- cap -- a grace period the offline plan accepts on purpose. The cap is still counted per
-- entry_date, deleted rows included, and still clamped at 50.
--
-- The zone used is the one sent with the entry, captured on the device when it was
-- written. A queue replayed after travel therefore dates each entry where it was written;
-- the last op to sync also leaves its zone as user_settings.time_zone, which is at worst
-- briefly stale and corrected by the next entry.

-- ── 1. The streak, from the dates ───────────────────────────────────
-- Consecutive days with an entry, ending at p_day. Deleted entries count: a delete refunds
-- no coins and never touched user_stats, so a day you wrote on stays a day you wrote on.
--
-- Walking the distinct dates newest first, date + row_number() is constant (= p_day + 1)
-- exactly while the run is unbroken; after the first gap it is strictly smaller forever.
-- If p_day itself has no entry, nothing matches and the result is 0.
create function public.streak_through(p_user uuid, p_day date)
returns int
language sql
stable
set search_path = public, pg_temp
as $$
  select count(*)::int
  from (
    select d.entry_date, row_number() over (order by d.entry_date desc) as rn
    from (
      select distinct entry_date
      from public.gratitude_entries
      where user_id = p_user and entry_date <= p_day
    ) d
  ) r
  where r.entry_date + r.rn::int = p_day + 1;
$$;

-- Takes an arbitrary user id, so nobody but the owner calls it directly. The security
-- definer RPCs below run as the owner.
revoke all on function public.streak_through(uuid, date) from public, anon, authenticated;

-- Both the streak and the cap count read every row, deleted ones included, so the old
-- partial index (where deleted_at is null) served neither.
drop index if exists public.gratitude_entries_user_date_idx;
create index gratitude_entries_user_date_idx
  on public.gratitude_entries (user_id, entry_date);

-- ── 2. submit_gratitude_entry(uuid, text, input_method, text, timestamptz) ──
-- Drop, don't supersede: a leftover three-argument overload would still accept
-- id-less, retry-unsafe submits.
drop function if exists public.submit_gratitude_entry(text, public.input_method, text);

create function public.submit_gratitude_entry(
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
  v_count_day   int;
  v_cap         int;
  v_entry       public.gratitude_entries;
  v_prev_date   date;
  v_streak      int;
  v_reward      int;
  v_balance     int;
  v_text        text := trim(p_entry_text);
begin
  if v_user is null then raise exception 'not authenticated' using errcode = '42501'; end if;
  if p_id is null then raise exception 'entry id required'; end if;
  if v_text is null or length(v_text) = 0 then raise exception 'entry text required'; end if;

  -- Same lock order as water_plant and purchase_item. Taking them before the id check
  -- serializes two in-flight copies of the same submit: the second waits, then finds
  -- the first one's row.
  select balance into v_balance
  from public.coin_wallets where user_id = v_user for update;

  select last_entry_date into v_prev_date
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
  v_day := (v_written at time zone v_tz)::date;

  select count(*) into v_count_day
  from public.gratitude_entries
  where user_id = v_user and entry_date = v_day;

  if v_count_day >= v_cap then raise exception 'daily entry cap (%) reached', v_cap; end if;

  -- The streak this entry extends: the run through yesterday, plus its own day. Constant
  -- within a day, so entry_reward's first-of-day rule still pays the bonus once.
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
         last_entry_date    = v_prev_date
   where user_id = v_user;

  return v_entry;
end;
$function$;

-- A fresh function picks up pg_default_acl's EXECUTE for anon. Not hygiene -- mandatory.
revoke all     on function public.submit_gratitude_entry(uuid, text, public.input_method, text, timestamptz) from public, anon;
grant  execute on function public.submit_gratitude_entry(uuid, text, public.input_method, text, timestamptz) to authenticated;

-- Nothing calls it any more, and it was the one function still executable by anon.
drop function if exists public.next_streak(date, int, date);

-- ── 3. delete_gratitude_entry: a replayed delete is not an error ──────
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
  if not found and not exists (
    select 1 from public.gratitude_entries where id = p_entry_id and user_id = v_user
  ) then
    raise exception 'entry not found';
  end if;
end;
$$;

-- Unchanged signature keeps its ACL; re-asserted anyway, as in 20260916210000.
revoke all     on function public.delete_gratitude_entry(uuid) from public, anon;
grant  execute on function public.delete_gratitude_entry(uuid) to authenticated;

-- ── 4. No default for entry_date ────────────────────────────────────
-- The RPC always sets it, and a UTC default that disagrees with the rules above is a trap.
alter table public.gratitude_entries alter column entry_date drop default;

notify pgrst, 'reload schema';
