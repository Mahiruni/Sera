-- Ordered photo albums, additive and backwards compatible with existing single-file clients.
alter table public.professional_message_attachments add column if not exists position smallint not null default 0 check(position between 0 and 3);
create unique index if not exists sera_message_attachment_position on public.professional_message_attachments(message_id,position);
create or replace function public.sera_send_photo_album(p_conversation uuid,p_client_id uuid,p_body text,p_reply_to uuid,p_files jsonb)
returns uuid language plpgsql security invoker set search_path='' as $$
declare u uuid:=auth.uid(); mid uuid; item jsonb; path text; mime text; fname text; bytes integer; idx integer:=0; meta jsonb; caption text:=trim(coalesce(p_body,'')); seen text[]:='{}';
begin
 if u is null or p_client_id is null or not exists(select 1 from public.professional_conversations where id=p_conversation) then raise exception 'Conversation unavailable'; end if;
 select id into mid from public.professional_messages where conversation_id=p_conversation and sender_id=u and client_message_id=p_client_id;
 if found then return mid; end if;
 if p_files is null or jsonb_typeof(p_files)<>'array' then raise exception 'Choose two to four photos'; end if;
 if jsonb_array_length(p_files) not between 2 and 4 or char_length(caption)>4000 then raise exception 'Choose two to four photos and a valid caption'; end if;
 -- Validate every file before creating a message. Object ownership and MIME/size are checked under RLS.
 for item in select value from jsonb_array_elements(p_files) loop
   path:=item->>'path'; mime:=item->>'mime'; fname:=trim(item->>'name');
   if jsonb_typeof(item->'size') is distinct from 'number' or (item->>'size') !~ '^[0-9]{1,7}$' then raise exception 'Invalid photo size'; end if;
   bytes:=(item->>'size')::integer;
   if mime is null or mime not in ('image/jpeg','image/png','image/webp') or bytes not between 1 and 5242880 or fname is null or char_length(fname) not between 1 and 180 then raise exception 'Invalid photo'; end if;
   if path is null or path not like u::text||'/'||p_conversation::text||'/%' or array_length(string_to_array(path,'/'),1)<>3
      or split_part(path,'/',3) !~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\.(jpg|png|webp)$'
      or split_part(path,'.',2) is distinct from (case mime when 'image/jpeg' then 'jpg' when 'image/png' then 'png' else 'webp' end)
      or path=any(seen) then raise exception 'Invalid or duplicate photo path'; end if;
   seen:=array_append(seen,path);
   select metadata into meta from storage.objects where bucket_id='professional-message-files' and name=path and owner_id=u::text;
   if not found or meta->>'size' is null or meta->>'mimetype' is null or (meta->>'size')::bigint<>bytes or lower(split_part(meta->>'mimetype',';',1))<>mime then raise exception 'Photo upload does not match metadata'; end if;
 end loop;
 if caption='' then caption:='Attachment'; end if;
 insert into public.professional_messages(conversation_id,sender_id,body,reply_to_id,is_forwarded,client_message_id)
 values(p_conversation,u,caption,p_reply_to,false,p_client_id)
 on conflict(conversation_id,sender_id,client_message_id) do nothing returning id into mid;
 if mid is null then select id into mid from public.professional_messages where conversation_id=p_conversation and sender_id=u and client_message_id=p_client_id;return mid;end if;
 for item in select value from jsonb_array_elements(p_files) loop
   insert into public.professional_message_attachments(message_id,owner_id,storage_path,file_name,mime_type,byte_size,position)
   values(mid,u,item->>'path',trim(item->>'name'),item->>'mime',(item->>'size')::integer,idx);
   idx:=idx+1;
 end loop;
 return mid;
end; $$;
revoke all on function public.sera_send_photo_album(uuid,uuid,text,uuid,jsonb) from public,anon;
grant execute on function public.sera_send_photo_album(uuid,uuid,text,uuid,jsonb) to authenticated;
