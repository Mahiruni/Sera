-- Hosted Supabase only: private storage bucket and object policies.
insert into storage.buckets(id,name,public,file_size_limit,allowed_mime_types)
values('professional-message-files','professional-message-files',false,5242880,
 array['image/jpeg','image/png','image/webp','application/pdf'])
on conflict(id) do update set public=false,file_size_limit=5242880,
 allowed_mime_types=excluded.allowed_mime_types;
create policy "Message owners upload private files" on storage.objects
for insert to authenticated with check (
 bucket_id='professional-message-files'
 and (storage.foldername(name))[1]=(select auth.uid())::text
 and exists(select 1 from public.professional_conversations c
 where c.id=(((storage.foldername(name))[2])::uuid)
 and (c.member_a=(select auth.uid()) or c.member_b=(select auth.uid())))
);
create policy "Participants read private message files" on storage.objects
for select to authenticated using (
 bucket_id='professional-message-files'
 and exists(select 1 from public.professional_message_attachments a
 join public.professional_messages m on m.id=a.message_id
 where a.storage_path=name)
);
