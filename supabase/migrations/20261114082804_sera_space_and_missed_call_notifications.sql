alter table public.professional_notifications add column if not exists space_id uuid references public.sera_collaboration_spaces(id) on delete cascade,
 add column if not exists conversation_id uuid references public.professional_conversations(id) on delete cascade,
 add column if not exists source_event_id uuid;
alter table public.professional_notifications drop constraint professional_notifications_kind_check;
alter table public.professional_notifications add constraint professional_notifications_kind_check check(kind in ('like','comment','share','follow','connection_request','connection_accepted','repost','reply','job_match','space_message','missed_call'));
alter table public.professional_notifications add constraint sera_notification_activity_target check(
 (kind='space_message')=(space_id is not null) and (kind='missed_call')=(conversation_id is not null)
 and (kind in ('space_message','missed_call'))=(source_event_id is not null)
);
create unique index sera_notification_activity_once on public.professional_notifications(recipient_id,kind,source_event_id) where source_event_id is not null;
create function private.sera_notify_space_message() returns trigger language plpgsql security definer set search_path='' as $$
begin
 insert into public.professional_notifications(recipient_id,actor_id,kind,space_id,source_event_id,created_at)
 select m.member_id,new.sender_id,'space_message',new.space_id,new.id,new.created_at
 from public.sera_collaboration_members m join auth.users u on u.id=m.member_id
 where m.space_id=new.space_id and m.status='active' and m.member_id<>new.sender_id and u.email_confirmed_at is not null
 and not exists(select 1 from public.professional_blocks b where (b.blocker_id=m.member_id and b.blocked_id=new.sender_id) or (b.blocker_id=new.sender_id and b.blocked_id=m.member_id))
 on conflict do nothing;
 return new;
end; $$;
revoke all on function private.sera_notify_space_message() from public,anon,authenticated;
create trigger sera_notify_space_message after insert on public.sera_space_messages for each row execute function private.sera_notify_space_message();
create function private.sera_remove_space_notifications() returns trigger language plpgsql security definer set search_path='' as $$
begin
 if tg_op='DELETE' then
  delete from public.professional_notifications where kind='space_message' and space_id=old.space_id and recipient_id=old.member_id;
  return old;
 end if;
 if new.status<>'active' then
  delete from public.professional_notifications where kind='space_message' and space_id=old.space_id and recipient_id=old.member_id;
 end if;
 return new;
end; $$;
revoke all on function private.sera_remove_space_notifications() from public,anon,authenticated;
create trigger sera_remove_space_notifications after delete or update of status on public.sera_collaboration_members for each row execute function private.sera_remove_space_notifications();
create function private.sera_notify_missed_call() returns trigger language plpgsql security definer set search_path='' as $$
begin
 if new.state='missed' and old.state='ringing' and new.callee<>new.caller
 and not exists(select 1 from public.professional_blocks b where (b.blocker_id=new.callee and b.blocked_id=new.caller) or (b.blocker_id=new.caller and b.blocked_id=new.callee)) then
  insert into public.professional_notifications(recipient_id,actor_id,kind,conversation_id,source_event_id,created_at)
  values(new.callee,new.caller,'missed_call',new.conversation_id,new.id,coalesce(new.ended_at,now())) on conflict do nothing;
 end if;
 return new;
end; $$;
revoke all on function private.sera_notify_missed_call() from public,anon,authenticated;
create trigger sera_notify_missed_call after update of state on sera_call_private.sessions for each row execute function private.sera_notify_missed_call();
