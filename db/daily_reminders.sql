-- Daily streak reminders — backend change.
--
-- Version-controlled copy of the Supabase migration
-- `add_notif_prompt_seen_to_user_settings` (idempotent — safe to re-run).
--
-- The reminder *preferences* (on/off + time-of-day) are stored on-device via
-- DataStore. Only this one-time "have we already asked to enable reminders?"
-- flag is persisted server-side, so the opt-in prompt is shown exactly once per
-- account rather than once per install. The app sets it (to true) after the
-- user's first gratitude entry by calling the mark_notif_prompt_seen() RPC.
--
-- It used to be a plain UPDATE, permitted by a user_settings_update_own RLS
-- policy. That policy was dropped in 20260916210000: it checked which ROW the
-- caller owned but not which COLUMNS they could write, so it also handed the
-- client daily_entry_cap — the ceiling submit_gratitude_entry reads to decide
-- daily coin earnings. handle_new_user() still provisions the row; the RPC just
-- latches this one flag.

ALTER TABLE public.user_settings
  ADD COLUMN IF NOT EXISTS notif_prompt_seen boolean NOT NULL DEFAULT false;

COMMENT ON COLUMN public.user_settings.notif_prompt_seen IS
  'True once the one-time "enable daily reminders?" prompt has been shown to this user (accept or decline). Prevents re-showing it. Set by the app after the first gratitude entry.';
