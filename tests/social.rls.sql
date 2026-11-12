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
 insert into public.posts(kind,status,title,body,repost_of,repost_note) values('idea','published','Repost','Shared original post',pid,'QA note');
 if (select repost_count from public.sera_repost_states(array[pid]))<>1 or not (select reposted from public.sera_repost_states(array[pid])) then raise exception 'Repost state incorrect';end if;
 begin insert into public.posts(kind,status,title,body,repost_of) values('idea','published','Repost','Shared original post',pid);raise exception 'Duplicate repost allowed';exception when unique_violation then null;end;
 perform public.sera_set_comment_like(parent,true);perform public.sera_set_comment_like(parent,true);
 if (select like_count from public.sera_comment_reaction_states(array[parent]))<>1 then raise exception 'Comment reaction not idempotent';end if;
 update public.post_comments set body='Edited QA comment' where id=parent;
 perform public.sera_undo_repost(pid);
 if (select repost_count from public.sera_repost_states(array[pid]))<>0 then raise exception 'Undo repost failed';end if;
 insert into public.professional_blocks(blocker_id,blocked_id) select b,a from sera_qa_ids;
 if exists(select 1 from public.posts where id=pid) then raise exception 'Blocked post leaked';end if;
 if exists(select 1 from public.post_comments where post_id=pid) then raise exception 'Blocked post comments leaked';end if;
 delete from public.professional_blocks where blocker_id=(select b from sera_qa_ids) and blocked_id=(select a from sera_qa_ids);
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
 perform public.sera_manage_post((select photo from sera_qa_ids),'trash');
 if exists(select 1 from public.posts where id=(select photo from sera_qa_ids)) then raise exception 'Binned post visible';end if;
 perform public.sera_manage_post((select photo from sera_qa_ids),'restore');
 if not exists(select 1 from public.post_images where post_id=(select photo from sera_qa_ids)) then raise exception 'Restore lost images';end if;
 perform public.sera_manage_post((select photo from sera_qa_ids),'trash');
 perform public.sera_manage_post((select photo from sera_qa_ids),'purge');
 if exists(select 1 from public.post_images where post_id=(select photo from sera_qa_ids)) then raise exception 'Purge did not cascade';end if;
end$$;
reset role;

-- Post management: audience transitions and owner-only recovery RPCs.
select set_config('request.jwt.claims',json_build_object('sub',(select a from sera_qa_ids),'role','authenticated')::text,true);
set local role authenticated;
select public.sera_manage_post((select post from sera_qa_ids),'privacy','only_me');
do $$begin
 if not exists(select 1 from public.posts where id=(select post from sera_qa_ids)) then raise exception 'Owner cannot read own private post';end if;
end$$;
reset role;
select set_config('request.jwt.claims',json_build_object('sub',(select b from sera_qa_ids),'role','authenticated')::text,true);
set local role authenticated;
do $$declare pid uuid:=(select post from sera_qa_ids); action_name text; denied boolean;begin
 if exists(select 1 from public.posts where id=pid) then raise exception 'Only-me post leaked';end if;
 foreach action_name in array array['privacy','trash','restore','purge'] loop
  denied:=false;
  begin perform public.sera_manage_post(pid,action_name,'public');
  exception when raise_exception then denied:=true;end;
  if not denied then raise exception 'Non-owner management allowed: %',action_name;end if;
 end loop;
end$$;
reset role;
-- Without a connection or follow, neither restricted audience is visible.
do $$declare audience_name text;begin
 foreach audience_name in array array['connections','followers'] loop
  perform set_config('request.jwt.claims',json_build_object('sub',(select a from sera_qa_ids),'role','authenticated')::text,true);
  perform public.sera_manage_post((select post from sera_qa_ids),'privacy',audience_name);
  perform set_config('request.jwt.claims',json_build_object('sub',(select b from sera_qa_ids),'role','authenticated')::text,true);
  if private.post_published((select post from sera_qa_ids)) then raise exception 'Restricted audience leaked: %',audience_name;end if;
 end loop;
end$$;
select set_config('request.jwt.claims',json_build_object('sub',(select a from sera_qa_ids),'role','authenticated')::text,true);
set local role authenticated;
do $$declare pid uuid:=(select post from sera_qa_ids);begin
 perform public.sera_manage_post(pid,'privacy','only_me');
 perform public.sera_manage_post(pid,'trash');
 if not exists(select 1 from public.sera_list_post_bin() where id=pid and audience='only_me' and expires_at=deleted_at+interval '30 days') then raise exception 'Bin did not preserve audience or retention';end if;
end$$;
reset role;
select set_config('request.jwt.claims',json_build_object('sub',(select b from sera_qa_ids),'role','authenticated')::text,true);
set local role authenticated;
do $$begin
 if exists(select 1 from public.sera_list_post_bin() where id=(select post from sera_qa_ids)) then raise exception 'Another owner bin leaked';end if;
end$$;
reset role;
select set_config('request.jwt.claims',json_build_object('sub',(select a from sera_qa_ids),'role','authenticated')::text,true);
set local role authenticated;
do $$declare pid uuid:=(select post from sera_qa_ids);begin
 perform public.sera_manage_post(pid,'restore');
 if not exists(select 1 from public.posts where id=pid and audience='only_me') then raise exception 'Restore changed post privacy';end if;
 perform public.sera_manage_post(pid,'trash');
