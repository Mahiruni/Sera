-- Owner-only recruitment analytics; dates are Addis Ababa calendar days.
create function public.sera_recruitment_analytics(p_days integer default 30) returns jsonb
language sql stable security definer set search_path='' as $$
with bounds as (
 select (now() at time zone 'Africa/Addis_Ababa')::date as today,
 greatest(7,least(coalesce(p_days,30),90)) as days
), owned as (
 select id,title,status from public.jobs where employer_id=auth.uid()
), cohort as (
 select a.id,a.job_id,a.stage,a.created_at from public.applications a join owned j on j.id=a.job_id cross join bounds b
 where a.created_at >= ((b.today-b.days+1)::timestamp at time zone 'Africa/Addis_Ababa')
 and a.created_at < ((b.today+1)::timestamp at time zone 'Africa/Addis_Ababa')
), interviews as (
 select i.application_id,i.status,i.starts_at from public.sera_interviews i join cohort c on c.id=i.application_id
), daily as (
 select g.day::date as day,count(c.id) as count from bounds b
 cross join lateral generate_series((b.today-b.days+1)::timestamp,b.today::timestamp,interval '1 day') g(day)
 left join cohort c on (c.created_at at time zone 'Africa/Addis_Ababa')::date=g.day::date
 group by g.day
), job_stats as (
 select j.id,j.title,j.status,count(c.id) as applicants,
 count(c.id) filter(where c.stage='offer') as offers,
 count(c.id) filter(where exists(select 1 from interviews i where i.application_id=c.id and i.status in ('accepted','completed'))) as interviewed
 from owned j left join cohort c on c.job_id=j.id group by j.id,j.title,j.status
 order by count(c.id) desc,j.id limit 50
)
select jsonb_build_object(
 'days',(select days from bounds),'total',(select count(*) from cohort),
 'interviewed',(select count(distinct application_id) from interviews where status in ('accepted','completed')),
 'completed',(select count(distinct application_id) from interviews where status='completed'),
 'offers',(select count(*) from cohort where stage='offer'),
 'stages',coalesce((select jsonb_object_agg(stage,n) from (select stage,count(*) n from cohort group by stage)s),'{}'::jsonb),
 'daily',coalesce((select jsonb_agg(to_jsonb(d) order by d.day) from daily d),'[]'::jsonb),
 'jobs',coalesce((select jsonb_agg(to_jsonb(j) order by j.applicants desc,j.id) from job_stats j),'[]'::jsonb)
) where auth.uid() is not null;
$$;
revoke all on function public.sera_recruitment_analytics(integer) from public,anon;
grant execute on function public.sera_recruitment_analytics(integer) to authenticated;
