-- SERA social feed, phase 1: posts, images, likes, comments, saves, shares.
-- Members-only network: every read requires a signed-in account; every write requires
-- a verified email (private.account_verified) and ownership. Counters are maintained by
-- triggers and are never writable by clients. Saves, likes and shares are private rows.

-- ---------------------------------------------------------------- posts
create table public.posts (
  id uuid primary key default gen_random_uuid(),
  author_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  kind text not null check (kind in ('idea','photos','quote','talent','project','achievement','question')),
  status text not null default 'draft' check (status in ('draft','published')),
  title text not null default '' check (char_length(title)<=140),
  body text not null default '' check (char_length(body)<=10000),
  quote_source text not null default '' check (char_length(quote_source)<=120),
  category text not null default '' check (char_length(category)<=60),
  location text not null default '' check (char_length(location)<=120),
  skills text[] not null default '{}' check (cardinality(skills)<=10 and char_length(array_to_string(skills,','))<=400),
  project_url text check (project_url is null or (char_length(project_url)<=500 and project_url ~* '^https://[^[:space:]]+$')),
  like_count integer not null default 0 check (like_count>=0),
  comment_count integer not null default 0 check (comment_count>=0),
  share_count integer not null default 0 check (share_count>=0),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  published_at timestamptz,
  edited_at timestamptz,
  check (status='draft' or published_at is not null),
  check (status='draft' or kind='quote' or char_length(btrim(title))>=3),
  check (status='draft' or kind<>'quote' or char_length(btrim(body)) between 3 and 600)
);
create index posts_feed on public.posts(published_at desc,id desc) where status='published';
create index posts_author on public.posts(author_id,created_at desc);
create index posts_kind_feed on public.posts(kind,published_at desc) where status='published';
create index posts_skills on public.posts using gin(skills);

-- Helpers are defined after the table: SQL-language functions are validated at creation.
create or replace function private.owns_post(p_id uuid) returns boolean language sql stable security definer set search_path='' as $$
  select auth.uid() is not null and exists(select 1 from public.posts where id=p_id and author_id=(select auth.uid()))
$$;
create or replace function private.can_read_post(p_id uuid) returns boolean language sql stable security definer set search_path='' as $$
  select auth.uid() is not null and exists(select 1 from public.posts where id=p_id and (status='published' or author_id=(select auth.uid())))
$$;
create or replace function private.post_published(p_id uuid) returns boolean language sql stable security definer set search_path='' as $$
  select exists(select 1 from public.posts where id=p_id and status='published')
$$;
revoke all on function private.owns_post(uuid),private.can_read_post(uuid),private.post_published(uuid) from public,anon,authenticated;
grant execute on function private.owns_post(uuid),private.can_read_post(uuid),private.post_published(uuid) to authenticated;

alter table public.posts enable row level security;
revoke all on public.posts from anon,authenticated;
grant select on public.posts to authenticated;
-- Counters, author_id, kind, status timestamps are deliberately outside the client grants.
grant insert(kind,status,title,body,quote_source,category,location,skills,project_url) on public.posts to authenticated;
grant update(status,title,body,quote_source,category,location,skills,project_url) on public.posts to authenticated;
grant delete on public.posts to authenticated;
create policy posts_read on public.posts for select to authenticated using (status='published' or author_id=(select auth.uid()));
create policy posts_create on public.posts for insert to authenticated with check (author_id=(select auth.uid()) and (select private.account_verified()));
create policy posts_edit on public.posts for update to authenticated using (author_id=(select auth.uid()) and (select private.account_verified())) with check (author_id=(select auth.uid()) and (select private.account_verified()));
create policy posts_delete on public.posts for delete to authenticated using (author_id=(select auth.uid()));

