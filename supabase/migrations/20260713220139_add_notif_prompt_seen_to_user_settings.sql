ALTER TABLE public.user_settings
  ADD COLUMN IF NOT EXISTS notif_prompt_seen boolean NOT NULL DEFAULT false;

COMMENT ON COLUMN public.user_settings.notif_prompt_seen IS
  'True once the one-time "enable daily reminders?" prompt has been shown to this user (accept or decline). Prevents re-showing it. Set by the app after the first gratitude entry.';
