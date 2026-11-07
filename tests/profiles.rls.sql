-- Disposable fixtures are rolled back; this tests database authorization, not email delivery.
begin;
create temporary table sera_qa_ids(a uuid,b uuid);
insert into sera_qa_ids values(gen_random_uuid(),gen_random_uuid());
grant select on sera_qa_ids to authenticated;
insert into auth.users(id,raw_user_meta_data) select a,'{"full_name":"Sera QA A","journey":"admin","locale":"zz"}'::jsonb from sera_qa_ids;
insert into auth.users(id,raw_user_meta_data) select b,'{"full_name":"Sera QA B","journey":"employer","locale":"am"}'::jsonb from sera_qa_ids;
do $$begin if (select journey from public.profiles where id=(select a from sera_qa_ids))<>'candidate' then raise exception 'Untrusted role was not normalized';end if;end$$;
select set_config('request.jwt.claims',json_build_object('sub',(select a from sera_qa_ids),'role','authenticated')::text,true);
set local role authenticated;
do $$declare affected integer;begin
 if (select count(*) from public.profiles)<>1 then raise exception 'Cross-user read';end if;
 update public.profiles set full_name='Unauthorized' where id=(select b from sera_qa_ids);get diagnostics affected=row_count;if affected<>0 then raise exception 'Cross-user update';end if;
 update public.profiles set full_name='Sera QA updated' where id=(select a from sera_qa_ids);get diagnostics affected=row_count;if affected<>1 then raise exception 'Owner update failed';end if;
 begin update public.profiles set id=(select b from sera_qa_ids) where id=(select a from sera_qa_ids);raise exception 'Ownership reassignment allowed';exception when insufficient_privilege then null;end;
 begin insert into public.profiles(id) values(gen_random_uuid());raise exception 'Direct insert allowed';exception when insufficient_privilege then null;end;
end$$;
reset role;
select set_config('request.jwt.claims',json_build_object('sub',(select b from sera_qa_ids),'role','authenticated')::text,true);
set local role authenticated;
do $$begin if (select count(*) from public.profiles)<>1 or (select full_name from public.profiles)<>'Sera QA B' then raise exception 'Second-user isolation failed';end if;end$$;
reset role;
set local role anon;
do $$begin begin perform * from public.profiles;raise exception 'Anonymous profile read allowed';exception when insufficient_privilege then null;end;end$$;
reset role;
select 'PASS: two-user ownership, update, privilege and anonymous isolation' as result;
rollback;
