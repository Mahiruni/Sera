-- Social interaction integrity: retain legacy records, use canonical sources and authenticated ownership.
update public.posts r set repost_note=left(btrim(left(r.body,length(r.body)-length(x.suffix))),400)
from (select o.id, left(coalesce(nullif(o.body,''),nullif(o.title,''),'Professional update'),2000)||E'\n\nOriginal: https://sera.hisabtech.com/posts/'||o.id::text suffix from public.posts o) x
where r.repost_of=x.id and r.repost_note='' and r.title like 'Reposted:%'
and right(r.body,length(x.suffix))=x.suffix and length(r.body)>length(x.suffix)
and length(btrim(left(r.body,length(r.body)-length(x.suffix))))<=400;

create function private.sera_social_visible(p_viewer uuid,p_author uuid) returns boolean language sql stable security definer set search_path='' as $$
select p_viewer is not null and not exists(select 1 from public.professional_blocks b where (b.blocker_id=p_viewer and b.blocked_id=p_author) or (b.blocked_id=p_viewer and b.blocker_id=p_author));
$$;
revoke all on function private.sera_social_visible(uuid,uuid) from public,anon,authenticated;
grant execute on function private.sera_social_visible(uuid,uuid) to authenticated;
create or replace function private.can_read_post(p_id uuid) returns boolean language sql stable security definer set search_path='' as $$
select auth.uid() is not null and exists(select 1 from public.posts p where p.id=p_id and (p.status='published' or p.author_id=auth.uid()) and private.sera_social_visible(auth.uid(),p.author_id));
$$;
create or replace function private.post_published(p_id uuid) returns boolean language sql stable security definer set search_path='' as $$
select exists(select 1 from public.posts p where p.id=p_id and p.status='published' and private.sera_social_visible(auth.uid(),p.author_id));
$$;
drop policy posts_read on public.posts;
create policy posts_read on public.posts for select to authenticated using(private.can_read_post(id));
drop policy post_comments_read on public.post_comments;
create policy post_comments_read on public.post_comments for select to authenticated using(private.can_read_post(post_id) and private.sera_social_visible(auth.uid(),author_id));

create function private.sera_guard_repost() returns trigger language plpgsql security definer set search_path='' as $$
declare source public.posts%rowtype;
begin
 if tg_op='UPDATE' and new.repost_of is distinct from old.repost_of then raise exception 'Original post cannot change';end if;
 if new.repost_of is null then
  if new.repost_note<>'' then raise exception 'Repost note requires an original';end if;
  return new;
 end if;
 select * into source from public.posts where id=new.repost_of;
 if not found or source.status<>'published' or source.repost_of is not null or source.author_id=new.author_id or not private.sera_social_visible(new.author_id,source.author_id) then raise exception 'Original post unavailable';end if;
 return new;
end;$$;
revoke all on function private.sera_guard_repost() from public,anon,authenticated;
create trigger sera_repost_guard before insert or update of repost_of,repost_note,status on public.posts for each row execute function private.sera_guard_repost();

-- Keep moderated service-role writes valid only for verified members and visible posts.
create or replace function private.guard_comment() returns trigger language plpgsql security definer set search_path='' as $$
declare u uuid:=auth.uid(); owner_id uuid;
begin
 if u is null then
  if current_setting('role')<>'service_role' or not exists(select 1 from auth.users where id=new.author_id and email_confirmed_at is not null) then raise exception 'Verified account required';end if;
  u:=new.author_id;
 end if;
 if new.author_id<>u then raise exception 'Ownership mismatch';end if;
 select p.author_id into owner_id from public.posts p where p.id=new.post_id and p.status='published';
 if owner_id is null or not private.sera_social_visible(u,owner_id) then raise exception 'Post unavailable';end if;
 if tg_op='INSERT' then
  if new.parent_id is not null and not exists(select 1 from public.post_comments c where c.id=new.parent_id and c.post_id=new.post_id and c.parent_id is null and private.sera_social_visible(u,c.author_id)) then raise exception 'Replies attach to a visible top-level comment on the same post';end if;
  perform pg_advisory_xact_lock(hashtextextended(u::text,19));
  if (select count(*) from public.post_comments where author_id=u and created_at>now()-interval '1 hour')>=120 then raise exception 'Comment limit reached';end if;
  new.edited_at:=null;
 else
  if new.author_id<>old.author_id or new.post_id<>old.post_id or new.parent_id is distinct from old.parent_id then raise exception 'Comment identity cannot change';end if;
  if btrim(new.body) is distinct from btrim(old.body) then new.edited_at:=now();end if;
 end if;
 return new;
end;$$;

