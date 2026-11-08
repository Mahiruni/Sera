create schema if not exists private;
revoke all on schema private from public;
-- Shared recruitment records. Self-service posting never grants a verified badge.
create or replace function private.account_verified() returns boolean language sql stable security definer set search_path='' as $$ select exists(select 1 from auth.users where id=(select auth.uid()) and email_confirmed_at is not null) $$;
revoke all on function private.account_verified() from public,anon,authenticated;
grant usage on schema private to authenticated;
grant execute on function private.account_verified() to authenticated;
create table public.jobs (
 id uuid primary key default gen_random_uuid(), employer_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
 title text not null check(char_length(title) between 3 and 160), company text not null check(char_length(company) between 2 and 160),
 location text not null check(char_length(location) between 2 and 160), work_type text not null check(work_type in ('Full-time','Part-time','Contract','Internship')),
 salary_min integer not null default 0 check(salary_min>=0), salary_max integer not null default 0 check(salary_max>=salary_min),
 description text not null check(char_length(description) between 40 and 12000), requirements text not null check(char_length(requirements) between 10 and 8000),
 status text not null default 'open' check(status in ('open','closed')), created_at timestamptz not null default now()
);
alter table public.jobs enable row level security;
grant select on public.jobs to anon,authenticated;
grant insert on public.jobs to authenticated;
grant update(title,company,location,work_type,salary_min,salary_max,description,requirements,status) on public.jobs to authenticated;
create table public.applications (
 id uuid primary key default gen_random_uuid(), job_id uuid not null references public.jobs(id), candidate_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
 candidate_name text not null check(char_length(candidate_name) between 2 and 100), skills text not null default '' check(char_length(skills)<=500), experience text not null default '' check(char_length(experience)<=500),
 cover_note text not null check(char_length(cover_note) between 20 and 4000), stage text not null default 'applied' check(stage in ('applied','screened','interview','offer','rejected','withdrawn')), created_at timestamptz not null default now(), unique(job_id,candidate_id)
);
create index applications_candidate on public.applications(candidate_id,created_at desc);
create index applications_job on public.applications(job_id);
create index jobs_owner on public.jobs(employer_id,created_at desc);
create index jobs_open on public.jobs(created_at desc) where status='open';
create or replace function private.owns_job(p_id uuid) returns boolean language sql stable security definer set search_path='' as $$ select auth.uid() is not null and exists(select 1 from public.jobs where id=p_id and employer_id=(select auth.uid())) $$;
create or replace function private.application_party(p_id uuid) returns boolean language sql stable security definer set search_path='' as $$ select auth.uid() is not null and exists(select 1 from public.applications a join public.jobs j on j.id=a.job_id where a.id=p_id and (a.candidate_id=(select auth.uid()) or j.employer_id=(select auth.uid()))) $$;
revoke all on function private.owns_job(uuid),private.application_party(uuid) from public,anon,authenticated;
grant execute on function private.owns_job(uuid),private.application_party(uuid) to authenticated;
alter table public.applications enable row level security;
grant select,insert on public.applications to authenticated;
grant update(stage) on public.applications to authenticated;
create function private.applied_to_job(p_id uuid) returns boolean language sql stable security definer set search_path='' as $$ select auth.uid() is not null and exists(select 1 from public.applications where job_id=p_id and candidate_id=(select auth.uid())) $$;
revoke all on function private.applied_to_job(uuid) from public,anon,authenticated;
grant usage on schema private to anon;
grant execute on function private.applied_to_job(uuid) to anon,authenticated;
create policy jobs_read on public.jobs for select using(status='open' or employer_id=(select auth.uid()) or private.applied_to_job(id));
create policy jobs_create on public.jobs for insert to authenticated with check(employer_id=(select auth.uid()) and private.account_verified());
create policy jobs_edit on public.jobs for update to authenticated using(employer_id=(select auth.uid()) and private.account_verified()) with check(employer_id=(select auth.uid()) and private.account_verified());
create policy applications_read on public.applications for select to authenticated using(candidate_id=(select auth.uid()) or private.owns_job(job_id));
create policy applications_create on public.applications for insert to authenticated with check(candidate_id=(select auth.uid()) and private.account_verified() and stage='applied' and exists(select 1 from public.jobs j where j.id=job_id and j.status='open' and j.employer_id<>(select auth.uid())));
create policy applications_stage on public.applications for update to authenticated using(private.account_verified() and (candidate_id=(select auth.uid()) or private.owns_job(job_id))) with check((candidate_id=(select auth.uid()) and stage='withdrawn') or (private.owns_job(job_id) and stage in ('screened','interview','offer','rejected')));
create table public.saved_jobs ( user_id uuid not null default auth.uid() references auth.users(id) on delete cascade, job_id uuid not null references public.jobs(id) on delete cascade, created_at timestamptz not null default now(), primary key(user_id,job_id));
alter table public.saved_jobs enable row level security;
grant select,insert,delete on public.saved_jobs to authenticated;
create policy saved_read on public.saved_jobs for select to authenticated using(user_id=(select auth.uid()));
create policy saved_insert on public.saved_jobs for insert to authenticated with check(user_id=(select auth.uid()) and private.account_verified() and exists(select 1 from public.jobs j where j.id=job_id and status='open'));
create policy saved_delete on public.saved_jobs for delete to authenticated using(user_id=(select auth.uid()));
create table public.application_messages (id uuid primary key default gen_random_uuid(),application_id uuid not null references public.applications(id) on delete cascade,sender_id uuid not null default auth.uid() references auth.users(id) on delete cascade,body text not null check(char_length(body) between 1 and 4000),created_at timestamptz not null default now());
create index application_messages_thread on public.application_messages(application_id,created_at);
create index application_messages_sender on public.application_messages(sender_id,created_at);
alter table public.application_messages enable row level security;
grant select,insert on public.application_messages to authenticated;
create policy messages_read on public.application_messages for select to authenticated using(private.application_party(application_id));
create policy messages_insert on public.application_messages for insert to authenticated with check(sender_id=(select auth.uid()) and private.account_verified() and private.application_party(application_id) and exists(select 1 from public.applications a where a.id=application_id and a.stage<>'withdrawn'));
create or replace function private.guard_recruitment() returns trigger language plpgsql security definer set search_path='' as $$
begin
 if auth.uid() is null then raise exception 'Verified account required'; end if;
 if tg_op='INSERT' and ((tg_table_name='jobs' and (to_jsonb(new)->>'employer_id')::uuid<>auth.uid()) or (tg_table_name='applications' and (to_jsonb(new)->>'candidate_id')::uuid<>auth.uid()) or (tg_table_name='application_messages' and (to_jsonb(new)->>'sender_id')::uuid<>auth.uid())) then raise exception 'Ownership mismatch'; end if;
 if tg_table_name='applications' and tg_op='UPDATE' then
  if old.stage='withdrawn' then raise exception 'Withdrawn applications cannot change stage'; end if;
 elsif tg_table_name='applications' and tg_op='INSERT' then
  perform 1 from public.jobs where id=new.job_id and status='open' for share;
  if not found then raise exception 'This job is closed'; end if;
  if (select count(*) from public.applications where candidate_id=new.candidate_id and created_at>now()-interval '1 day')>=50 then raise exception 'Daily application limit reached'; end if;
 elsif tg_table_name='jobs' then
  if (select count(*) from public.jobs where employer_id=new.employer_id and created_at>now()-interval '1 day')>=10 then raise exception 'Daily posting limit reached'; end if;
 elsif tg_table_name='application_messages' then
  if (select count(*) from public.application_messages where sender_id=new.sender_id and created_at>now()-interval '1 hour')>=60 then raise exception 'Message limit reached'; end if;
 end if;
 return new;
