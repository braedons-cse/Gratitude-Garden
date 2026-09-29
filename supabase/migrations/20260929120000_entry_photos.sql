-- A photo on a journal entry (roadmap 1.4). One per entry, set when writing or later from
-- Edit, replaced or removed at any time.
--
--   * Files live in a private bucket, entry-photos, at {user_id}/{entry_id}/{photo_id}.jpg.
--     photo_id is new every time a photo is set, so a replaced photo never shares a name
--     with the one it replaces, and an upload retried with upsert writes the same bytes to
--     the same name.
--
--   * The column is only ever written by set_entry_photo, which checks the path is inside
--     the caller's own folder for that entry and that the file is really there.
--     gratitude_entries has no owner UPDATE policy (20260916210000), and this adds none.
--
--   * The photo is delivered as its own step after the submit, so submit_gratitude_entry,
--     the one journal RPC that pays out, is untouched.
--
--   * SQL can't delete storage objects: storage.protect_delete refuses, because a row
--     deleted that way leaves its file behind. Deleting goes through the Storage API from
--     the app, and own_photo_objects tells it what to delete. That covers a replaced photo,
--     a deleted entry, and account deletion, which Supabase refuses outright while the
--     user still owns any object.

-- ── 1. The bucket ───────────────────────────────────────────────────
-- The app sends JPEGs of a few hundred KB (long edge 1600 px); the limit is a backstop.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('entry-photos', 'entry-photos', false, 3145728, '{image/jpeg}');

-- ── 2. Who may touch which file ─────────────────────────────────────
-- A user's own folder, nothing else. UPDATE is needed as well as INSERT: an upload with
-- upsert checks it when the name already exists, which is exactly the retried upload.
create policy entry_photos_select_own on storage.objects
  for select to authenticated
  using (bucket_id = 'entry-photos' and (storage.foldername(name))[1] = (select auth.uid())::text);

create policy entry_photos_insert_own on storage.objects
  for insert to authenticated
  with check (bucket_id = 'entry-photos' and (storage.foldername(name))[1] = (select auth.uid())::text);

create policy entry_photos_update_own on storage.objects
  for update to authenticated
  using (bucket_id = 'entry-photos' and (storage.foldername(name))[1] = (select auth.uid())::text)
  with check (bucket_id = 'entry-photos' and (storage.foldername(name))[1] = (select auth.uid())::text);

create policy entry_photos_delete_own on storage.objects
  for delete to authenticated
  using (bucket_id = 'entry-photos' and (storage.foldername(name))[1] = (select auth.uid())::text);

-- ── 3. The column ───────────────────────────────────────────────────
alter table public.gratitude_entries
  add column photo_path text,
  add constraint gratitude_entries_photo_path_shape
    check (photo_path ~ '^[0-9a-f-]{36}/[0-9a-f-]{36}/[0-9a-f-]{36}\.jpg$');

-- ── 4. set_entry_photo(uuid, text) ──────────────────────────────────
-- Null removes the photo. Setting the value it already has changes nothing, so a replayed
-- call is harmless. The old file, if any, is the app's to delete afterwards.
create function public.set_entry_photo(p_entry_id uuid, p_photo_path text)
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

  if p_photo_path is not null then
    -- The check constraint pins the shape; this pins whose folder and which entry.
    if p_photo_path not like v_user::text || '/' || p_entry_id::text || '/%' then
      raise exception 'photo path not allowed';
    end if;
    if not exists (
      select 1 from storage.objects where bucket_id = 'entry-photos' and name = p_photo_path
    ) then
      raise exception 'photo not uploaded';
    end if;
  end if;

  update public.gratitude_entries
     set photo_path = p_photo_path
   where id = p_entry_id and user_id = v_user and deleted_at is null
   returning * into v_entry;

  -- Same wording as edit_gratitude_entry, so the app reads it the same way.
  if v_entry.id is null then raise exception 'entry not found'; end if;
  return v_entry;
end;
$$;

-- A fresh function picks up pg_default_acl's EXECUTE for anon. Not hygiene -- mandatory.
revoke all     on function public.set_entry_photo(uuid, text) from public, anon;
grant  execute on function public.set_entry_photo(uuid, text) to authenticated;

-- ── 5. own_photo_objects(uuid) ──────────────────────────────────────
-- The names of the caller's files, all of them or one entry's. The storage schema isn't
-- served by PostgREST, and listing through the Storage API goes one folder at a time.
create function public.own_photo_objects(p_entry_id uuid default null)
returns setof text
language sql
stable
security definer
set search_path = public, pg_temp
as $$
  select o.name
  from storage.objects o
  where o.bucket_id = 'entry-photos'
    and o.name like auth.uid()::text || '/' || coalesce(p_entry_id::text || '/', '') || '%'
  order by o.name;
$$;

revoke all     on function public.own_photo_objects(uuid) from public, anon;
grant  execute on function public.own_photo_objects(uuid) to authenticated;

-- ── 6. delete_gratitude_entry: a deleted entry lets go of its photo ──
-- The app deletes the file once this has answered. Clearing the path first means no copy
-- of the row ever points at a file that is gone.
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
         photo_path = null
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

notify pgrst, 'reload schema';
