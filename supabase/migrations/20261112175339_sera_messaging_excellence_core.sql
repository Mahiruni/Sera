-- Milestone A: private replies, reactions, forwarding, unsend, voice and idempotent sending.
-- Backwards compatible with the previously released web and Android clients.
alter table public.professional_messages
  add column if not exists reply_to_id uuid,
  add column if not exists is_forwarded boolean not null default false,
  add column if not exists unsent_at timestamptz,
  add column if not exists client_message_id uuid;
create unique index if not exists professional_messages_client_id_idx
  on public.professional_messages(conversation_id,sender_id,client_message_id);
alter table public.professional_message_attachments add column if not exists duration_ms integer;
alter table public.professional_message_attachments
  drop constraint if exists professional_message_attachments_mime_type_check;
alter table public.professional_message_attachments
  add constraint professional_message_attachments_mime_type_check
  check (mime_type in ('image/jpeg','image/png','image/webp','application/pdf','audio/mp4','audio/webm','audio/ogg','audio/mpeg'));
alter table public.professional_message_attachments
  add constraint professional_message_attachments_duration_check
  check (duration_ms is null or (mime_type like 'audio/%' and duration_ms between 1 and 120000));
update storage.buckets set allowed_mime_types=array['image/jpeg','image/png','image/webp','application/pdf','audio/mp4','audio/webm','audio/ogg','audio/mpeg']
  where id='professional-message-files';

-- Reply identifiers never cross a conversation; dangling replies after account deletion
-- deliberately remain harmless identifiers instead of blocking account erasure.
create or replace function public.guard_professional_message_insert() returns trigger
language plpgsql security invoker set search_path='' as $$
begin
 if new.unsent_at is not null then raise exception 'Cannot send a removed message'; end if;
 if new.reply_to_id is not null and not exists (
  select 1 from public.professional_messages m where m.id=new.reply_to_id
   and m.conversation_id=new.conversation_id and m.unsent_at is null
 ) then raise exception 'Reply target is unavailable'; end if;
 return new;
end; $$;
drop trigger if exists guard_professional_message_before_insert on public.professional_messages;
create trigger guard_professional_message_before_insert before insert on public.professional_messages
 for each row execute function public.guard_professional_message_insert();

-- Only the original sender can redact content. Receipt transitions remain
-- recipient-only, immutable and server-timed. A tombstone is irreversible.
create or replace function public.guard_professional_message_update() returns trigger
language plpgsql security invoker set search_path='' as $$
begin
 if new.id is distinct from old.id or new.conversation_id is distinct from old.conversation_id
 or new.sender_id is distinct from old.sender_id or new.created_at is distinct from old.created_at
 or new.reply_to_id is distinct from old.reply_to_id or new.is_forwarded is distinct from old.is_forwarded
 or new.client_message_id is distinct from old.client_message_id then
  raise exception 'Message identity is immutable';
 end if;
 if old.unsent_at is null and new.unsent_at is not null then
  if (select auth.uid()) is distinct from old.sender_id
    or new.read_at is distinct from old.read_at or new.delivered_at is distinct from old.delivered_at then
   raise exception 'Only the sender can unsend a message';
  end if;
  new.body='Message unsent'; new.unsent_at=now(); return new;
 end if;
 if new.unsent_at is distinct from old.unsent_at or new.body is distinct from old.body then
  raise exception 'Message content is immutable';
 end if;
 if (select auth.uid())=old.sender_id and
  (new.read_at is distinct from old.read_at or new.delivered_at is distinct from old.delivered_at) then
  raise exception 'Senders cannot forge receipts';
 end if;
 if old.read_at is not null and new.read_at is distinct from old.read_at then raise exception 'Read receipt is immutable'; end if;
 if old.delivered_at is not null and new.delivered_at is distinct from old.delivered_at then raise exception 'Delivery receipt is immutable'; end if;
 if new.read_at is not null and old.read_at is null then new.read_at=now(); end if;
 if new.delivered_at is not null and old.delivered_at is null then new.delivered_at=now(); end if;
 if new.read_at is not null and new.delivered_at is null then new.delivered_at=new.read_at; end if;
 return new;
end; $$;

drop policy if exists "Sender can unsend own message" on public.professional_messages;
create policy "Sender can unsend own message" on public.professional_messages for update to authenticated
 using (sender_id=(select auth.uid()) and exists(select 1 from public.professional_conversations c where c.id=conversation_id))
 with check (sender_id=(select auth.uid()) and unsent_at is not null and exists(select 1 from public.professional_conversations c where c.id=conversation_id));

drop policy if exists "Participants read attachment metadata" on public.professional_message_attachments;
create policy "Participants read attachment metadata" on public.professional_message_attachments for select to authenticated
 using (exists(select 1 from public.professional_messages m where m.id=message_id and m.unsent_at is null));
