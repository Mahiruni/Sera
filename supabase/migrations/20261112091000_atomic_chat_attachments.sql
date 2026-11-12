-- Atomic registration of a message and its already uploaded private attachment.
create or replace function public.sera_send_message_attachment(
 p_conversation uuid,p_body text,p_path text,p_name text,p_mime text,p_size integer
) returns uuid language plpgsql security invoker set search_path='' as $$
declare message_id uuid;
begin
 if (select auth.uid()) is null or p_path is null or p_path not like (select auth.uid())::text || '/' || p_conversation::text || '/%' then
  raise exception 'Invalid attachment owner or conversation';
 end if;
 if not exists(select 1 from storage.objects o where o.bucket_id='professional-message-files' and o.name=p_path and o.owner_id=(select auth.uid())::text) then raise exception 'Attachment not found'; end if;
 if p_mime not in ('image/jpeg','image/png','image/webp','application/pdf') or p_size not between 1 and 5242880 then raise exception 'Unsupported attachment'; end if;
 insert into public.professional_messages(conversation_id,sender_id,body)
 values(p_conversation,(select auth.uid()),coalesce(nullif(trim(p_body),''),'Attachment')) returning id into message_id;
 insert into public.professional_message_attachments(message_id,owner_id,storage_path,file_name,mime_type,byte_size)
 values(message_id,(select auth.uid()),p_path,left(p_name,180),p_mime,p_size);
 return message_id;
end; $$;
revoke all on function public.sera_send_message_attachment(uuid,text,text,text,text,integer) from public,anon;
grant execute on function public.sera_send_message_attachment(uuid,text,text,text,text,integer) to authenticated;
