create table public.sera_recruitment_notes(application_id uuid primary key references public.applications(id) on delete cascade,body text not null default '' check(char_length(body)<=4000),updated_at timestamptz not null default now());
alter table public.sera_recruitment_notes enable row level security;
revoke all on public.sera_recruitment_notes from public,anon,authenticated;
create function public.sera_candidate_review(p_application uuid) returns jsonb language sql stable security definer set search_path='' as $$
select jsonb_build_object('id',a.id,'name',a.candidate_name,'headline',a.candidate_headline,'skills',a.skills,'experience',a.experience,'education',a.education,'cover_note',a.cover_note,'stage',a.stage,'job_title',j.title,'notes',coalesce(n.body,''),'has_cv',a.cv_path is not null and a.stage<>'withdrawn') from public.applications a join public.jobs j on j.id=a.job_id left join public.sera_recruitment_notes n on n.application_id=a.id where a.id=p_application and j.employer_id=auth.uid();
$$;
create function public.sera_save_recruitment_note(p_application uuid,p_body text) returns void language plpgsql security definer set search_path='' as $$
begin
if not exists(select 1 from public.applications a join public.jobs j on j.id=a.job_id join auth.users u on u.id=j.employer_id where a.id=p_application and j.employer_id=auth.uid() and u.email_confirmed_at is not null) then raise exception 'Employer access required';end if;
insert into public.sera_recruitment_notes(application_id,body) values(p_application,trim(coalesce(p_body,''))) on conflict(application_id) do update set body=excluded.body,updated_at=now();
end;$$;
create function public.sera_review_stage(p_application uuid,p_stage text,p_expected text) returns void language plpgsql security invoker set search_path='' as $$
begin
if p_stage not in ('screened','interview','offer','rejected') then raise exception 'Invalid stage';end if;
update public.applications a set stage=p_stage where a.id=p_application and a.stage=p_expected and a.stage<>'withdrawn' and exists(select 1 from public.jobs j where j.id=a.job_id and j.employer_id=auth.uid());
if not found then raise exception 'Application changed or unavailable';end if;
end;$$;
revoke all on function public.sera_candidate_review(uuid),public.sera_save_recruitment_note(uuid,text),public.sera_review_stage(uuid,text,text) from public,anon;
grant execute on function public.sera_candidate_review(uuid),public.sera_save_recruitment_note(uuid,text),public.sera_review_stage(uuid,text,text) to authenticated;