end$$;
reset role;
-- Positive audience access: fixtures remain inside the rollback transaction.
select set_config('request.jwt.claims',json_build_object('sub',(select b from sera_qa_ids),'role','authenticated')::text,true);
set local role authenticated;
insert into public.professional_follows(follower_id,followed_id) select b,a from sera_qa_ids;
reset role;
select set_config('request.jwt.claims',json_build_object('sub',(select a from sera_qa_ids),'role','authenticated')::text,true);
set local role authenticated;
select public.sera_manage_post((select post from sera_qa_ids),'restore');
select public.sera_manage_post((select post from sera_qa_ids),'privacy','followers');
reset role;
select set_config('request.jwt.claims',json_build_object('sub',(select b from sera_qa_ids),'role','authenticated')::text,true);
set local role authenticated;
do $$begin
 if not exists(select 1 from public.posts where id=(select post from sera_qa_ids)) then raise exception 'Follower cannot read followers post';end if;
end$$;
insert into public.professional_connection_requests(requester_id,recipient_id) select b,a from sera_qa_ids;
reset role;
select set_config('request.jwt.claims',json_build_object('sub',(select a from sera_qa_ids),'role','authenticated')::text,true);
set local role authenticated;
update public.professional_connection_requests set status='accepted' where requester_id=(select b from sera_qa_ids) and recipient_id=(select a from sera_qa_ids);
select public.sera_manage_post((select post from sera_qa_ids),'privacy','connections');
reset role;
select set_config('request.jwt.claims',json_build_object('sub',(select b from sera_qa_ids),'role','authenticated')::text,true);
set local role authenticated;
do $$begin
 if not exists(select 1 from public.posts where id=(select post from sera_qa_ids)) then raise exception 'Accepted connection cannot read connections post';end if;
end$$;
reset role;
select set_config('request.jwt.claims',json_build_object('sub',(select a from sera_qa_ids),'role','authenticated')::text,true);
set local role authenticated;
select public.sera_manage_post((select post from sera_qa_ids),'trash');
reset role;

-- Search uses the same RLS as the feed: no private or binned keyword matches,
-- author discovery or topic extraction for another member.
select set_config('request.jwt.claims',json_build_object('sub',(select a from sera_qa_ids),'role','authenticated')::text,true);
set local role authenticated;
select public.sera_manage_post((select post from sera_qa_ids),'restore');
select public.sera_manage_post((select post from sera_qa_ids),'privacy','only_me');
do $$begin
 if not exists(select 1 from public.posts where id=(select post from sera_qa_ids) and status='published' and title ilike '%QA idea%') then raise exception 'Owner search lost private post';end if;
end$$;
reset role;
select set_config('request.jwt.claims',json_build_object('sub',(select b from sera_qa_ids),'role','authenticated')::text,true);
set local role authenticated;
do $$begin
 if exists(select 1 from public.posts where id=(select post from sera_qa_ids) and status='published' and (title ilike '%QA idea%' or skills @> array['design'])) then raise exception 'Private post appeared in search';end if;
 if exists(select 1 from public.post_authors where id=(select a from sera_qa_ids)) then raise exception 'Private-only author appeared in discovery';end if;
end$$;
reset role;
select set_config('request.jwt.claims',json_build_object('sub',(select a from sera_qa_ids),'role','authenticated')::text,true);
set local role authenticated;
select public.sera_manage_post((select post from sera_qa_ids),'privacy','public');
reset role;
select set_config('request.jwt.claims',json_build_object('sub',(select b from sera_qa_ids),'role','authenticated')::text,true);
set local role authenticated;
do $$begin
 if not exists(select 1 from public.posts where id=(select post from sera_qa_ids) and status='published' and title ilike '%QA idea%') then raise exception 'Public post missing from search';end if;
end$$;
reset role;
select set_config('request.jwt.claims',json_build_object('sub',(select a from sera_qa_ids),'role','authenticated')::text,true);
set local role authenticated;
select public.sera_manage_post((select post from sera_qa_ids),'trash');
reset role;
select set_config('request.jwt.claims',json_build_object('sub',(select b from sera_qa_ids),'role','authenticated')::text,true);
set local role authenticated;
do $$begin
 if exists(select 1 from public.posts where id=(select post from sera_qa_ids) and status='published' and title ilike '%QA idea%') then raise exception 'Binned post appeared in search';end if;
end$$;
reset role;

-- Simulate expiry without waiting 30 days; the fixture transaction is rolled back.
update public.posts set deleted_at=now()-interval '31 days' where id=(select post from sera_qa_ids);
select set_config('request.jwt.claims',json_build_object('sub',(select a from sera_qa_ids),'role','authenticated')::text,true);
set local role authenticated;
do $$declare pid uuid:=(select post from sera_qa_ids); denied boolean:=false;begin
 if exists(select 1 from public.sera_list_post_bin() where id=pid) then raise exception 'Expired post remained recoverable';end if;
 begin perform public.sera_manage_post(pid,'restore');exception when raise_exception then denied:=true;end;
 if not denied then raise exception 'Expired post restored';end if;
end$$;
reset role;

select 'PASS: social feed ownership, visibility, privacy, counters, cascades and anonymous isolation' as result;
rollback;