-- Fires only when content columns are in the SET list, so counter updates never touch timestamps.
create function private.guard_post() returns trigger language plpgsql security definer set search_path='' as $$
begin
  if auth.uid() is null then raise exception 'Verified account required'; end if;
  if tg_op='INSERT' then
    if new.author_id<>auth.uid() then raise exception 'Ownership mismatch'; end if;
    if new.status='published' and new.kind='photos' then raise exception 'Photo posts are saved as a draft, given images, then published'; end if;
    if (select count(*) from public.posts where author_id=new.author_id and created_at>now()-interval '1 day')>=30 then raise exception 'Daily post limit reached'; end if;
    new.published_at := case when new.status='published' then now() end;
    new.edited_at := null;
  else
    if new.author_id<>old.author_id or new.kind<>old.kind then raise exception 'Author and post type cannot change'; end if;
    if new.status='published' then
      if old.status='draft' and new.kind='photos' and not exists(select 1 from public.post_images where post_id=new.id) then
        raise exception 'Add at least one image before publishing';
      end if;
      new.published_at := coalesce(old.published_at,now());
    else
      new.published_at := old.published_at;
    end if;
    if old.status='published' and new.status='published'
       and (old.title,old.body,old.quote_source,old.category,old.location,old.skills,old.project_url)
           is distinct from (new.title,new.body,new.quote_source,new.category,new.location,new.skills,new.project_url) then
      new.edited_at := now();
    end if;
  end if;
  new.updated_at := now();
  return new;
end $$;
revoke all on function private.guard_post() from public,anon,authenticated;
create trigger posts_guard_insert before insert on public.posts for each row execute function private.guard_post();
create trigger posts_guard_update before update of author_id,kind,status,title,body,quote_source,category,location,skills,project_url on public.posts for each row execute function private.guard_post();

-- ---------------------------------------------------------------- images
create table public.post_images (
  id uuid primary key default gen_random_uuid(),
  post_id uuid not null references public.posts(id) on delete cascade,
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  path text not null check (char_length(path)<=300),
  alt text not null default '' check (char_length(alt)<=200),
  sort_order smallint not null default 0 check (sort_order between 0 and 9),
  width integer check (width between 1 and 8000),
  height integer check (height between 1 and 8000),
  created_at timestamptz not null default now(),
  check (path like user_id::text||'/'||post_id::text||'/%'),
  unique(path),
  unique(post_id,sort_order) deferrable initially deferred
);
create index post_images_post on public.post_images(post_id,sort_order);
alter table public.post_images enable row level security;
revoke all on public.post_images from anon,authenticated;
grant select on public.post_images to authenticated;
grant insert(post_id,path,alt,sort_order,width,height) on public.post_images to authenticated;
grant update(alt,sort_order) on public.post_images to authenticated;
grant delete on public.post_images to authenticated;
create policy post_images_read on public.post_images for select to authenticated using (private.can_read_post(post_id));
create policy post_images_create on public.post_images for insert to authenticated with check (user_id=(select auth.uid()) and private.owns_post(post_id) and (select private.account_verified()));
create policy post_images_edit on public.post_images for update to authenticated using (private.owns_post(post_id) and (select private.account_verified())) with check (private.owns_post(post_id) and (select private.account_verified()));
create policy post_images_delete on public.post_images for delete to authenticated using (private.owns_post(post_id));

create function private.guard_post_image_delete() returns trigger language plpgsql security definer set search_path='' as $$
begin
  -- During a post delete the parent row is already gone, so cascades pass.
  if exists(select 1 from public.posts p where p.id=old.post_id and p.kind='photos' and p.status='published')
     and not exists(select 1 from public.post_images i where i.post_id=old.post_id and i.id<>old.id) then
    raise exception 'A published photo post needs at least one image';
  end if;
  return old;
end $$;
revoke all on function private.guard_post_image_delete() from public,anon,authenticated;
create trigger post_images_guard_delete before delete on public.post_images for each row execute function private.guard_post_image_delete();

