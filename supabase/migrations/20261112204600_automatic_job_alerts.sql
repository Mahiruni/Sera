-- Explicit opt-in for automatic career alerts; existing manual refresh remains available.
create table public.sera_job_alert_preferences(
 user_id uuid primary key references auth.users(id) on delete cascade default auth.uid(),
 enabled boolean not null default false,
 updated_at timestamptz not null default now()
);
alter table public.sera_job_alert_preferences enable row level security;
revoke all on public.sera_job_alert_preferences from public,anon,authenticated;
grant select,insert,update(enabled,updated_at) on public.sera_job_alert_preferences to authenticated;
create policy job_alert_preferences_read on public.sera_job_alert_preferences for select to authenticated using(user_id=auth.uid());
create policy job_alert_preferences_insert on public.sera_job_alert_preferences for insert to authenticated with check(user_id=auth.uid() and private.account_verified());
create policy job_alert_preferences_update on public.sera_job_alert_preferences for update to authenticated using(user_id=auth.uid() and private.account_verified()) with check(user_id=auth.uid());
create table private.sera_job_alert_schedule(user_id uuid primary key references auth.users(id) on delete cascade,last_checked_at timestamptz not null);
revoke all on private.sera_job_alert_schedule from public,anon,authenticated;
create function private.sera_generate_job_alerts(u uuid) returns integer language plpgsql security definer set search_path='' as $$
declare allowed integer; inserted integer; my_skills text[]; my_roles text[];
begin
 if u is null or not exists(select 1 from auth.users where id=u and email_confirmed_at is not null) then return 0;end if;
 perform pg_advisory_xact_lock(hashtextextended(u::text,29));
 select private.sera_job_match_terms(skills),private.sera_job_match_terms(preferred_roles) into my_skills,my_roles from public.profiles where id=u;
 if coalesce(cardinality(my_skills),0)+coalesce(cardinality(my_roles),0)=0 then return 0;end if;
 select greatest(0,3-count(*))::integer into allowed from public.professional_notifications where recipient_id=u and kind='job_match' and created_at>now()-interval '1 day';
 if allowed=0 then return 0;end if;
 with candidates as (
 select j.* from public.jobs j
 where j.status='open' and j.created_at>=now()-interval '7 days' and j.employer_id<>u
 and private.sera_social_visible(u,j.employer_id)
 and not exists(select 1 from public.applications a where a.job_id=j.id and a.candidate_id=u)
 and not exists(select 1 from public.professional_notifications n where n.recipient_id=u and n.kind='job_match' and n.job_id=j.id)
 order by j.created_at desc,j.id limit 100
 ), scored as (
 select j.id,j.employer_id,j.created_at,
 array(select s from unnest(my_skills) s where private.sera_job_term_matches(concat_ws(' ',j.title,j.description,j.requirements),s)) as skills,
 array(select s from unnest(my_roles) s where private.sera_job_term_matches(j.title,s)) as roles
 from candidates j
 )
 insert into public.professional_notifications(recipient_id,actor_id,kind,job_id,match_reasons)
 select u,s.employer_id,'job_match',s.id,
 array(select distinct t from unnest(s.roles||s.skills) t order by t limit 4)
 from scored s where cardinality(s.skills)+cardinality(s.roles)>0
 order by cardinality(s.roles)*6+cardinality(s.skills)*3 desc,s.created_at desc,s.id limit allowed
 on conflict do nothing;
 get diagnostics inserted=row_count;
 return inserted;
end $$;

revoke all on function private.sera_generate_job_alerts(uuid) from public,anon,authenticated;
create or replace function public.sera_refresh_job_alerts() returns integer language plpgsql security definer set search_path='' as $$
begin
 if auth.uid() is null or not private.account_verified() then raise exception 'Verified account required';end if;
 return private.sera_generate_job_alerts(auth.uid());
end $$;
create function public.sera_run_automatic_job_alerts() returns jsonb language plpgsql security definer set search_path='' as $$
declare uid uuid; checked integer:=0; generated integer:=0;
begin
 if not pg_try_advisory_xact_lock(742391625) then return jsonb_build_object('checked',0,'generated',0,'busy',true);end if;
 for uid in
  select p.user_id from public.sera_job_alert_preferences p
  join auth.users u on u.id=p.user_id and u.email_confirmed_at is not null
  left join private.sera_job_alert_schedule s on s.user_id=p.user_id
  where p.enabled and (s.last_checked_at is null or s.last_checked_at<now()-interval '6 hours')
  order by s.last_checked_at asc nulls first,p.user_id limit 50
 loop
  -- Serialize preference changes and recheck the opt-in before generating.
  perform 1 from public.sera_job_alert_preferences where user_id=uid and enabled for share;
  if not found then continue;end if;
  generated:=generated+private.sera_generate_job_alerts(uid);
  insert into private.sera_job_alert_schedule(user_id,last_checked_at) values(uid,now()) on conflict(user_id) do update set last_checked_at=excluded.last_checked_at;
  checked:=checked+1;
 end loop;
 return jsonb_build_object('checked',checked,'generated',generated);
end $$;
revoke all on function public.sera_run_automatic_job_alerts() from public,anon,authenticated;
grant execute on function public.sera_run_automatic_job_alerts() to service_role;
-- Schedule only when this migration is deliberately applied.
do $$begin
 if exists(select 1 from pg_available_extensions where name='pg_cron') then
  execute 'create extension if not exists pg_cron with schema pg_catalog';
  execute $job$select cron.schedule('sera-automatic-job-alerts','*/15 * * * *','select public.sera_run_automatic_job_alerts();')$job$;
 end if;
end$$;
