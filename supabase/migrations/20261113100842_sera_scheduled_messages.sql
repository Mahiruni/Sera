create table public.sera_scheduled_messages(
 id uuid primary key, user_id uuid not null references auth.users(id) on delete cascade,
 conversation_id uuid not null references public.professional_conversations(id) on delete cascade,
 body text not null check(char_length(body) between 1 and 4000),
 send_at timestamptz not null, created_at timestamptz not null default now(),
 updated_at timestamptz not null default now(),
 state text not null default 'scheduled' check(state in('scheduled','sent','cancelled','failed')),
 message_id uuid references public.professional_messages(id) on delete set null,
 failure_reason text, attempts integer not null default 0 check(attempts between 0 and 5)
);
alter table public.sera_scheduled_messages enable row level security;
revoke all on public.sera_scheduled_messages from public,anon,authenticated;
grant select on public.sera_scheduled_messages to authenticated;
create policy "Owner reads scheduled messages" on public.sera_scheduled_messages for select to authenticated using(user_id=(select auth.uid()));
create index sera_scheduled_due on public.sera_scheduled_messages(send_at,id) where state='scheduled';
create index sera_scheduled_owner on public.sera_scheduled_messages(user_id,send_at desc);
create function public.sera_save_scheduled_message(p_id uuid,p_conversation uuid,p_body text,p_send_at timestamptz) returns uuid
language plpgsql security definer set search_path='' as $$
declare u uuid:=(select auth.uid()); result uuid; peer uuid;
begin
 if u is null or p_id is null or p_body is null or p_send_at is null or char_length(btrim(p_body)) not between 1 and 4000 or p_send_at<now()+interval '1 minute' or p_send_at>now()+interval '90 days' then raise exception 'Invalid scheduled message'; end if;
 select case when c.member_a=u then c.member_b when c.member_b=u then c.member_a end into peer from public.professional_conversations c where c.id=p_conversation;
 if peer is null or not exists(select 1 from auth.users where id=u and email_confirmed_at is not null) or exists(select 1 from public.professional_blocks b where (b.blocker_id=u and b.blocked_id=peer) or (b.blocker_id=peer and b.blocked_id=u)) then raise exception 'Conversation unavailable'; end if;
 -- Serialize quota checks for the account, including parallel scheduling requests.
 perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(u::text,0));
 if exists(select 1 from public.sera_scheduled_messages where id=p_id and user_id=u and conversation_id=p_conversation and state='scheduled') then
  update public.sera_scheduled_messages set body=btrim(p_body),send_at=p_send_at,updated_at=now(),attempts=0 where id=p_id and user_id=u and conversation_id=p_conversation and state='scheduled' returning id into result;
 else
  if (select count(*) from public.sera_scheduled_messages where user_id=u and state='scheduled')>=100 then raise exception 'Scheduled message limit reached';end if;
  insert into public.sera_scheduled_messages(id,user_id,conversation_id,body,send_at) values(p_id,u,p_conversation,btrim(p_body),p_send_at) on conflict(id) do nothing returning id into result;
 end if;
 if result is null then raise exception 'Scheduled message unavailable';end if;
 return result;
end; $$;
create function public.sera_cancel_scheduled_message(p_id uuid) returns boolean language plpgsql security definer set search_path='' as $$
begin
 if (select auth.uid()) is null then raise exception 'Authentication required'; end if;
 update public.sera_scheduled_messages set state='cancelled',updated_at=now() where id=p_id and user_id=(select auth.uid()) and state='scheduled';
 return found;
end; $$;
revoke all on function public.sera_save_scheduled_message(uuid,uuid,text,timestamptz),public.sera_cancel_scheduled_message(uuid) from public,anon;
grant execute on function public.sera_save_scheduled_message(uuid,uuid,text,timestamptz),public.sera_cancel_scheduled_message(uuid) to authenticated;
-- Worker is not exposed to authenticated callers. The row lock makes cancellation,
-- editing and delivery mutually exclusive; unique client IDs prevent double sends.
create function private.sera_deliver_scheduled_messages(p_limit integer default 50) returns integer language plpgsql security definer set search_path='' as $$
declare j public.sera_scheduled_messages%rowtype; peer uuid; mid uuid; sent integer:=0;
begin
 for j in select * from public.sera_scheduled_messages where state='scheduled' and send_at<=now() order by send_at,id limit greatest(1,least(coalesce(p_limit,50),100)) for update skip locked loop
  begin
   select case when c.member_a=j.user_id then c.member_b when c.member_b=j.user_id then c.member_a end into peer from public.professional_conversations c where c.id=j.conversation_id;
   if peer is null or not exists(select 1 from auth.users where id=j.user_id and email_confirmed_at is not null) or exists(select 1 from public.professional_blocks b where (b.blocker_id=j.user_id and b.blocked_id=peer) or (b.blocker_id=peer and b.blocked_id=j.user_id)) then
    update public.sera_scheduled_messages set state='failed',failure_reason='Conversation unavailable',updated_at=now() where id=j.id;continue;
   end if;
   insert into public.professional_messages(conversation_id,sender_id,body,client_message_id)
   values(j.conversation_id,j.user_id,j.body,j.id) on conflict(conversation_id,sender_id,client_message_id) do nothing returning id into mid;
   if mid is null then select id into mid from public.professional_messages where conversation_id=j.conversation_id and sender_id=j.user_id and client_message_id=j.id;end if;
   if mid is null then raise exception 'Message not saved';end if;
   perform public.sera_enqueue_message_push(mid,'message',peer,j.conversation_id);
   update public.sera_scheduled_messages set state='sent',message_id=mid,updated_at=now(),failure_reason=null where id=j.id;
   sent=sent+1;
  exception when others then
   update public.sera_scheduled_messages set attempts=attempts+1,state=case when attempts+1>=5 then 'failed' else 'scheduled' end,
    failure_reason=case when attempts+1>=5 then 'Delivery could not complete' else null end,updated_at=now() where id=j.id;
  end;
 end loop;
 return sent;
end; $$;
revoke all on function private.sera_deliver_scheduled_messages(integer) from public,anon,authenticated;
