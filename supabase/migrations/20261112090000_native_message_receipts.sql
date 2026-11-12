-- Recipient-confirmed delivery, immutable message content and server-timed typing.
alter table public.professional_messages add column if not exists delivered_at timestamptz;
drop policy if exists "Recipient marks messages read" on public.professional_messages;
create policy "Recipient marks messages read" on public.professional_messages
for update to authenticated using (
 sender_id <> (select auth.uid()) and exists(select 1 from public.professional_conversations c where c.id=conversation_id and (c.member_a=(select auth.uid()) or c.member_b=(select auth.uid())))
) with check (
 sender_id <> (select auth.uid()) and (delivered_at is not null or read_at is not null)
 and exists(select 1 from public.professional_conversations c where c.id=conversation_id and (c.member_a=(select auth.uid()) or c.member_b=(select auth.uid())))
);
create or replace function public.guard_professional_message_update() returns trigger
language plpgsql security invoker set search_path='' as $$
begin
 if new.id is distinct from old.id or new.conversation_id is distinct from old.conversation_id
 or new.sender_id is distinct from old.sender_id or new.body is distinct from old.body
 or new.created_at is distinct from old.created_at then raise exception 'Message content is immutable'; end if;
 if old.read_at is not null and new.read_at is distinct from old.read_at then raise exception 'Read receipt is immutable'; end if;
 if old.delivered_at is not null and new.delivered_at is distinct from old.delivered_at then raise exception 'Delivery receipt is immutable'; end if;
 if new.read_at is not null and old.read_at is null then new.read_at=now(); end if;
 if new.delivered_at is not null and old.delivered_at is null then new.delivered_at=now(); end if;
 if new.read_at is not null and new.delivered_at is null then new.delivered_at=new.read_at; end if;
 return new;
end; $$;
-- A sender cannot forge delivery when inserting.
drop policy if exists "Participants send as themselves" on public.professional_messages;
create policy "Participants send as themselves" on public.professional_messages for insert to authenticated with check (
 sender_id=(select auth.uid()) and read_at is null and delivered_at is null and exists(
 select 1 from public.professional_conversations c where c.id=conversation_id and (c.member_a=(select auth.uid()) or c.member_b=(select auth.uid())))
);
create or replace function public.sera_message_receipt(p_conversation uuid,p_ids uuid[],p_read boolean default false) returns void
language plpgsql security invoker set search_path='' as $$
begin
 update public.professional_messages set delivered_at=coalesce(delivered_at,now()),read_at=case when p_read then coalesce(read_at,now()) else read_at end
 where conversation_id=p_conversation and id=any(p_ids) and sender_id<>(select auth.uid())
 and (delivered_at is null or (p_read and read_at is null));
end; $$;
revoke all on function public.sera_message_receipt(uuid,uuid[],boolean) from public,anon;
grant execute on function public.sera_message_receipt(uuid,uuid[],boolean) to authenticated;
create or replace function public.sera_set_typing(p_conversation uuid,p_typing boolean) returns void
language plpgsql security invoker set search_path='' as $$
begin
 if not exists(select 1 from public.professional_conversations c where c.id=p_conversation and (c.member_a=(select auth.uid()) or c.member_b=(select auth.uid()))) then return; end if;
 if p_typing then
 insert into public.professional_typing_status(conversation_id,member_id,updated_at) values(p_conversation,(select auth.uid()),now())
 on conflict(conversation_id,member_id) do update set updated_at=now();
 else delete from public.professional_typing_status where conversation_id=p_conversation and member_id=(select auth.uid()); end if;
end; $$;
revoke all on function public.sera_set_typing(uuid,boolean) from public,anon;
grant execute on function public.sera_set_typing(uuid,boolean) to authenticated;
