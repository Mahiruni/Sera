-- Small MP4 clips use the existing private bucket and single-file send contract.
alter table public.professional_message_attachments drop constraint professional_message_attachments_mime_type_check;
alter table public.professional_message_attachments add constraint professional_message_attachments_mime_type_check check(mime_type in ('image/jpeg','image/png','image/webp','application/pdf','audio/mp4','audio/webm','audio/ogg','audio/mpeg','video/mp4'));
update storage.buckets set allowed_mime_types=array_append(allowed_mime_types,'video/mp4') where id='professional-message-files' and not ('video/mp4'=any(allowed_mime_types));
create or replace function public.sera_send_message_v2(
 p_conversation uuid,p_client_id uuid,p_body text default '',p_reply_to uuid default null,
 p_forward_source uuid default null,p_path text default null,p_name text default null,
 p_mime text default null,p_size integer default null,p_duration_ms integer default null
) returns uuid language plpgsql security invoker set search_path='' as $$
declare v_id uuid; v_body text; v_sender uuid; v_source public.professional_messages%rowtype;
 v_source_file public.professional_message_attachments%rowtype; v_count integer; v_metadata jsonb;
begin
 v_sender=(select auth.uid());
 if v_sender is null or p_client_id is null then raise exception 'Authentication and client message ID required'; end if;
 if not exists(select 1 from public.professional_conversations where id=p_conversation) then raise exception 'Conversation unavailable'; end if;
 select id into v_id from public.professional_messages where conversation_id=p_conversation and sender_id=v_sender and client_message_id=p_client_id;
 if found then return v_id; end if;
 v_body=trim(coalesce(p_body,''));
 if p_forward_source is not null then
  if p_reply_to is not null then raise exception 'Cannot combine reply and forward'; end if;
  select * into v_source from public.professional_messages where id=p_forward_source and unsent_at is null;
  if not found then raise exception 'Forward source unavailable'; end if;
  v_body=v_source.body;
  select count(*) into v_count from public.professional_message_attachments where message_id=p_forward_source;
  if v_count>1 then raise exception 'Forward one attachment at a time'; end if;
  if v_count=1 then
   select * into v_source_file from public.professional_message_attachments where message_id=p_forward_source;
   if p_path is null or p_mime is distinct from v_source_file.mime_type or p_size is distinct from v_source_file.byte_size then
    raise exception 'Forwarded attachment must be copied to destination';
   end if;
   p_name=v_source_file.file_name; p_duration_ms=v_source_file.duration_ms;
  elsif p_path is not null then raise exception 'Unexpected forwarded attachment'; end if;
 end if;
 if p_path is not null then
  if p_path not like v_sender::text || '/' || p_conversation::text || '/%' or
    split_part(p_path,'/',3) !~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\.(jpg|jpeg|png|webp|pdf|m4a|mp4|webm|ogg|mp3)$'
    or array_length(string_to_array(p_path,'/'),1)<>3 then raise exception 'Invalid attachment path'; end if;
  if p_mime is null or p_mime not in ('image/jpeg','image/png','image/webp','application/pdf','audio/mp4','audio/webm','audio/ogg','audio/mpeg','video/mp4')
   or p_size is null or p_size not between 1 and 5242880 or nullif(trim(p_name),'') is null then
   raise exception 'Invalid attachment';
  end if;
  if p_duration_ms is not null and (p_mime not like 'audio/%' or p_duration_ms not between 1 and 120000) then raise exception 'Invalid voice duration'; end if;
  select metadata into v_metadata from storage.objects where bucket_id='professional-message-files' and name=p_path and owner_id=v_sender::text;
  if not found then raise exception 'Attachment not found'; end if;
  if v_metadata->>'size' is not null and (v_metadata->>'size')::bigint<>p_size then raise exception 'Attachment size mismatch'; end if;
  if v_metadata->>'mimetype' is not null and lower(split_part(v_metadata->>'mimetype',';',1))<>p_mime then raise exception 'Attachment type mismatch'; end if;
  if v_body='' then v_body=case when p_mime like 'audio/%' then 'Voice message' else 'Attachment' end; end if;
 elsif p_name is not null or p_mime is not null or p_size is not null or p_duration_ms is not null then
  raise exception 'Attachment path required';
 end if;
 if char_length(v_body) not between 1 and 4000 then raise exception 'Message must contain 1–4000 characters'; end if;
 insert into public.professional_messages(conversation_id,sender_id,body,reply_to_id,is_forwarded,client_message_id)
 values(p_conversation,v_sender,v_body,p_reply_to,p_forward_source is not null,p_client_id)
 on conflict(conversation_id,sender_id,client_message_id) do nothing returning id into v_id;
 if v_id is null then
  select id into v_id from public.professional_messages where conversation_id=p_conversation and sender_id=v_sender and client_message_id=p_client_id;
  return v_id;
 end if;
 if p_path is not null then
  insert into public.professional_message_attachments(message_id,owner_id,storage_path,file_name,mime_type,byte_size,duration_ms)
  values(v_id,v_sender,p_path,left(trim(p_name),180),p_mime,p_size,p_duration_ms);
 end if;
 return v_id;
end; $$;
revoke all on function public.sera_send_message_v2(uuid,uuid,text,uuid,uuid,text,text,text,integer,integer) from public,anon;
grant execute on function public.sera_send_message_v2(uuid,uuid,text,uuid,uuid,text,text,text,integer,integer) to authenticated;