-- ---------------------------------------------------------------- likes, saves, shares (private rows)
create table public.post_likes (post_id uuid not null references public.posts(id) on delete cascade, user_id uuid not null default auth.uid() references auth.users(id) on delete cascade, created_at timestamptz not null default now(), primary key(post_id,user_id));
create index post_likes_user on public.post_likes(user_id,created_at desc);
create table public.post_saves (post_id uuid not null references public.posts(id) on delete cascade, user_id uuid not null default auth.uid() references auth.users(id) on delete cascade, created_at timestamptz not null default now(), primary key(post_id,user_id));
create index post_saves_user on public.post_saves(user_id,created_at desc);
create table public.post_shares (post_id uuid not null references public.posts(id) on delete cascade, user_id uuid not null default auth.uid() references auth.users(id) on delete cascade, created_at timestamptz not null default now(), primary key(post_id,user_id));

alter table public.post_likes enable row level security;
alter table public.post_saves enable row level security;
alter table public.post_shares enable row level security;
revoke all on public.post_likes,public.post_saves,public.post_shares from anon,authenticated;
grant select,delete on public.post_likes,public.post_saves to authenticated;
grant insert(post_id) on public.post_likes,public.post_saves,public.post_shares to authenticated;
grant select on public.post_shares to authenticated;
-- Only your own rows are readable: "liked by me" and "saved by me" never reveal other people.
create policy post_likes_read on public.post_likes for select to authenticated using (user_id=(select auth.uid()));
create policy post_likes_create on public.post_likes for insert to authenticated with check (user_id=(select auth.uid()) and (select private.account_verified()) and private.post_published(post_id));
create policy post_likes_delete on public.post_likes for delete to authenticated using (user_id=(select auth.uid()));
create policy post_saves_read on public.post_saves for select to authenticated using (user_id=(select auth.uid()));
create policy post_saves_create on public.post_saves for insert to authenticated with check (user_id=(select auth.uid()) and (select private.account_verified()) and private.post_published(post_id));
create policy post_saves_delete on public.post_saves for delete to authenticated using (user_id=(select auth.uid()));
create policy post_shares_read on public.post_shares for select to authenticated using (user_id=(select auth.uid()));
create policy post_shares_create on public.post_shares for insert to authenticated with check (user_id=(select auth.uid()) and (select private.account_verified()) and private.post_published(post_id));

-- ---------------------------------------------------------------- comments and one level of replies
create table public.post_comments (
  id uuid primary key default gen_random_uuid(),
  post_id uuid not null references public.posts(id) on delete cascade,
  author_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  parent_id uuid references public.post_comments(id) on delete cascade,
  body text not null check (char_length(btrim(body)) between 1 and 2000),
  created_at timestamptz not null default now(),
  edited_at timestamptz
);
create index post_comments_thread on public.post_comments(post_id,created_at);
create index post_comments_parent on public.post_comments(parent_id) where parent_id is not null;
create index post_comments_author on public.post_comments(author_id,created_at desc);
alter table public.post_comments enable row level security;
revoke all on public.post_comments from anon,authenticated;
grant select,delete on public.post_comments to authenticated;
grant insert(post_id,parent_id,body) on public.post_comments to authenticated;
grant update(body) on public.post_comments to authenticated;
create policy post_comments_read on public.post_comments for select to authenticated using (private.can_read_post(post_id));
create policy post_comments_create on public.post_comments for insert to authenticated with check (author_id=(select auth.uid()) and (select private.account_verified()) and private.post_published(post_id));
create policy post_comments_edit on public.post_comments for update to authenticated using (author_id=(select auth.uid()) and (select private.account_verified())) with check (author_id=(select auth.uid()) and (select private.account_verified()));
-- Authors remove their own comments; a post's owner may remove any comment on it.
create policy post_comments_delete on public.post_comments for delete to authenticated using (author_id=(select auth.uid()) or private.owns_post(post_id));

