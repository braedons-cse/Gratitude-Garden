-- The first-week funnel (roadmap 0.6): signed up → wrote a first entry → came back.
--
--   * Built only from what's already stored. The app sends no analytics events, so there's
--     nothing new to collect or declare on the Data Safety form. "Came back" therefore means
--     wrote again, not just opened the app; for a journal that's the number that matters.
--
--   * Days are the user's own, like everything else here (20260923140000): day 1 is the
--     local date of signup in user_settings.time_zone, and entries count by entry_date.
--     Deleted entries still count. Writing one was the activity.
--
--   * Admins are left out: today they're the test accounts.
--
--   * It lives in its own schema, which PostgREST doesn't serve, and only the database owner
--     can read it. Read it in the dashboard's SQL editor:
--
--       select * from analytics.signup_funnel;

create schema analytics;
revoke all on schema analytics from public, anon, authenticated;

comment on schema analytics is
  'Owner-only reporting views over public. Not exposed through the API; read from the SQL editor.';

-- ── 1. One row per person ───────────────────────────────────────────
create view analytics.signup_journeys as
with signups as (
  select
    p.id as user_id,
    p.created_at as signed_up_at,
    (p.created_at at time zone coalesce(s.time_zone, 'UTC'))::date as day_1,
    (now() at time zone coalesce(s.time_zone, 'UTC'))::date as local_today
  from public.profiles p
  left join public.user_settings s on s.user_id = p.id
  where not p.is_admin
)
select
  su.user_id,
  su.signed_up_at,
  su.day_1,
  min(e.entry_date) as first_entry_day,
  coalesce(bool_or(e.entry_date = su.day_1), false) as wrote_day_1,
  -- A day counts only once it's over, so a cohort from yesterday isn't read as churned.
  su.local_today > su.day_1 + 1 as day_2_over,
  coalesce(bool_or(e.entry_date = su.day_1 + 1), false) as wrote_day_2,
  su.local_today > su.day_1 + 6 as day_7_over,
  coalesce(bool_or(e.entry_date = su.day_1 + 6), false) as wrote_day_7,
  -- Bounded below too: seeded test accounts have entries from before their profile.
  count(distinct e.entry_date) filter (where e.entry_date between su.day_1 and su.day_1 + 6)
    as days_written_week_1
from signups su
left join public.gratitude_entries e on e.user_id = su.user_id
group by su.user_id, su.signed_up_at, su.day_1, su.local_today;

comment on view analytics.signup_journeys is
  'Per non-admin user: signup day (local), first entry day, and whether they wrote on days 1, 2 and 7. Day 1 is the signup day.';

-- ── 2. By signup week ───────────────────────────────────────────────
-- The day-2 and day-7 rates are out of the people whose day 2 or 7 is already over.
create view analytics.signup_funnel as
select
  date_trunc('week', day_1)::date as cohort_week,
  count(*) as signed_up,
  count(*) filter (where first_entry_day is not null) as wrote_an_entry,
  count(*) filter (where wrote_day_1) as wrote_day_1,
  count(*) filter (where day_2_over) as day_2_over,
  count(*) filter (where day_2_over and wrote_day_2) as wrote_day_2,
  round(100.0 * count(*) filter (where day_2_over and wrote_day_2)
        / nullif(count(*) filter (where day_2_over), 0), 1) as day_2_pct,
  count(*) filter (where day_7_over) as day_7_over,
  count(*) filter (where day_7_over and wrote_day_7) as wrote_day_7,
  round(100.0 * count(*) filter (where day_7_over and wrote_day_7)
        / nullif(count(*) filter (where day_7_over), 0), 1) as day_7_pct,
  round(avg(days_written_week_1) filter (where day_7_over), 2) as avg_days_written_week_1
from analytics.signup_journeys
group by 1
order by 1 desc;

comment on view analytics.signup_funnel is
  'Signup → first entry → wrote on day 2 → wrote on day 7, by signup week. Percentages are out of users whose day has passed.';

revoke all on all tables in schema analytics from public, anon, authenticated;
