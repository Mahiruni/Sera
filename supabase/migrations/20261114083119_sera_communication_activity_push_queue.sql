alter table public.sera_message_push_jobs drop constraint sera_message_push_jobs_kind_check;
alter table public.sera_message_push_jobs add constraint sera_message_push_jobs_kind_check check(kind in ('message','company_message','space_message','missed_call'));
create table private.sera_activity_push_rollout(singleton boolean primary key default true check(singleton),enabled boolean not null default false);
insert into private.sera_activity_push_rollout(singleton,enabled) values(true,false);
revoke all on private.sera_activity_push_rollout from public,anon,authenticated;
create function private.sera_enqueue_communication_activity() returns trigger language plpgsql security definer set search_path='' as $$
begin
 if new.kind not in ('space_message','missed_call') or not exists(select 1 from private.sera_activity_push_rollout where singleton and enabled) then return new;end if;
 insert into public.sera_message_push_jobs(event_id,kind,recipient_id,conversation_id,device_id)
 select new.id,new.kind,new.recipient_id,coalesce(new.space_id,new.conversation_id),d.id
 from public.professional_push_devices d where d.user_id=new.recipient_id and d.enabled order by d.updated_at desc limit 100
 on conflict do nothing;
 return new;
end; $$;
revoke all on function private.sera_enqueue_communication_activity() from public,anon,authenticated;
create trigger sera_enqueue_communication_activity after insert on public.professional_notifications for each row execute function private.sera_enqueue_communication_activity();
