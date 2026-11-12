-- Preserve published post identity and engagement during the recovery window.
alter table public.posts add column audience text not null default 'public' check(audience in ('public','connections','followers','only_me')),add column deleted_at timestamptz;
create index posts_bin_expiry on public.posts(deleted_at) where deleted_at is not null;
create function private.sera_audience_allows(p_owner uuid,p_audience text,p_viewer uuid) returns boolean language sql stable security definer set search_path='' as $$
select p_viewer is not null and private.sera_social_visible(p_viewer,p_owner) and (p_owner=p_viewer or p_audience='public' or (p_audience='connections' and exists(select 1 from public.professional_connection_requests c where c.status='accepted' and ((c.requester_id=p_owner and c.recipient_id=p_viewer) or (c.recipient_id=p_owner and c.requester_id=p_viewer)))) or (p_audience='followers' and exists(select 1 from public.professional_follows f where f.follower_id=p_viewer and f.followed_id=p_owner)));
$$;
revoke all on function private.sera_audience_allows(uuid,text,uuid) from public,anon,authenticated;
create function private.sera_audience_to_me(p_owner uuid,p_audience text) returns boolean language sql stable security definer set search_path='' as $$select private.sera_audience_allows(p_owner,p_audience,auth.uid())$$;
revoke all on function private.sera_audience_to_me(uuid,text) from public,anon;
grant execute on function private.sera_audience_to_me(uuid,text) to authenticated;
create or replace function private.can_read_post(p_id uuid) returns boolean language sql stable security definer set search_path='' as $$
select auth.uid() is not null and exists(select 1 from public.posts p where p.id=p_id and p.deleted_at is null and (p.status='published' or p.author_id=auth.uid()) and private.sera_audience_allows(p.author_id,p.audience,auth.uid()) and (p.repost_of is null or exists(select 1 from public.posts o where o.id=p.repost_of and o.deleted_at is null and o.status='published' and private.sera_audience_allows(o.author_id,o.audience,auth.uid()))));
$$;
create or replace function private.post_published(p_id uuid) returns boolean language sql stable security definer set search_path='' as $$select exists(select 1 from public.posts p where p.id=p_id and p.status='published' and private.can_read_post(p.id))$$;
drop policy posts_read on public.posts;
create policy posts_read on public.posts for select to authenticated using(deleted_at is null and (status='published' or author_id=auth.uid()) and private.sera_audience_to_me(author_id,audience) and (repost_of is null or private.can_read_post(repost_of)));
drop policy posts_edit on public.posts;
create policy posts_edit on public.posts for update to authenticated using(author_id=auth.uid() and deleted_at is null and private.account_verified()) with check(author_id=auth.uid() and deleted_at is null and private.account_verified());
-- Direct DELETE remains available for unpublished upload drafts, never recoverable posts.
drop policy posts_delete on public.posts;
create policy posts_delete on public.posts for delete to authenticated using(author_id=auth.uid() and status='draft' and deleted_at is null and published_at is null);
create or replace function private.reads_post_media(p_name text) returns boolean language sql stable security definer set search_path='' as $$select exists(select 1 from public.post_images i where i.path=p_name and private.post_published(i.post_id))$$;
-- Prevent editing image associations of posts in the bin via ordinary owner APIs.
create or replace function private.owns_post(p_id uuid) returns boolean language sql stable security definer set search_path='' as $$select auth.uid() is not null and exists(select 1 from public.posts where id=p_id and author_id=auth.uid() and deleted_at is null)$$;
create or replace function private.sera_guard_repost() returns trigger language plpgsql security definer set search_path='' as $$
declare source public.posts%rowtype;
begin
 if tg_op='UPDATE' and new.repost_of is distinct from old.repost_of then raise exception 'Original post cannot change';end if;
 if new.repost_of is null then if new.repost_note<>'' then raise exception 'Repost note requires an original';end if;return new;end if;
 if new.deleted_at is not null then return new;end if;
 select * into source from public.posts where id=new.repost_of;
 if not found or source.status<>'published' or source.deleted_at is not null or source.repost_of is not null or source.author_id=new.author_id or not private.sera_audience_allows(source.author_id,source.audience,new.author_id) or (tg_op='INSERT' and source.audience<>'public') then raise exception 'Original post unavailable for reposting';end if;
 return new;
