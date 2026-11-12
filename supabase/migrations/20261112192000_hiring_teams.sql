create table public.sera_hiring_members(job_id uuid not null references public.jobs(id) on delete cascade,member_id uuid not null references auth.users(id) on delete cascade,role text not null check(role in ('manager','interviewer')),status text not null default 'invited' check(status in ('invited','active')),created_at timestamptz not null default now(),primary key(job_id,member_id));
create table public.sera_hiring_feedback(application_id uuid not null references public.applications(id) on delete cascade,reviewer_id uuid not null references auth.users(id) on delete cascade,feedback text not null check(char_length(trim(feedback)) between 1 and 4000),updated_at timestamptz not null default now(),primary key(application_id,reviewer_id));
alter table public.sera_hiring_members enable row level security;alter table public.sera_hiring_feedback enable row level security;
revoke all on public.sera_hiring_members,public.sera_hiring_feedback from public,anon,authenticated;
create function public.sera_hiring_invite(p_job uuid,p_member uuid,p_role text) returns void language plpgsql security definer set search_path='' as $$
declare u uuid:=auth.uid();
begin
if u is null or u=p_member or not exists(select 1 from public.jobs where id=p_job and employer_id=u for update) then raise exception 'Job owner required';end if;
if not exists(select 1 from public.professional_connection_requests r where r.status='accepted' and ((r.requester_id=u and r.recipient_id=p_member) or (r.recipient_id=u and r.requester_id=p_member))) or exists(select 1 from public.professional_blocks b where (b.blocker_id=u and b.blocked_id=p_member) or (b.blocker_id=p_member and b.blocked_id=u)) then raise exception 'Accepted connection required';end if;
if (select count(*) from public.sera_hiring_members where job_id=p_job)>=20 then raise exception 'Team limit';end if;
insert into public.sera_hiring_members(job_id,member_id,role) values(p_job,p_member,p_role) on conflict do nothing;
end;$$;
create function public.sera_hiring_respond(p_job uuid,p_accept boolean) returns void language plpgsql security definer set search_path='' as $$
declare u uuid:=auth.uid();owner uuid;
begin
select employer_id into owner from public.jobs where id=p_job;
if u is null or owner is null then raise exception 'Invitation unavailable';end if;
if p_accept then
if not exists(select 1 from auth.users where id=u and email_confirmed_at is not null) or exists(select 1 from public.professional_blocks b where (b.blocker_id=u and b.blocked_id=owner) or (b.blocker_id=owner and b.blocked_id=u)) then raise exception 'Invitation unavailable';end if;
update public.sera_hiring_members set status='active' where job_id=p_job and member_id=u and status='invited';
else delete from public.sera_hiring_members where job_id=p_job and member_id=u;end if;
end;$$;
create function public.sera_hiring_remove(p_job uuid,p_member uuid) returns void language plpgsql security definer set search_path='' as $$
begin
if not exists(select 1 from public.jobs where id=p_job and employer_id=auth.uid()) then raise exception 'Owner required';end if;
delete from public.sera_hiring_members where job_id=p_job and member_id=p_member;
end;$$;
create function public.sera_hiring_list() returns table(job_id uuid,job_title text,owner_id uuid,member_id uuid,full_name text,role text,status text) language sql stable security definer set search_path='' as $$
select j.id,j.title,j.employer_id,m.member_id,coalesce(p.full_name,'SERA member'),m.role,m.status from public.jobs j join public.sera_hiring_members m on m.job_id=j.id left join public.profiles p on p.id=m.member_id where j.employer_id=auth.uid() or m.member_id=auth.uid() order by m.created_at desc limit 200;
$$;
create function public.sera_hiring_candidates(p_job uuid) returns table(id uuid,candidate_name text,headline text,skills text,stage text,feedback text) language sql stable security definer set search_path='' as $$
select a.id,a.candidate_name,a.candidate_headline,a.skills,a.stage,coalesce(f.feedback,'') from public.applications a join public.jobs j on j.id=a.job_id left join public.sera_hiring_feedback f on f.application_id=a.id and f.reviewer_id=auth.uid() where a.job_id=p_job and a.stage<>'withdrawn' and (j.employer_id=auth.uid() or exists(select 1 from public.sera_hiring_members m where m.job_id=p_job and m.member_id=auth.uid() and m.status='active' and (m.role='manager' or a.stage='interview'))) and not exists(select 1 from public.professional_blocks b where (b.blocker_id=auth.uid() and b.blocked_id=a.candidate_id) or (b.blocker_id=a.candidate_id and b.blocked_id=auth.uid()) or (b.blocker_id=auth.uid() and b.blocked_id=j.employer_id) or (b.blocker_id=j.employer_id and b.blocked_id=auth.uid())) order by a.created_at desc limit 100;
$$;
create function public.sera_hiring_submit_feedback(p_application uuid,p_feedback text) returns void language plpgsql security definer set search_path='' as $$
declare j uuid; u uuid:=auth.uid();o uuid;c uuid;s text;
begin
select a.job_id,a.candidate_id,a.stage,k.employer_id into j,c,s,o from public.applications a join public.jobs k on k.id=a.job_id where a.id=p_application for update of a;
if u is null or j is null or s='withdrawn' or not exists(select 1 from public.sera_hiring_members m where m.job_id=j and m.member_id=u and m.status='active' and (m.role='manager' or s='interview') for update) or exists(select 1 from public.professional_blocks b where (b.blocker_id=u and b.blocked_id in(c,o)) or (b.blocked_id=u and b.blocker_id in(c,o))) then raise exception 'Review unavailable';end if;
insert into public.sera_hiring_feedback(application_id,reviewer_id,feedback) values(p_application,u,trim(p_feedback)) on conflict(application_id,reviewer_id) do update set feedback=excluded.feedback,updated_at=now();
end;$$;
create function public.sera_hiring_feedback_list(p_application uuid) returns table(reviewer_id uuid,full_name text,feedback text,updated_at timestamptz) language sql stable security definer set search_path='' as $$
select f.reviewer_id,coalesce(p.full_name,'SERA member'),f.feedback,f.updated_at from public.sera_hiring_feedback f join public.applications a on a.id=f.application_id join public.jobs j on j.id=a.job_id left join public.profiles p on p.id=f.reviewer_id where f.application_id=p_application and j.employer_id=auth.uid() order by f.updated_at desc limit 100;
$$;
revoke all on function public.sera_hiring_invite(uuid,uuid,text),public.sera_hiring_respond(uuid,boolean),public.sera_hiring_remove(uuid,uuid),public.sera_hiring_list(),public.sera_hiring_candidates(uuid),public.sera_hiring_submit_feedback(uuid,text),public.sera_hiring_feedback_list(uuid) from public,anon;
grant execute on function public.sera_hiring_invite(uuid,uuid,text),public.sera_hiring_respond(uuid,boolean),public.sera_hiring_remove(uuid,uuid),public.sera_hiring_list(),public.sera_hiring_candidates(uuid),public.sera_hiring_submit_feedback(uuid,text),public.sera_hiring_feedback_list(uuid) to authenticated;