create function private.guard_comment() returns trigger language plpgsql security definer set search_path='' as $$
begin
  if auth.uid() is null then raise exception 'Verified account required'; end if;
  if tg_op='INSERT' then
    if new.author_id<>auth.uid() then raise exception 'Ownership mismatch'; end if;
    if new.parent_id is not null and not exists(select 1 from public.post_comments c where c.id=new.parent_id and c.post_id=new.post_id and c.parent_id is null) then
      raise exception 'Replies attach to a top-level comment on the same post';
    end if;
    if (select count(*) from public.post_comments where author_id=new.author_id and created_at>now()-interval '1 hour')>=120 then raise exception 'Comment limit reached'; end if;
    new.edited_at := null;
  else
    if btrim(new.body) is distinct from btrim(old.body) then new.edited_at := now(); end if;
  end if;
  return new;
end $$;
revoke all on function private.guard_comment() from public,anon,authenticated;
create trigger post_comments_guard_insert before insert on public.post_comments for each row execute function private.guard_comment();
create trigger post_comments_guard_update before update of body on public.post_comments for each row execute function private.guard_comment();

-- ---------------------------------------------------------------- counters
create function private.bump_post_counter() returns trigger language plpgsql security definer set search_path='' as $$
declare delta integer := case tg_op when 'INSERT' then 1 else -1 end; pid uuid := coalesce(new.post_id,old.post_id);
begin
  if tg_table_name='post_likes' then update public.posts set like_count=greatest(like_count+delta,0) where id=pid;
  elsif tg_table_name='post_comments' then update public.posts set comment_count=greatest(comment_count+delta,0) where id=pid;
  elsif tg_table_name='post_shares' then update public.posts set share_count=greatest(share_count+delta,0) where id=pid;
  end if;
  return null;
end $$;
revoke all on function private.bump_post_counter() from public,anon,authenticated;
create trigger post_likes_count after insert or delete on public.post_likes for each row execute function private.bump_post_counter();
create trigger post_comments_count after insert or delete on public.post_comments for each row execute function private.bump_post_counter();
create trigger post_shares_count after insert on public.post_shares for each row execute function private.bump_post_counter();

-- ---------------------------------------------------------------- author cards
-- profiles stay owner-readable. This view exposes only a public-facing subset, and only for
-- members who have a published post or a comment, so the feed can show who wrote what.
create view public.post_authors as
  select p.id,p.full_name,p.headline,p.location
  from public.profiles p
  where exists(select 1 from public.posts x where x.author_id=p.id and x.status='published')
     or exists(select 1 from public.post_comments c where c.author_id=p.id);
revoke all on public.post_authors from public,anon,authenticated;
grant select on public.post_authors to authenticated;

-- ---------------------------------------------------------------- image storage
-- Drafts stay private to their owner; once attached to a published post, any member may read.
create function private.reads_post_media(p_name text) returns boolean language sql stable security definer set search_path='' as $$
  select auth.uid() is not null and exists(
    select 1 from public.post_images i join public.posts p on p.id=i.post_id
    where i.path=p_name and p.status='published')
$$;
revoke all on function private.reads_post_media(text) from public,anon,authenticated;
grant execute on function private.reads_post_media(text) to authenticated;
insert into storage.buckets(id,name,public,file_size_limit,allowed_mime_types)
  values('sera-post-media','sera-post-media',false,8388608,array['image/jpeg','image/png','image/webp']);
create policy post_media_read on storage.objects for select to authenticated using (bucket_id='sera-post-media' and ((storage.foldername(name))[1]=(select auth.uid())::text or private.reads_post_media(name)));
create policy post_media_insert on storage.objects for insert to authenticated with check (bucket_id='sera-post-media' and (storage.foldername(name))[1]=(select auth.uid())::text and name ~ '^[0-9a-f-]{36}/[0-9a-f-]{36}/[A-Za-z0-9._-]{1,120}$' and (select private.account_verified()));
create policy post_media_delete on storage.objects for delete to authenticated using (bucket_id='sera-post-media' and (storage.foldername(name))[1]=(select auth.uid())::text);
