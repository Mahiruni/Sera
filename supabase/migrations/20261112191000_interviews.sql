create table public.sera_interviews(id uuid primary key default gen_random_uuid(),application_id uuid not null references public.applications(id) on delete cascade,starts_at timestamptz not null,duration_minutes integer not null check(duration_minutes between 15 and 180),location text not null check(char_length(trim(location)) between 2 and 300),status text not null default 'pending' check(status in ('pending','accepted','declined','cancelled','completed')),created_at timestamptz not null default now());
create table public.sera_interview_evaluations(interview_id uuid primary key references public.sera_interviews(id) on delete cascade,score integer not null check(score between 1 and 5),feedback text not null check(char_length(feedback)<=4000),updated_at timestamptz not null default now());
alter table public.sera_interviews enable row level security;alter table public.sera_interview_evaluations enable row level security;
revoke all on public.sera_interviews,public.sera_interview_evaluations from public,anon,authenticated;
create function public.sera_interviews_list(p_application uuid) returns table(id uuid,starts_at timestamptz,duration_minutes integer,location text,status text,score integer,feedback text) language sql stable security definer set search_path='' as $$
select i.id,i.starts_at,i.duration_minutes,i.location,i.status,case when j.employer_id=auth.uid() then e.score end,case when j.employer_id=auth.uid() then e.feedback end from public.sera_interviews i join public.applications a on a.id=i.application_id join public.jobs j on j.id=a.job_id left join public.sera_interview_evaluations e on e.interview_id=i.id where i.application_id=p_application and (a.candidate_id=auth.uid() or j.employer_id=auth.uid()) order by i.starts_at desc limit 50;
$$;
create function public.sera_schedule_interview(p_application uuid,p_starts timestamptz,p_duration integer,p_location text) returns uuid language plpgsql security definer set search_path='' as $$
declare v uuid;
begin
if not exists(select 1 from public.applications a join public.jobs j on j.id=a.job_id join auth.users u on u.id=j.employer_id where a.id=p_application and j.employer_id=auth.uid() and u.email_confirmed_at is not null and a.stage not in ('withdrawn','rejected') for update of a) then raise exception 'Application unavailable';end if;
if p_starts is null or p_starts<=now() or p_starts>now()+interval '1 year' then raise exception 'Choose a future date';end if;
if (select count(*) from public.sera_interviews where application_id=p_application)>=50 then raise exception 'Interview limit';end if;
if exists(select 1 from public.sera_interviews where application_id=p_application and status in ('pending','accepted') and starts_at < p_starts+make_interval(mins=>p_duration) and starts_at+make_interval(mins=>duration_minutes)>p_starts) then raise exception 'Overlapping interview';end if;
insert into public.sera_interviews(application_id,starts_at,duration_minutes,location) values(p_application,p_starts,p_duration,trim(p_location)) returning id into v;return v;
end;$$;
create function public.sera_interview_status(p_interview uuid,p_status text) returns void language plpgsql security definer set search_path='' as $$
declare i public.sera_interviews%rowtype; candidate uuid; employer uuid; appstage text;
begin
select * into i from public.sera_interviews where id=p_interview for update;
if not found then raise exception 'Interview unavailable';end if;
select a.candidate_id,j.employer_id,a.stage into candidate,employer,appstage from public.applications a join public.jobs j on j.id=a.job_id where a.id=i.application_id;
if auth.uid() is null or appstage='withdrawn' then raise exception 'Interview unavailable';end if;
if not ((auth.uid()=candidate and i.status='pending' and p_status in ('accepted','declined') and i.starts_at>now()) or (auth.uid()=employer and i.status in ('pending','accepted') and p_status='cancelled') or (auth.uid()=employer and i.status='accepted' and p_status='completed' and i.starts_at<=now())) then raise exception 'Action unavailable';end if;
update public.sera_interviews set status=p_status where id=p_interview;
end;$$;
create function public.sera_evaluate_interview(p_interview uuid,p_score integer,p_feedback text) returns void language plpgsql security definer set search_path='' as $$
begin
if not exists(select 1 from public.sera_interviews i join public.applications a on a.id=i.application_id join public.jobs j on j.id=a.job_id where i.id=p_interview and i.status='completed' and j.employer_id=auth.uid()) then raise exception 'Completed interview required';end if;
insert into public.sera_interview_evaluations(interview_id,score,feedback) values(p_interview,p_score,trim(coalesce(p_feedback,''))) on conflict(interview_id) do update set score=excluded.score,feedback=excluded.feedback,updated_at=now();
end;$$;
revoke all on function public.sera_interviews_list(uuid),public.sera_schedule_interview(uuid,timestamptz,integer,text),public.sera_interview_status(uuid,text),public.sera_evaluate_interview(uuid,integer,text) from public,anon;
grant execute on function public.sera_interviews_list(uuid),public.sera_schedule_interview(uuid,timestamptz,integer,text),public.sera_interview_status(uuid,text),public.sera_evaluate_interview(uuid,integer,text) to authenticated;