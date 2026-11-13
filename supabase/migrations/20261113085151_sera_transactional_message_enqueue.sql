-- Stage transactional personal-message enqueue behind an explicit rollout switch.
-- Keep disabled until the recovery worker is confirmed live.
create table private.sera_message_push_rollout(singleton boolean primary key default true check(singleton),enabled boolean not null default false);
insert into private.sera_message_push_rollout(singleton,enabled) values(true,false);
revoke all on private.sera_message_push_rollout from public,anon,authenticated;
create function private.sera_enqueue_message_from_insert() returns trigger language plpgsql security definer set search_path='' as $$
declare recipient uuid;
begin
 if not exists(select 1 from private.sera_message_push_rollout where singleton and enabled) then return new; end if;
 select case when c.member_a=new.sender_id then c.member_b when c.member_b=new.sender_id then c.member_a else null end
 into recipient from public.professional_conversations c where c.id=new.conversation_id;
 if recipient is null then return new; end if;
 if exists(select 1 from public.professional_blocks b where (b.blocker_id=new.sender_id and b.blocked_id=recipient) or (b.blocker_id=recipient and b.blocked_id=new.sender_id)) then return new; end if;
 perform public.sera_enqueue_message_push(new.id,'message',recipient,new.conversation_id);
 return new;
end; $$;
revoke all on function private.sera_enqueue_message_from_insert() from public,anon,authenticated;
create trigger sera_enqueue_message_after_insert after insert on public.professional_messages for each row execute function private.sera_enqueue_message_from_insert();