end;$$;
-- A private operation, never callable directly by a member.
create table public.sera_post_media_cleanup(path text primary key,created_at timestamptz not null default now());
alter table public.sera_post_media_cleanup enable row level security;
revoke all on public.sera_post_media_cleanup from public,anon,authenticated;
grant select,delete on public.sera_post_media_cleanup to service_role;
create function private.sera_queue_post_media() returns trigger language plpgsql security definer set search_path='' as $$
begin
 insert into public.sera_post_media_cleanup(path) select i.path from public.post_images i where i.post_id=old.id and i.path like old.author_id::text||'/'||old.id::text||'/%' on conflict do nothing;
 return old;
end;$$;
revoke all on function private.sera_queue_post_media() from public,anon,authenticated;
create trigger sera_queue_deleted_media before delete on public.posts for each row execute function private.sera_queue_post_media();
create function public.sera_manage_post(p_id uuid,p_action text,p_audience text default null) returns void language plpgsql security definer set search_path='' as $$
declare p public.posts%rowtype;
begin
 if auth.uid() is null or not private.account_verified() then raise exception 'Verified account required';end if;
 select * into p from public.posts where id=p_id and author_id=auth.uid() for update;
 if not found then raise exception 'Post unavailable';end if;
 if p_action='trash' then
  if p.deleted_at is not null then return;end if;
  if p.status<>'published' then raise exception 'Published post required';end if;
  update public.posts set status='draft',deleted_at=now() where id=p_id;
 elsif p_action='restore' then
  if p.deleted_at is null or p.deleted_at<=now()-interval '30 days' then raise exception 'Recovery window expired';end if;
  update public.posts set status='published',deleted_at=null where id=p_id;
 elsif p_action='purge' then
  if p.deleted_at is null then raise exception 'Move post to bin first';end if;
  delete from public.posts where id=p_id;
 elsif p_action='privacy' then
  if p.deleted_at is not null or p.status<>'published' or p_audience is null or p_audience not in ('public','connections','followers','only_me') then raise exception 'Invalid audience';end if;
  update public.posts set audience=p_audience where id=p_id;
 else raise exception 'Invalid post action';end if;
end;$$;
create function public.sera_list_post_bin() returns table(id uuid,title text,body text,deleted_at timestamptz,expires_at timestamptz,audience text,published_at timestamptz) language sql stable security definer set search_path='' as $$
select p.id,case when p.repost_of is not null then 'Repost' else p.title end,case when p.repost_of is not null then p.repost_note else p.body end,p.deleted_at,p.deleted_at+interval '30 days',p.audience,p.published_at from public.posts p where p.author_id=auth.uid() and p.deleted_at>now()-interval '30 days' order by p.deleted_at desc limit 200;
$$;
create or replace function public.sera_undo_repost(p_original uuid) returns void language plpgsql security definer set search_path='' as $$
begin
 if auth.uid() is null then raise exception 'Sign in required';end if;
 update public.posts set status='draft',deleted_at=now() where repost_of=p_original and author_id=auth.uid() and status='published' and deleted_at is null;
end;$$;
-- Expired posts are unrecoverable immediately; scheduled cleanup purges bounded batches.
create function public.sera_purge_expired_posts() returns integer language plpgsql security definer set search_path='' as $$
declare n integer;
begin
 delete from public.posts where id in(select id from public.posts where deleted_at<=now()-interval '30 days' order by deleted_at limit 100 for update skip locked);
 get diagnostics n=row_count;return n;
end;$$;
revoke all on function public.sera_manage_post(uuid,text,text),public.sera_list_post_bin(),public.sera_purge_expired_posts() from public,anon,authenticated;
grant execute on function public.sera_manage_post(uuid,text,text),public.sera_list_post_bin() to authenticated;
grant execute on function public.sera_purge_expired_posts() to service_role;
-- Reposting after undo restores the same recoverable record; no duplicate is created.
create function public.sera_restore_own_repost(p_original uuid) returns uuid language plpgsql security definer set search_path='' as $$
declare v uuid;
begin
 if auth.uid() is null or not private.account_verified() then raise exception 'Verified account required';end if;
 select id into v from public.posts where author_id=auth.uid() and repost_of=p_original and deleted_at>now()-interval '30 days' for update;
 if v is not null then perform public.sera_manage_post(v,'restore');end if;
 return v;
end;$$;
revoke all on function public.sera_restore_own_repost(uuid) from public,anon;
grant execute on function public.sera_restore_own_repost(uuid) to authenticated;
