begin;
create temporary table fixture(a uuid,b uuid,c uuid,post uuid,root uuid,other_root uuid);
insert into fixture select gen_random_uuid(),gen_random_uuid(),gen_random_uuid(),gen_random_uuid(),gen_random_uuid(),gen_random_uuid();
grant select,update on fixture to authenticated;
insert into auth.users(id,email_confirmed_at) select unnest(array[a,b,c]),now() from fixture;
insert into public.account_assets(user_id,avatar_path,cv_path)
select b,b||'/avatar/current.jpg',b||'/cv/private.pdf' from fixture;
insert into storage.objects(bucket_id,name) select 'sera-account-private',b||suffix from fixture,
unnest(array['/avatar/current.jpg','/avatar/old.jpg','/cv/private.pdf']) suffix;
select set_config('request.jwt.claims',json_build_object('sub',a)::text,true) from fixture;
set local role authenticated;
with p as (insert into public.posts(kind,status,title,body) values('idea','published','Activity QA','A story') returning id) update fixture set post=p.id from p;
with c as (insert into public.post_comments(post_id,body) select post,'My comment' from fixture returning id) update fixture set root=c.id from c;
insert into public.post_likes(post_id) select post from fixture;
do $$begin
 if exists(select from public.member_notifications) then raise exception 'Self notification'; end if;
 if exists(select from public.member_cards(array[(select b from fixture)])) then raise exception 'Unrelated profile exposed'; end if;
end$$;
reset role;
select set_config('request.jwt.claims',json_build_object('sub',b)::text,true) from fixture;
set local role authenticated;
insert into public.post_likes(post_id) select post from fixture;
delete from public.post_likes;
insert into public.post_likes(post_id) select post from fixture;
insert into public.post_shares(post_id) select post from fixture;
insert into public.post_saves(post_id) select post from fixture;
with c as (insert into public.post_comments(post_id,body) select post,'Another perspective' from fixture returning id) update fixture set other_root=c.id from c;
insert into public.post_comments(post_id,parent_id,body) select post,root,'Reply to author' from fixture;
do $$begin
 if exists(select from public.member_notifications) then raise exception 'Actor saw recipient inbox'; end if;
 begin insert into public.member_notifications(recipient_id,actor_id,post_id,kind) select a,b,post,'like' from fixture;
 raise exception 'Forged event'; exception when insufficient_privilege then null;end;
end$$;
reset role;
select set_config('request.jwt.claims',json_build_object('sub',c)::text,true) from fixture;
set local role authenticated;
insert into public.post_comments(post_id,parent_id,body) select post,other_root,'Third member replies' from fixture;
do $$begin
 if exists(select from public.member_notifications) then raise exception 'Unrelated inbox visible'; end if;
 update public.member_notifications set read_at=now();
 if found then raise exception 'Outsider marked read'; end if;
end$$;
reset role;
select set_config('request.jwt.claims',json_build_object('sub',a)::text,true) from fixture;
set local role authenticated;
do $$begin
 if (select count(*) from public.member_notifications)<>5 or
 (select count(*) from public.member_notifications where kind='reply')<>1 then raise exception 'Wrong recipient routing or duplicate event'; end if;
 if (select count(*) from storage.objects where bucket_id='sera-account-private')<>1 then raise exception 'Wrong visible object count'; end if;
 if not exists(select from public.member_cards(array[(select b from fixture)]) where avatar_path like '%/avatar/current.jpg') then raise exception 'Current avatar missing';end if;
 if exists(select from storage.objects where name like '%/cv/%' or name like '%old.jpg') then raise exception 'Private asset leaked';end if;
 update public.member_notifications set read_at=now() where kind='like';
 if (select count(*) from public.member_notifications where read_at is null)<>4 then raise exception 'Read state incorrect';end if;
 delete from public.post_comments where id=(select root from fixture);
 if exists(select from public.member_notifications where kind='reply') then raise exception 'Deleted reply link survived'; end if;
end$$;
reset role;
select set_config('request.jwt.claims',json_build_object('sub',b)::text,true) from fixture;
set local role authenticated;
do $$begin
 if (select count(*) from public.member_notifications where kind='reply')<>1 then raise exception 'Reply to another commenter missing';end if;
 update public.account_assets set avatar_path=user_id||'/avatar/replaced.jpg' where user_id=auth.uid();
end$$;
reset role;
select set_config('request.jwt.claims',json_build_object('sub',a)::text,true) from fixture;
set local role authenticated;
do $$begin
 if exists(select from storage.objects where name like '%current.jpg') then raise exception 'Replaced avatar still signable';end if;
end$$;
reset role;
set local role anon;
do $$begin
 begin perform * from public.member_notifications;raise exception 'Anonymous inbox access';exception when insufficient_privilege then null;end;
 begin perform * from public.member_cards('{}');raise exception 'Anonymous member lookup';exception when insufficient_privilege then null;end;
end$$;
reset role;
rollback;
select 'PASS: notification recipients, deduplication, scoped avatars, read state and isolation' as result;