drop policy if exists "Sender registers own attachment" on public.professional_message_attachments;
create policy "Sender registers own attachment" on public.professional_message_attachments for insert to authenticated
 with check (owner_id=(select auth.uid()) and exists(
  select 1 from public.professional_messages m where m.id=message_id and m.sender_id=(select auth.uid())
  and m.unsent_at is null and storage_path like (select auth.uid())::text || '/' || m.conversation_id::text || '/%'));

revoke truncate, references, trigger, delete on public.professional_messages from authenticated,anon;
revoke truncate, references, trigger, update, delete on public.professional_message_attachments from authenticated,anon;

create table if not exists public.professional_message_reactions (
 message_id uuid not null references public.professional_messages(id) on delete cascade,
 conversation_id uuid not null references public.professional_conversations(id) on delete cascade,
 member_id uuid not null references auth.users(id) on delete cascade,
 emoji text check (emoji is null or emoji in ('👍','❤️','😂','😮','😢','👏')),
 updated_at timestamptz not null default now(),
 primary key(message_id,member_id)
);
create index if not exists professional_message_reactions_conversation_idx on public.professional_message_reactions(conversation_id);
create index if not exists professional_message_reactions_member_idx on public.professional_message_reactions(member_id);
alter table public.professional_message_reactions enable row level security;
revoke all on public.professional_message_reactions from public,anon,authenticated;
grant select,insert,update on public.professional_message_reactions to authenticated;
create policy "Participants read message reactions" on public.professional_message_reactions for select to authenticated
 using (exists(select 1 from public.professional_messages m where m.id=message_id and m.conversation_id=professional_message_reactions.conversation_id and m.unsent_at is null));
create policy "Participants add own message reaction" on public.professional_message_reactions for insert to authenticated
 with check (member_id=(select auth.uid()) and exists(select 1 from public.professional_messages m where m.id=message_id and m.conversation_id=professional_message_reactions.conversation_id and m.unsent_at is null));
create policy "Participants change own message reaction" on public.professional_message_reactions for update to authenticated
 using (member_id=(select auth.uid()) and exists(select 1 from public.professional_messages m where m.id=message_id and m.conversation_id=professional_message_reactions.conversation_id and m.unsent_at is null))
 with check (member_id=(select auth.uid()) and exists(select 1 from public.professional_messages m where m.id=message_id and m.conversation_id=professional_message_reactions.conversation_id and m.unsent_at is null));

-- Use a nullable reaction for removal, keeping realtime changes participant scoped.
create or replace function public.sera_set_message_reaction(p_message uuid,p_emoji text) returns void
language plpgsql security invoker set search_path='' as $$
declare v_conversation uuid;
begin
 if (select auth.uid()) is null then raise exception 'Authentication required'; end if;
 select conversation_id into v_conversation from public.professional_messages where id=p_message and unsent_at is null;
 if not found then raise exception 'Message unavailable'; end if;
 insert into public.professional_message_reactions(message_id,conversation_id,member_id,emoji,updated_at)
 values(p_message,v_conversation,(select auth.uid()),p_emoji,now())
 on conflict(message_id,member_id) do update set emoji=excluded.emoji,updated_at=now();
end; $$;
revoke all on function public.sera_set_message_reaction(uuid,text) from public,anon;
grant execute on function public.sera_set_message_reaction(uuid,text) to authenticated;

-- Return the sender's private file paths for immediate Storage API cleanup.
-- Attachment metadata becomes invisible to participants as soon as redaction commits.
create or replace function public.sera_unsend_message(p_message uuid) returns text[]
language plpgsql security invoker set search_path='' as $$
declare v_paths text[];
begin
 if (select auth.uid()) is null then raise exception 'Authentication required'; end if;
 if not exists(select 1 from public.professional_messages where id=p_message and sender_id=(select auth.uid())) then
  raise exception 'Only the original sender may unsend';
 end if;
 select coalesce(array_agg(storage_path),array[]::text[]) into v_paths from public.professional_message_attachments where message_id=p_message;
 update public.professional_messages set unsent_at=now(),body='Message unsent'
  where id=p_message and sender_id=(select auth.uid()) and unsent_at is null;
 return v_paths;
end; $$;
revoke all on function public.sera_unsend_message(uuid) from public,anon;
grant execute on function public.sera_unsend_message(uuid) to authenticated;

-- One transaction creates message and attachment; UUID supplied by client makes retries idempotent.
-- Forwarded messages are independent copies. Source IDs never leave their original conversation.
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
  if p_mime is null or p_mime not in ('image/jpeg','image/png','image/webp','application/pdf','audio/mp4','audio/webm','audio/ogg','audio/mpeg')
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

-- Reactions arrive in the same authorized realtime thread as message changes.
do $$ begin
 if exists(select 1 from pg_publication where pubname='supabase_realtime') and not exists(select 1 from pg_publication_tables where pubname='supabase_realtime' and schemaname='public' and tablename='professional_message_reactions') then
  alter publication supabase_realtime add table public.professional_message_reactions;
 end if;
end; $$;
