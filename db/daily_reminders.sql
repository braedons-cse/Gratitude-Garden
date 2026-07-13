-- Daily streak reminders — backend change.
--
-- Version-controlled copy of the Supabase migration
-- `add_notif_prompt_seen_to_user_settings` (idempotent — safe to re-run).
--
-- The reminder *preferences* (on/off + time-of-day) are stored on-device via
-- DataStore. Only this one-time "have we already asked to enable reminders?"
-- flag is persisted server-side, so the opt-in prompt is shown exactly once per
-- account rather than once per install. The app sets it (to true) after the
-- user's first gratitude entry, via a plain UPDATE — handle_new_user() already
-- provisions the user_settings row and the existing user_settings_update_own
-- RLS policy permits the write.

ALTER TABLE public.user_settings
  ADD COLUMN IF NOT EXISTS notif_prompt_seen boolean NOT NULL DEFAULT false;

COMMENT ON COLUMN public.user_settings.notif_prompt_seen IS
  'True once the one-time "enable daily reminders?" prompt has been shown to this user (accept or decline). Prevents re-showing it. Set by the app after the first gratitude entry.';
