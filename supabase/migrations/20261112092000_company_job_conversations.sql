-- Job-scoped company conversations. The existing job owner is the authorized company representative.
create table public.company_job_threads (
 id uuid primary key default gen_random_uuid(), job_id uuid not null references public.jobs(id) on delete cascade,
 candidate_id uuid not null references auth.users(id) on delete cascade, employer_id uuid not null references auth.users(id) on delete cascade,
 company_name text not null, job_title text not null, created_at timestamptz not null default now(),
 last_message_at timestamptz not null default now(), last_auto_reply_at timestamptz,
 unique(job_id,candidate_id), check(candidate_id<>employer_id)
);
create table public.company_job_messages (
 id uuid primary key default gen_random_uuid(), thread_id uuid not null references public.company_job_threads(id) on delete cascade,
 sender_id uuid references auth.users(id) on delete cascade, body text not null check(char_length(trim(body)) between 1 and 4000),
 automatic boolean not null default false, created_at timestamptz not null default now(),
 check((automatic and sender_id is null) or (not automatic and sender_id is not null))
);
create index company_job_messages_thread on public.company_job_messages(thread_id,created_at desc);
create index company_job_threads_employer on public.company_job_threads(employer_id,last_message_at desc);
create index company_job_threads_candidate on public.company_job_threads(candidate_id,last_message_at desc);
create table public.company_inbox_presence (member_id uuid primary key references auth.users(id) on delete cascade,last_seen_at timestamptz not null default now());
alter table public.company_job_threads enable row level security;
alter table public.company_job_messages enable row level security;
alter table public.company_inbox_presence enable row level security;
revoke all on public.company_job_threads,public.company_job_messages,public.company_inbox_presence from anon,authenticated;
grant select on public.company_job_threads,public.company_job_messages to authenticated;
create policy company_threads_participant on public.company_job_threads for select to authenticated using (candidate_id=(select auth.uid()) or employer_id=(select auth.uid()));
create policy company_messages_participant on public.company_job_messages for select to authenticated using (exists(select 1 from public.company_job_threads t where t.id=thread_id));
create function public.sera_start_job_chat(p_job uuid) returns uuid language plpgsql security definer set search_path='' as $$
declare j public.jobs%rowtype; result uuid; u uuid := auth.uid();
begin
 if u is null or not exists(select 1 from auth.users where id=u and email_confirmed_at is not null) then raise exception 'Verified account required'; end if;
 select * into j from public.jobs where id=p_job and status='open';
 if not found or j.employer_id=u then raise exception 'Job unavailable'; end if;
 if exists(select 1 from public.professional_blocks b where (b.blocker_id=u and b.blocked_id=j.employer_id) or (b.blocker_id=j.employer_id and b.blocked_id=u)) then raise exception 'Conversation unavailable'; end if;
 insert into public.company_job_threads(job_id,candidate_id,employer_id,company_name,job_title) values(j.id,u,j.employer_id,j.company,j.title)
 on conflict(job_id,candidate_id) do update set job_id=excluded.job_id returning id into result;
 return result;
end; $$;
create function public.sera_company_inbox_heartbeat() returns void language plpgsql security definer set search_path='' as $$
begin
 if auth.uid() is not null then insert into public.company_inbox_presence(member_id,last_seen_at) values(auth.uid(),now()) on conflict(member_id) do update set last_seen_at=now(); end if;
end; $$;
create function public.sera_send_job_message(p_thread uuid,p_body text) returns uuid language plpgsql security definer set search_path='' as $$
declare t public.company_job_threads%rowtype; u uuid:=auth.uid(); result uuid;
begin
 if u is null or not exists(select 1 from auth.users where id=u and email_confirmed_at is not null) then raise exception 'Verified account required'; end if;
 select * into t from public.company_job_threads where id=p_thread and (candidate_id=u or employer_id=u) for update;
 if not found then raise exception 'Conversation unavailable'; end if;
 if exists(select 1 from public.professional_blocks b where (b.blocker_id=t.candidate_id and b.blocked_id=t.employer_id) or (b.blocker_id=t.employer_id and b.blocked_id=t.candidate_id)) then raise exception 'Conversation unavailable'; end if;
 if p_body is null or char_length(trim(p_body)) not between 1 and 4000 then raise exception 'Invalid message'; end if;
 if (select count(*) from public.company_job_messages where sender_id=u and created_at>now()-interval '1 hour')>=60 then raise exception 'Message limit reached'; end if;
 insert into public.company_job_messages(thread_id,sender_id,body) values(t.id,u,trim(p_body)) returning id into result;
 update public.company_job_threads set last_message_at=now() where id=t.id;
 if u=t.candidate_id and (t.last_auto_reply_at is null or t.last_auto_reply_at<now()-interval '12 hours')
 and not exists(select 1 from public.company_inbox_presence p where p.member_id=t.employer_id and p.last_seen_at>now()-interval '75 seconds') then
 insert into public.company_job_messages(thread_id,body,automatic) values(t.id,'Thank you for contacting '||t.company_name||'. Your message has been received. Our recruitment team will get back to you as soon as possible.',true);
 update public.company_job_threads set last_auto_reply_at=now() where id=t.id;
 end if;
 return result;
end; $$;
revoke all on function public.sera_start_job_chat(uuid),public.sera_company_inbox_heartbeat(),public.sera_send_job_message(uuid,text) from public,anon;
grant execute on function public.sera_start_job_chat(uuid),public.sera_company_inbox_heartbeat(),public.sera_send_job_message(uuid,text) to authenticated;
