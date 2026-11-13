-- Service-only durable per-device notification jobs. No message content or device tokens stored.
create table public.sera_message_push_jobs(
 event_id uuid not null,
 kind text not null check(kind in ('message','company_message')),
 recipient_id uuid not null references auth.users(id) on delete cascade,
 conversation_id uuid not null,
 device_id uuid not null references public.professional_push_devices(id) on delete cascade,
 state text not null default 'pending' check(state in ('pending','leased','accepted','skipped','expired','failed')),
 attempts integer not null default 0 check(attempts between 0 and 6),
 next_attempt_at timestamptz not null default now(),
 lease_id uuid,
 lease_until timestamptz,
 created_at timestamptz not null default now(),
 expires_at timestamptz not null default now()+interval '30 minutes',
 primary key(event_id,kind,device_id)
);
alter table public.sera_message_push_jobs enable row level security;
revoke all on public.sera_message_push_jobs from public,anon,authenticated;
grant select,insert,update,delete on public.sera_message_push_jobs to service_role;
create index sera_push_jobs_due_idx on public.sera_message_push_jobs(next_attempt_at) where state in ('pending','leased');
create function public.sera_enqueue_message_push(p_event uuid,p_kind text,p_recipient uuid,p_conversation uuid) returns integer
language plpgsql security invoker set search_path='' as $$
declare n integer;
begin
 if p_kind not in ('message','company_message') then raise exception 'Invalid notification type'; end if;
 -- Old dispatchers and the new queue share the event claim, atomically.
 insert into public.sera_native_push_events(event_id,kind) values(p_event,p_kind) on conflict do nothing;
 if not found then return 0; end if;
 insert into public.sera_message_push_jobs(event_id,kind,recipient_id,conversation_id,device_id)
 select p_event,p_kind,p_recipient,p_conversation,id from public.professional_push_devices where user_id=p_recipient and enabled=true order by updated_at desc limit 100;
 get diagnostics n=row_count; return n;
end; $$;
create function public.sera_claim_message_push_jobs(p_limit integer default 8,p_event uuid default null)
returns setof public.sera_message_push_jobs language plpgsql security invoker set search_path='' as $$
begin
 update public.sera_message_push_jobs set state=case when expires_at<=now() then 'expired' else 'failed' end,lease_id=null,lease_until=null
 where state in ('pending','leased') and (state='pending' or lease_until<=now()) and (expires_at<=now() or attempts>=6);
 return query with candidates as (
 select event_id,kind,device_id from public.sera_message_push_jobs
 where expires_at>now() and attempts<6 and (p_event is null or event_id=p_event)
 and ((state='pending' and next_attempt_at<=now()) or (state='leased' and lease_until<=now()))
 order by next_attempt_at,event_id,device_id limit greatest(1,least(coalesce(p_limit,8),16)) for update skip locked
 )
 update public.sera_message_push_jobs j set state='leased',attempts=j.attempts+1,lease_id=gen_random_uuid(),lease_until=now()+interval '90 seconds'
 from candidates c where j.event_id=c.event_id and j.kind=c.kind and j.device_id=c.device_id returning j.*;
end; $$;
create function public.sera_finish_message_push(p_event uuid,p_kind text,p_device uuid,p_lease uuid,p_result text) returns boolean
language plpgsql security invoker set search_path='' as $$
begin
 if p_result not in ('accepted','skipped','retry') then raise exception 'Invalid result'; end if;
 update public.sera_message_push_jobs set
 state=case when p_result<>'retry' then p_result when expires_at<=now() then 'expired' when attempts>=6 then 'failed' else 'pending' end,
 next_attempt_at=now()+make_interval(secs=>least(900,15*power(2,attempts-1)::integer)),
 lease_id=null,lease_until=null
 where event_id=p_event and kind=p_kind and device_id=p_device and lease_id=p_lease and state='leased';
 return found;
end; $$;
revoke all on function public.sera_enqueue_message_push(uuid,text,uuid,uuid),public.sera_claim_message_push_jobs(integer,uuid),public.sera_finish_message_push(uuid,text,uuid,uuid,text) from public,anon,authenticated;
grant execute on function public.sera_enqueue_message_push(uuid,text,uuid,uuid),public.sera_claim_message_push_jobs(integer,uuid),public.sera_finish_message_push(uuid,text,uuid,uuid,text) to service_role;