create function public.sera_repost_states(p_ids uuid[]) returns table(id uuid,repost_count bigint,reposted boolean) language sql stable security definer set search_path='' as $$
select p.id,(select count(*) from public.posts r where r.repost_of=p.id and r.status='published' and private.sera_social_visible(auth.uid(),r.author_id)),exists(select 1 from public.posts r where r.repost_of=p.id and r.author_id=auth.uid() and r.status='published')
from public.posts p where p.id=any(p_ids[1:100]) and p.status='published' and p.repost_of is null and private.can_read_post(p.id);
$$;
create function public.sera_undo_repost(p_original uuid) returns void language plpgsql security invoker set search_path='' as $$
begin
 if auth.uid() is null then raise exception 'Sign in required';end if;
 delete from public.posts where repost_of=p_original and author_id=auth.uid();
end;$$;
revoke all on function public.sera_repost_states(uuid[]),public.sera_undo_repost(uuid) from public,anon;
grant execute on function public.sera_repost_states(uuid[]),public.sera_undo_repost(uuid) to authenticated;

create table public.sera_comment_likes(comment_id uuid not null references public.post_comments(id) on delete cascade,user_id uuid not null references auth.users(id) on delete cascade,created_at timestamptz not null default now(),primary key(comment_id,user_id));
alter table public.sera_comment_likes enable row level security;
revoke all on public.sera_comment_likes from public,anon,authenticated;
create function public.sera_comment_reaction_states(p_ids uuid[]) returns table(id uuid,like_count bigint,liked boolean) language sql stable security definer set search_path='' as $$
select c.id,(select count(*) from public.sera_comment_likes l where l.comment_id=c.id and private.sera_social_visible(auth.uid(),l.user_id)),exists(select 1 from public.sera_comment_likes l where l.comment_id=c.id and l.user_id=auth.uid())
from public.post_comments c where c.id=any(p_ids[1:200]) and private.can_read_post(c.post_id) and private.sera_social_visible(auth.uid(),c.author_id);
$$;
create function public.sera_set_comment_like(p_comment uuid,p_liked boolean) returns void language plpgsql security definer set search_path='' as $$
declare u uuid:=auth.uid();
begin
 if u is null or not exists(select 1 from auth.users where id=u and email_confirmed_at is not null) then raise exception 'Verified account required';end if;
 if not p_liked then delete from public.sera_comment_likes where comment_id=p_comment and user_id=u;return;end if;
 if not exists(select 1 from public.post_comments c where c.id=p_comment and private.post_published(c.post_id) and private.sera_social_visible(u,c.author_id)) then raise exception 'Comment unavailable';end if;
 insert into public.sera_comment_likes(comment_id,user_id) values(p_comment,u) on conflict do nothing;
end;$$;
revoke all on function public.sera_comment_reaction_states(uuid[]),public.sera_set_comment_like(uuid,boolean) from public,anon;
grant execute on function public.sera_comment_reaction_states(uuid[]),public.sera_set_comment_like(uuid,boolean) to authenticated;

-- Repost notifications are created once per published repost, not per retry.
alter table public.professional_notifications drop constraint professional_notifications_kind_check;
alter table public.professional_notifications add constraint professional_notifications_kind_check check(kind in ('like','comment','share','follow','connection_request','connection_accepted','repost','reply'));
create function private.sera_notify_repost() returns trigger language plpgsql security definer set search_path='' as $$
declare owner_id uuid;
begin
 if new.repost_of is not null and new.status='published' then
  if tg_op='UPDATE' and old.status='published' then return new;end if;
  select author_id into owner_id from public.posts where id=new.repost_of and status='published';
  if owner_id is not null and owner_id<>new.author_id and private.sera_social_visible(new.author_id,owner_id) then
   insert into public.professional_notifications(recipient_id,actor_id,kind,post_id) values(owner_id,new.author_id,'repost',new.repost_of);
  end if;
 end if;
 return new;
end;$$;
revoke all on function private.sera_notify_repost() from public,anon,authenticated;
create trigger sera_notify_repost_publish after insert or update of status on public.posts for each row execute function private.sera_notify_repost();
create or replace function private.notify_social_activity() returns trigger language plpgsql security definer set search_path='' as $$
declare owner_id uuid; actor uuid; event_kind text; parent_author uuid;
begin
 if tg_table_name='post_comments' then actor:=new.author_id; event_kind:='comment';
 elsif tg_table_name='post_likes' then actor:=new.user_id; event_kind:='like';
 else actor:=new.user_id; event_kind:='share'; end if;
 select p.author_id into owner_id from public.posts p where p.id=new.post_id and p.status='published';
 if owner_id is not null and owner_id<>actor and private.sera_social_visible(actor,owner_id) then
  insert into public.professional_notifications(recipient_id,actor_id,kind,post_id) values(owner_id,actor,event_kind,new.post_id);
 end if;
 if tg_table_name='post_comments' then
  if new.parent_id is not null then
   select author_id into parent_author from public.post_comments where id=new.parent_id;
   if parent_author is not null and parent_author<>actor and parent_author<>owner_id and private.sera_social_visible(actor,parent_author) then
    insert into public.professional_notifications(recipient_id,actor_id,kind,post_id) values(parent_author,actor,'reply',new.post_id);
   end if;
  end if;
 end if;
 return new;
end;$$;