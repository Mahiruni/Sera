begin;
create temporary table intake_fixture(a uuid,m uuid);
insert into intake_fixture values(gen_random_uuid(),gen_random_uuid());
grant select on intake_fixture to authenticated;
insert into auth.users(id,email_confirmed_at,raw_user_meta_data,raw_app_meta_data)
 select a,now(),'{"full_name":"Intake QA Applicant"}'::jsonb,'{}'::jsonb from intake_fixture
 union all select m,now(),'{"full_name":"Intake QA Reviewer"}'::jsonb,'{}'::jsonb from intake_fixture;
update public.profiles set full_name='Intake QA Applicant' where id=(select a from intake_fixture);
-- Use a rollback-only environment, and temporarily remove existing reviewer roles to test fail-closed intake.
update auth.users set raw_app_meta_data=raw_app_meta_data-'role'-'identity_reviewer' where raw_app_meta_data->>'role' in ('admin','moderator') or raw_app_meta_data->>'identity_reviewer'='true';
select set_config('request.jwt.claims',json_build_object('sub',a,'role','authenticated')::text,true) from intake_fixture;
set local role authenticated;
do $$begin
 if (public.sera_identity_status()->>'intake_available')::boolean then raise exception 'Intake open without reviewer';end if;
 begin perform public.sera_identity_start('passport',true);raise exception 'Unattended collection allowed';exception when raise_exception then if sqlerrm='Unattended collection allowed' then raise;end if;end;
end$$;
reset role;
update auth.users set raw_app_meta_data=jsonb_build_object('identity_reviewer',true) where id=(select m from intake_fixture);
set local role authenticated;
do $$begin if not (public.sera_identity_status()->>'intake_available')::boolean then raise exception 'Dedicated reviewer did not open intake';end if;perform public.sera_identity_start('passport',true);end$$;
reset role;
select set_config('request.jwt.claims',json_build_object('sub',m,'role','authenticated')::text,true) from intake_fixture;
set local role authenticated;
do $$begin if (public.sera_identity_status()->>'intake_available')::boolean then raise exception 'Sole reviewer can apply to self-review';end if;perform public.sera_identity_queue();end$$;
reset role;
rollback;
select 'PASS: paused intake without reviewers, dedicated reviewer authority, no sole self-review' as result;
