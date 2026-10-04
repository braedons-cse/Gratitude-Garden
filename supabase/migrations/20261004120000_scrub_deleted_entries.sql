-- Deleting an entry erases what it said (roadmap 0.4).
--
--   * Until now delete_gratitude_entry only stamped deleted_at, so a deleted entry's text and
--     mood stayed on the server for as long as the account did. The privacy policy says
--     deleting an entry erases it; this makes that true.
--
--   * The row itself stays. The daily cap counts deleted entries on purpose (20260910051500:
--     submit → delete → submit must not mint), and so do the streak and the sign-up funnel.
--     They need the row's day, not its words.
--
--   * The text becomes a fixed marker rather than null or ''. entry_text is
--     not null check (length(trim(entry_text)) > 0), and every released APK reads it as a
--     non-null String, so a tombstone they sync must still parse. The app never shows a
--     deleted row (every Room query filters deletedAt), and its sync skips entries with a
--     pending or refused change, so the marker never replaces text still on a device.
--
--   * Same signature, so create or replace keeps the function's grants. Re-asserted anyway.

-- ── 1. delete_gratitude_entry lets go of the words too ─────────────
-- Unchanged from 20260929120000 apart from entry_text and mood.
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
     set deleted_at = now(),
         photo_path = null,
         entry_text = '(deleted)',
         mood       = null
   where id = p_entry_id and user_id = v_user and deleted_at is null;
  if not found and not exists (
    select 1 from public.gratitude_entries where id = p_entry_id and user_id = v_user
  ) then
    raise exception 'entry not found';
  end if;
end;
$$;

revoke all     on function public.delete_gratitude_entry(uuid) from public, anon;
grant  execute on function public.delete_gratitude_entry(uuid) to authenticated;

-- ── 2. Entries already deleted ─────────────────────────────────────
update public.gratitude_entries
   set entry_text = '(deleted)',
       mood       = null
 where deleted_at is not null
   and (entry_text <> '(deleted)' or mood is not null);

notify pgrst, 'reload schema';
