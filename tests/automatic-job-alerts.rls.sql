-- Automatic job-alert regression. Run after both migrations in a disposable transaction.
begin;
create temporary table auto_fixture(a uuid,b uuid,c uuid,job uuid,term text);
insert into auto_fixture values(gen_random_uuid(),gen_random_uuid(),gen_random_uuid(),gen_random_uuid(),'seraqa'||replace(gen_random_uuid()::text,'-',''));
grant select on auto_fixture to authenticated,service_role;
insert into auth.users(id,email_confirmed_at,raw_user_meta_data) select a,now(),'{"full_name":"QA employer"}'::jsonb from auto_fixture union all select b,now(),'{"full_name":"QA opted in"}'::jsonb from auto_fixture union all select c,now(),'{"full_name":"QA opted out"}'::jsonb from auto_fixture;
update public.profiles p set skills=f.term,preferred_roles='' from auto_fixture f where p.id in(f.b,f.c);
select set_config('request.jwt.claims',json_build_object('sub',(select a from auto_fixture),'role','authenticated')::text,true);
set local role authenticated;
insert into public.jobs(id,title,company,location,work_type,description,requirements) select job,'QA opportunity','QA company','Test location','Contract',repeat('Fictional role description. ',4)||term,'Fictional requirements' from auto_fixture;
reset role;
select set_config('request.jwt.claims',json_build_object('sub',(select b from auto_fixture),'role','authenticated')::text,true);
set local role authenticated;
insert into public.sera_job_alert_preferences(user_id,enabled) select b,true from auto_fixture;
do $$begin
 if has_function_privilege('authenticated','public.sera_run_automatic_job_alerts()','execute') then raise exception 'Scheduler exposed';end if;
 begin insert into public.sera_job_alert_preferences(user_id,enabled) select c,true from auto_fixture;raise exception 'Cross-account preference write';exception when insufficient_privilege then null;end;
end$$;
reset role;
-- Isolate the scheduler batch from existing users during this rolled-back test.
update public.sera_job_alert_preferences set enabled=false where user_id<>(select b from auto_fixture);
set local role service_role;
select public.sera_run_automatic_job_alerts();
select public.sera_run_automatic_job_alerts();
reset role;
do $$begin
 if (select count(*) from public.professional_notifications where job_id=(select job from auto_fixture) and recipient_id=(select b from auto_fixture))<>1 then raise exception 'Automatic opted-in alert missing or duplicated';end if;
 if exists(select 1 from public.professional_notifications where job_id=(select job from auto_fixture) and recipient_id=(select c from auto_fixture)) then raise exception 'Opted-out member notified';end if;
end$$;
select 'PASS: opt-in, scheduler isolation, automatic deduplication and preference ownership' as result;
rollback;
