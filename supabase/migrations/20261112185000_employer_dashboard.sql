create function public.sera_employer_dashboard() returns jsonb language sql stable security definer set search_path='' as $$
select jsonb_build_object(
'open_jobs',(select count(*) from public.jobs j where j.employer_id=auth.uid() and j.status='open'),
'closed_jobs',(select count(*) from public.jobs j where j.employer_id=auth.uid() and j.status='closed'),
'stages',coalesce((select jsonb_object_agg(x.stage,x.n) from (select a.stage,count(*) n from public.applications a join public.jobs j on j.id=a.job_id where j.employer_id=auth.uid() group by a.stage)x),'{}'::jsonb),
'jobs',coalesce((select jsonb_agg(to_jsonb(x) order by x.created_at desc) from (select j.id,j.title,j.company,j.status,j.created_at,(select count(*) from public.applications a where a.job_id=j.id) as applicants from public.jobs j where j.employer_id=auth.uid() order by j.created_at desc limit 50)x),'[]'::jsonb),
'applications',coalesce((select jsonb_agg(to_jsonb(x) order by x.created_at desc) from (select a.id,a.candidate_name,a.stage,a.created_at,j.title as job_title from public.applications a join public.jobs j on j.id=a.job_id where j.employer_id=auth.uid() order by a.created_at desc limit 50)x),'[]'::jsonb)
) where auth.uid() is not null;
$$;
revoke all on function public.sera_employer_dashboard() from public,anon;
grant execute on function public.sera_employer_dashboard() to authenticated;
