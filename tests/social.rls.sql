-- Disposable fixtures are rolled back; this tests database authorization and integrity for the social feed.
begin;
create temporary table sera_qa_ids(a uuid,b uuid,u uuid,post uuid,photo uuid);
insert into sera_qa_ids values(gen_random_uuid(),gen_random_uuid(),gen_random_uuid(),gen_random_uuid(),gen_random_uuid());
grant select,update on sera_qa_ids to authenticated;
insert into auth.users(id,email_confirmed_at,raw_user_meta_data) select a,now(),'{"full_name":"Sera QA A"}'::jsonb from sera_qa_ids;
insert into auth.users(id,email_confirmed_at,raw_user_meta_data) select b,now(),'{"full_name":"Sera QA B"}'::jsonb from sera_qa_ids;
insert into auth.users(id,raw_user_meta_data) select u,'{"full_name":"Sera QA Unverified"}'::jsonb from sera_qa_ids;

-- Author A: publish a post, a draft, and a photo post that needs an image before publishing.
select set_config('request.jwt.claims',json_build_object('sub',(select a from sera_qa_ids),'role','authenticated')::text,true);
set local role authenticated;
do $$declare pid uuid;begin
 insert into public.posts(kind,status,title,body,skills) values('idea','published','QA idea','Body',array['design']) returning id into pid;
 update sera_qa_ids set post=pid;
 insert into public.posts(kind,status,title) values('idea','draft','QA draft');
 begin insert into public.posts(kind,status,title) values('photos','published','Direct publish');raise exception 'Photo post inserted already published';exception when raise_exception then if sqlerrm like 'Photo post inserted%' then raise; end if;end;
 insert into public.posts(kind,title) values('photos','QA photos') returning id into pid;
 update sera_qa_ids set photo=pid;
 begin update public.posts set status='published' where id=pid;raise exception 'Published photo post without image';exception when raise_exception then if sqlerrm like 'Published photo post%' then raise; end if;end;
 insert into public.post_images(post_id,path) select photo,a::text||'/'||photo::text||'/one.webp' from sera_qa_ids;
 update public.posts set status='published' where id=pid;
 begin delete from public.post_images where post_id=pid;raise exception 'Last image of published photo post removed';exception when raise_exception then if sqlerrm like 'Last image%' then raise; end if;end;
 begin update public.posts set like_count=99 where id=pid;raise exception 'Counter write allowed';exception when insufficient_privilege then null;end;
 begin update public.posts set kind='quote' where id=pid;raise exception 'Post type change allowed';exception when insufficient_privilege then null;end;
end$$;
reset role;

-- Member B: sees published only, cannot edit or delete, interacts privately.
select set_config('request.jwt.claims',json_build_object('sub',(select b from sera_qa_ids),'role','authenticated')::text,true);
set local role authenticated;
do $$declare affected integer;pid uuid:=(select post from sera_qa_ids);parent uuid;begin
 if (select count(*) from public.posts where title='QA draft')<>0 then raise exception 'Draft visible to another member';end if;
 if (select count(*) from public.posts where id=pid)<>1 then raise exception 'Published post not visible to members';end if;
 update public.posts set title='Hacked' where id=pid;get diagnostics affected=row_count;if affected<>0 then raise exception 'Cross-user edit';end if;
 delete from public.posts where id=pid;get diagnostics affected=row_count;if affected<>0 then raise exception 'Cross-user delete';end if;
 insert into public.post_likes(post_id) values(pid);
 begin insert into public.post_likes(post_id) values(pid);raise exception 'Double like allowed';exception when unique_violation then null;end;
 insert into public.post_saves(post_id) values(pid);
 insert into public.post_shares(post_id) values(pid);
 insert into public.post_comments(post_id,body) values(pid,'Nice') returning id into parent;
 insert into public.post_comments(post_id,parent_id,body) values(pid,parent,'A reply');
 begin insert into public.post_comments(post_id,parent_id,body) select pid,id,'Nested' from public.post_comments where parent_id is not null limit 1;raise exception 'Nested reply allowed';exception when raise_exception then if sqlerrm like 'Nested reply%' then raise; end if;end;
 if (select (like_count,comment_count,share_count)::text from public.posts where id=pid)<>'(1,2,1)' then raise exception 'Counters wrong: %',(select (like_count,comment_count,share_count)::text from public.posts where id=pid);end if;
end$$;
reset role;

-- Privacy: A cannot see B's likes or saves; A (post owner) may remove a comment, replies cascade, counts follow.
select set_config('request.jwt.claims',json_build_object('sub',(select a from sera_qa_ids),'role','authenticated')::text,true);
set local role authenticated;
do $$begin
 if (select count(*) from public.post_likes)<>0 or (select count(*) from public.post_saves)<>0 or (select count(*) from public.post_shares)<>0 then raise exception 'Private interaction rows leaked';end if;
 delete from public.post_comments where parent_id is null;
 if (select comment_count from public.posts where id=(select post from sera_qa_ids))<>0 then raise exception 'Comment count not reconciled after cascade';end if;
 if (select headline from public.post_authors where id=(select a from sera_qa_ids)) is null then raise exception 'Author card missing';end if;
end$$;
reset role;

-- Unverified and anonymous accounts.
select set_config('request.jwt.claims',json_build_object('sub',(select u from sera_qa_ids),'role','authenticated')::text,true);
set local role authenticated;
do $$begin
 begin insert into public.posts(kind,status,title) values('idea','published','Unverified post');raise exception 'Unverified post allowed';exception when insufficient_privilege then null;end;
 begin insert into public.post_likes(post_id) select post from sera_qa_ids;raise exception 'Unverified like allowed';exception when insufficient_privilege then null;end;
end$$;
reset role;
set local role anon;
do $$begin begin perform * from public.posts;raise exception 'Anonymous feed read allowed';exception when insufficient_privilege then null;end;end$$;
reset role;

-- Deleting a published photo post cascades its images despite the last-image guard.
select set_config('request.jwt.claims',json_build_object('sub',(select a from sera_qa_ids),'role','authenticated')::text,true);
set local role authenticated;
do $$declare affected integer;begin
 delete from public.posts where id=(select photo from sera_qa_ids);get diagnostics affected=row_count;
 if affected<>1 or (select count(*) from public.post_images where post_id=(select photo from sera_qa_ids))<>0 then raise exception 'Post delete did not cascade';end if;
end$$;
reset role;
select 'PASS: social feed ownership, visibility, privacy, counters, cascades and anonymous isolation' as result;
rollback;
