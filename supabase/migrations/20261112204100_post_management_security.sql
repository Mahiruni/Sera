-- Harden the recovery and audience rules across existing interaction paths.
revoke insert on public.posts from authenticated;
grant insert(id,author_id,kind,status,title,body,quote_source,category,location,skills,project_url,repost_of,repost_note,audience) on public.posts to authenticated;
grant update(repost_note) on public.posts to authenticated;
create or replace function private.sera_post_readable_by(p_id uuid,p_viewer uuid,p_published boolean default false) returns boolean language sql stable security definer set search_path='' as $$
select p_viewer is not null and exists(select 1 from public.posts p where p.id=p_id and p.deleted_at is null and (p.status='published' or (not p_published and p.author_id=p_viewer)) and private.sera_audience_allows(p.author_id,p.audience,p_viewer) and (p.repost_of is null or exists(select 1 from public.posts o where o.id=p.repost_of and o.deleted_at is null and o.status='published' and private.sera_audience_allows(o.author_id,o.audience,p_viewer))));
$$;
revoke all on function private.sera_post_readable_by(uuid,uuid,boolean) from public,anon,authenticated;
create or replace function private.can_read_post(p_id uuid) returns boolean language sql stable security definer set search_path='' as $$select private.sera_post_readable_by(p_id,auth.uid(),false)$$;
create or replace function private.post_published(p_id uuid) returns boolean language sql stable security definer set search_path='' as $$select private.sera_post_readable_by(p_id,auth.uid(),true)$$;
create or replace function private.guard_comment() returns trigger language plpgsql security definer set search_path='' as $$
declare u uuid:=auth.uid();
begin
 if u is null then
  if current_setting('role')<>'service_role' or not exists(select 1 from auth.users where id=new.author_id and email_confirmed_at is not null) then raise exception 'Verified account required';end if;
  u:=new.author_id;
 end if;
 if new.author_id<>u then raise exception 'Ownership mismatch';end if;
 if not private.sera_post_readable_by(new.post_id,u,true) then raise exception 'Post unavailable';end if;
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
create or replace function public.sera_repost_states(p_ids uuid[]) returns table(id uuid,repost_count bigint,reposted boolean) language sql stable security definer set search_path='' as $$
select p.id,(select count(*) from public.posts r where r.repost_of=p.id and private.sera_post_readable_by(r.id,auth.uid(),true)),exists(select 1 from public.posts r where r.repost_of=p.id and r.author_id=auth.uid() and r.status='published' and r.deleted_at is null)
from public.posts p where p.id=any(p_ids[1:100]) and p.repost_of is null and private.post_published(p.id);
$$;
-- Private member metadata must not be derived from inaccessible posts.
create or replace view public.post_authors as
select p.id,p.full_name,p.headline,p.location from public.profiles p where auth.uid() is not null and private.sera_social_visible(auth.uid(),p.id) and
(exists(select 1 from public.posts x where x.author_id=p.id and private.post_published(x.id)) or exists(select 1 from public.post_comments c where c.author_id=p.id and private.post_published(c.post_id)));
-- Recovery must work even when moderation is enabled, without allowing edits to bypass it.
create or replace function private.moderation_publication_gate() returns trigger language plpgsql security definer set search_path='' as $$
declare enabled boolean; unchanged boolean;
begin
 select enforce_publication_gate into enabled from private.moderation_configuration where singleton=true;
 if not coalesce(enabled,false) or auth.role()='service_role' then return new;end if;
 if tg_op='INSERT' and new.status<>'draft' then raise exception 'Only draft posts may be created directly';end if;
 if tg_op='UPDATE' then
  unchanged := (new.title,new.body,new.quote_source,new.category,new.location,new.skills,new.project_url,new.kind,new.repost_note) is not distinct from (old.title,old.body,old.quote_source,old.category,old.location,old.skills,old.project_url,old.kind,old.repost_note);
  if unchanged and ((old.deleted_at is null and new.deleted_at is not null and old.status='published' and new.status='draft') or (old.deleted_at is not null and new.deleted_at is null and old.status='draft' and new.status='published')) then return new;end if;
  if new.status is distinct from old.status or not unchanged then raise exception 'Publishing and content changes require server moderation';end if;
 end if;
 return new;
end;$$;
create function private.sera_mark_repost_edit() returns trigger language plpgsql security definer set search_path='' as $$
begin
 if new.repost_note is distinct from old.repost_note then new.edited_at:=now();new.updated_at:=now();end if;
 return new;
end;$$;
revoke all on function private.sera_mark_repost_edit() from public,anon,authenticated;
create trigger sera_mark_repost_edit before update of repost_note on public.posts for each row execute function private.sera_mark_repost_edit();
create or replace function private.sera_notify_repost() returns trigger language plpgsql security definer set search_path='' as $$
declare owner_id uuid;
begin
 if new.repost_of is not null and new.status='published' then
  if tg_op='UPDATE' and (old.status='published' or old.deleted_at is not null) then return new;end if;
  select author_id into owner_id from public.posts where id=new.repost_of and status='published' and deleted_at is null;
  if owner_id is not null and owner_id<>new.author_id and private.sera_post_readable_by(new.repost_of,new.author_id,true) then insert into public.professional_notifications(recipient_id,actor_id,kind,post_id) values(owner_id,new.author_id,'repost',new.repost_of);end if;
 end if;
 return new;
