-- A client must not replace a claimed object while the storage API is deleting it.
create or replace function public.sera_chat_upload_unclaimed(p_bucket text,p_path text) returns boolean
language plpgsql security definer set search_path='' as $$
begin
 if p_bucket is distinct from 'professional-message-files' then return true;end if;
 perform pg_advisory_xact_lock(hashtextextended(p_path,2026111306));
 return not exists(select 1 from sera_attachment_private.cleanup_claims c where c.path=p_path);
end; $$;
revoke all on function public.sera_chat_upload_unclaimed(text,text) from public,anon;
grant execute on function public.sera_chat_upload_unclaimed(text,text) to authenticated;
create policy "Chat cleanup prevents client replacement" on storage.objects as restrictive
for delete to authenticated using (public.sera_chat_upload_unclaimed(bucket_id,name));
create policy "Chat cleanup prevents client reupload" on storage.objects as restrictive
for insert to authenticated with check (public.sera_chat_upload_unclaimed(bucket_id,name));
