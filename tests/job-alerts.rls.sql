-- Run against a migrated test database. Everything is rolled back.
begin;
create temporary table sera_job_alert_fixture(owner_id uuid,candidate_id uuid,outsider_id uuid,job_id uuid,other_job uuid,closed_job uuid,term text);
insert into sera_job_alert_fixture values(gen_random_uuid(),gen_random_uuid(),gen_random_uuid(),gen_random_uuid(),gen_random_uuid(),gen_random_uuid(),'seraqa'||replace(gen_random_uuid()::text,'-',''));
grant select on sera_job_alert_fixture to authenticated;
insert into auth.users(id,email_confirmed_at,raw_user_meta_data) select owner_id,now(),'{"full_name":"Sera QA employer"}'::jsonb from sera_job_alert_fixture union all select candidate_id,now(),'{"full_name":"Sera QA candidate"}'::jsonb from sera_job_alert_fixture union all select outsider_id,now(),'{"full_name":"Sera QA outsider"}'::jsonb from sera_job_alert_fixture;
update public.profiles p set skills=f.term,preferred_roles='' from sera_job_alert_fixture f where p.id=f.candidate_id;
do $$begin
 if not private.sera_job_term_matches('Uses C++ and SQL','c++') or private.sera_job_term_matches('JavaScript','Java') then raise exception 'Literal matching failed';end if;
end$$;
select set_config('request.jwt.claims',json_build_object('sub',(select owner_id from sera_job_alert_fixture),'role','authenticated')::text,true);
set local role authenticated;
insert into public.jobs(id,title,company,location,work_type,description,requirements) select job_id,'QA match','QA fictional organization','Test location','Contract',repeat('Fictional role description. ',4)||term,'Fictional testing requirements' from sera_job_alert_fixture union all select other_job,'QA irrelevant','QA fictional organization','Test location','Contract',repeat('Unrelated fictional role. ',4),'Fictional testing requirements' from sera_job_alert_fixture union all select closed_job,'QA closed','QA fictional organization','Test location','Contract',repeat('Fictional role description. ',4)||term,'Fictional testing requirements' from sera_job_alert_fixture;
update public.jobs set status='closed' where id=(select closed_job from sera_job_alert_fixture);
reset role;
select set_config('request.jwt.claims',json_build_object('sub',(select candidate_id from sera_job_alert_fixture),'role','authenticated')::text,true);
set local role authenticated;
do $$begin
 if public.sera_refresh_job_alerts()<>1 then raise exception 'Expected exactly one relevant open job alert';end if;
 if public.sera_refresh_job_alerts()<>0 then raise exception 'Duplicate alert created';end if;
 if not exists(select 1 from public.professional_notifications where kind='job_match' and job_id=(select job_id from sera_job_alert_fixture) and match_reasons @> array[(select term from sera_job_alert_fixture)]) then raise exception 'Missing match reasons';end if;
 if exists(select 1 from public.professional_notifications where kind='job_match' and job_id in (select other_job from sera_job_alert_fixture union all select closed_job from sera_job_alert_fixture)) then raise exception 'Irrelevant or closed job alerted';end if;
end$$;
reset role;
select set_config('request.jwt.claims',json_build_object('sub',(select outsider_id from sera_job_alert_fixture),'role','authenticated')::text,true);
set local role authenticated;
do $$begin if exists(select 1 from public.professional_notifications where job_id=(select job_id from sera_job_alert_fixture)) then raise exception 'Cross-account alert leak';end if;end$$;
reset role;
select set_config('request.jwt.claims',json_build_object('sub',(select candidate_id from sera_job_alert_fixture),'role','authenticated')::text,true);
set local role authenticated;
insert into public.professional_blocks(blocker_id,blocked_id) select candidate_id,owner_id from sera_job_alert_fixture;
do $$begin if exists(select 1 from public.professional_notifications where job_id=(select job_id from sera_job_alert_fixture)) then raise exception 'Blocked employer alert leaked';end if;end$$;
reset role;
select 'PASS: job alert relevance, closure, idempotency, ownership and block isolation' as result;
rollback;