end $$;
revoke all on function private.guard_recruitment() from public,anon,authenticated;
create trigger application_guard before insert or update on public.applications for each row execute function private.guard_recruitment();
create trigger jobs_guard before insert on public.jobs for each row execute function private.guard_recruitment();
create trigger messages_guard before insert on public.application_messages for each row execute function private.guard_recruitment();
-- Server-only queue. No recipient-supplied email is ever used for welcome dispatch.
create table public.email_outbox(id uuid primary key default gen_random_uuid(),user_id uuid not null unique references auth.users(id) on delete cascade,status text not null default 'pending' check(status in ('pending','sending','sent')),attempts integer not null default 0,next_attempt_at timestamptz not null default now(),sent_at timestamptz,last_error text);
alter table public.email_outbox enable row level security;
revoke all on public.email_outbox from anon,authenticated;
grant all on public.email_outbox to service_role;
create index email_outbox_pending on public.email_outbox(next_attempt_at) where status<>'sent';
create or replace function private.enqueue_welcome() returns trigger language plpgsql security definer set search_path='' as $$ begin
 if new.email_confirmed_at is not null and old.email_confirmed_at is null then insert into public.email_outbox(user_id) values(new.id) on conflict(user_id) do nothing; end if; return new;
end $$;
revoke all on function private.enqueue_welcome() from public,anon,authenticated;
create trigger queue_verified_welcome after update of email_confirmed_at on auth.users for each row execute function private.enqueue_welcome();
create function public.claim_welcome(p_user_id uuid default null) returns setof public.email_outbox language sql security invoker set search_path='' as $$
 update public.email_outbox q set status='sending',attempts=attempts+1,next_attempt_at=now()+interval '10 minutes' where id in(select id from public.email_outbox where status<>'sent' and attempts<5 and next_attempt_at<=now() and (p_user_id is null or user_id=p_user_id) order by next_attempt_at for update skip locked limit 10) returning q.*
$$;
revoke all on function public.claim_welcome(uuid) from public,anon,authenticated;
grant execute on function public.claim_welcome(uuid) to service_role;