end;$$;
create or replace function public.sera_manage_post(p_id uuid,p_action text,p_audience text default null) returns void language plpgsql security definer set search_path='' as $$
declare p public.posts%rowtype; latest_decision text;
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
  select action into latest_decision from public.moderation_decisions where post_id=p_id order by created_at desc,id desc limit 1;
  if latest_decision in ('quarantine','remove') then raise exception 'This post is under moderation review';end if;
  update public.posts set status='published',deleted_at=null where id=p_id;
 elsif p_action='purge' then
  if p.deleted_at is null then raise exception 'Move post to bin first';end if;
  delete from public.posts where id=p_id;
 elsif p_action='privacy' then
  if p.deleted_at is not null or p.status<>'published' or p_audience is null or p_audience not in ('public','connections','followers','only_me') then raise exception 'Invalid audience';end if;
  update public.posts set audience=p_audience where id=p_id;
 else raise exception 'Invalid post action';end if;
end;$$;
-- Keep draft uploads deletable during moderation cascades.
create or replace function private.moderation_image_gate() returns trigger language plpgsql security definer set search_path='' as $$
declare enabled boolean; target_post uuid; current_status text;
begin
 select enforce_publication_gate into enabled from private.moderation_configuration where singleton=true;
 if not coalesce(enabled,false) or auth.role()='service_role' then return case when tg_op='DELETE' then old else new end;end if;
 target_post:=case when tg_op='DELETE' then old.post_id else new.post_id end;
 select status into current_status from public.posts where id=target_post;
 if tg_op='DELETE' and current_status is null then return old;end if;
 if current_status is distinct from 'draft' then raise exception 'Media changes to published posts require moderation';end if;
 return case when tg_op='DELETE' then old else new end;
end;$$;
-- Prevent deletion of a recoverable media object through the storage API.
create function private.sera_media_owner_can_delete(p_path text) returns boolean language sql stable security definer set search_path='' as $$
select auth.uid() is not null and split_part(p_path,'/',1)=auth.uid()::text and not exists(select 1 from public.post_images i join public.posts p on p.id=i.post_id where i.path=p_path and p.deleted_at is not null);
$$;
revoke all on function private.sera_media_owner_can_delete(text) from public,anon;
grant execute on function private.sera_media_owner_can_delete(text) to authenticated;
drop policy post_media_delete on storage.objects;
create policy post_media_delete on storage.objects for delete to authenticated using(bucket_id='sera-post-media' and private.sera_media_owner_can_delete(name));
-- New reposts can replace an earlier undo without a unique-index failure.
create or replace function public.sera_restore_own_repost(p_original uuid) returns uuid language plpgsql security definer set search_path='' as $$
declare v uuid; expiry timestamptz;
begin
 if auth.uid() is null or not private.account_verified() then raise exception 'Verified account required';end if;
 if not exists(select 1 from public.posts where id=p_original and audience='public' and private.post_published(id)) then raise exception 'Original post unavailable';end if;
 select id,deleted_at into v,expiry from public.posts where author_id=auth.uid() and repost_of=p_original and deleted_at is not null for update;
 if v is null then return null;end if;
 if expiry<=now()-interval '30 days' then delete from public.posts where id=v;return null;end if;
 perform public.sera_manage_post(v,'restore');return v;
end;$$;
-- Authenticated native edit uses the same database publication gate as existing direct writes.
create function public.sera_edit_post_text(p_id uuid,p_title text,p_body text,p_note text default null) returns void language plpgsql security invoker set search_path='' as $$
declare p public.posts%rowtype;
begin
 select * into p from public.posts where id=p_id and author_id=auth.uid() and status='published' and deleted_at is null for update;
 if not found or not private.account_verified() then raise exception 'Post unavailable';end if;
 if p.repost_of is not null then
  if p_note is null or char_length(p_note)>400 then raise exception 'Invalid repost commentary';end if;
  update public.posts set repost_note=p_note where id=p_id;
 else
  if p_title is null or p_body is null or char_length(p_title)>140 or char_length(p_body)>10000 or (p.kind<>'quote' and char_length(btrim(p_title))<3) or (p.kind='quote' and char_length(btrim(p_body)) not between 3 and 600) or (p.kind not in ('quote','photos') and btrim(p_body)='') then raise exception 'Check your post text';end if;
  update public.posts set title=p_title,body=p_body where id=p_id;
 end if;
end;$$;
revoke all on function public.sera_edit_post_text(uuid,text,text,text) from public,anon;
grant execute on function public.sera_edit_post_text(uuid,text,text,text) to authenticated;
