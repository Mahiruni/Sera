begin;
create temporary table account_qa(owner_id uuid,outsider_id uuid);
insert into account_qa values(gen_random_uuid(),gen_random_uuid());
grant select on account_qa to authenticated,anon;
insert into auth.users(id,email_confirmed_at,raw_user_meta_data) select owner_id,now(),'{"full_name":"Sera Profile QA"}'::jsonb from account_qa union all select outsider_id,now(),'{"full_name":"Sera Outsider QA"}'::jsonb from account_qa;
select set_config('request.jwt.claims',json_build_object('sub',(select owner_id from account_qa),'role','authenticated')::text,true);
set local role authenticated;
update public.profiles set headline='QA engineer',education='QA education',summary='Fictional QA summary' where id=(select owner_id from account_qa);
insert into public.account_assets(user_id,avatar_path) select owner_id,owner_id::text||'/avatar/qa.jpg' from account_qa;
insert into storage.objects(bucket_id,name) select 'sera-account-private',owner_id::text||'/cv/qa.pdf' from account_qa;
do $$ begin
 if not exists(select 1 from public.profiles where id=(select owner_id from account_qa) and headline='QA engineer') then raise exception 'Profile save failed';end if;
 if not exists(select 1 from storage.objects where name=(select owner_id::text||'/cv/qa.pdf' from account_qa)) then raise exception 'Own file inaccessible';end if;
 begin update public.profiles set headline=repeat('x',121) where id=(select owner_id from account_qa);raise exception 'Headline limit bypass';exception when check_violation then null;end;
 begin update public.account_assets set avatar_path=(select outsider_id::text||'/avatar/qa.jpg' from account_qa) where user_id=(select owner_id from account_qa);raise exception 'Cross-owner metadata path';exception when check_violation then null;end;
end $$;
reset role;
select set_config('request.jwt.claims',json_build_object('sub',(select outsider_id from account_qa),'role','authenticated')::text,true);
set local role authenticated;
do $$ declare n int;begin
 if exists(select 1 from public.account_assets where user_id=(select owner_id from account_qa)) then raise exception 'Outsider metadata read';end if;
 if exists(select 1 from storage.objects where name=(select owner_id::text||'/cv/qa.pdf' from account_qa)) then raise exception 'Outsider file read';end if;
 update public.profiles set headline='Intrusion' where id=(select owner_id from account_qa);get diagnostics n=row_count;if n<>0 then raise exception 'Outsider edited profile';end if;
 begin insert into storage.objects(bucket_id,name) select 'sera-account-private',owner_id::text||'/avatar/intrusion.jpg' from account_qa;raise exception 'Outsider upload';exception when insufficient_privilege then null;end;
 begin insert into public.account_assets(user_id) select owner_id from account_qa;raise exception 'Outsider metadata write';exception when insufficient_privilege then null;end;
end $$;
reset role;
select set_config('request.jwt.claims','{"role":"anon"}',true);
set local role anon;
do $$begin
 begin perform * from public.account_assets;raise exception 'Anonymous assets accessible';exception when insufficient_privilege then null;end;
 if exists(select 1 from storage.objects where bucket_id='sera-account-private') then raise exception 'Anonymous file read';end if;
end $$;
reset role;
do $$begin if (select public from storage.buckets where id='sera-account-private') then raise exception 'Public bucket';end if;end $$;
rollback;
select 'PASS: profile edit, limits, private files, owner access, outsider rejection and anonymous denial' as result;
