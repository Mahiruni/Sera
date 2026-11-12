-- Professional communities: authenticated directory, membership and discussions.
create table public.sera_communities (
 id uuid primary key default gen_random_uuid(), owner_id uuid not null references auth.users(id) on delete cascade,
 name text not null check(char_length(trim(name)) between 3 and 70),
 description text not null default '' check(char_length(description)<=500),
 topic text not null check(topic in ('technology','business','design','education','careers','other')),
 created_at timestamptz not null default now()
);
create unique index sera_communities_name_unique on public.sera_communities(lower(trim(name)));
create table public.sera_community_members (
 community_id uuid not null references public.sera_communities(id) on delete cascade,
 member_id uuid not null references auth.users(id) on delete cascade,
 joined_at timestamptz not null default now(), primary key(community_id,member_id)
);
create table public.sera_community_posts (
 id uuid primary key default gen_random_uuid(), community_id uuid not null references public.sera_communities(id) on delete cascade,
 author_id uuid not null references auth.users(id) on delete cascade,
 body text not null check(char_length(trim(body)) between 1 and 2000), created_at timestamptz not null default now()
);
create index sera_community_posts_recent on public.sera_community_posts(community_id,created_at desc);
alter table public.sera_communities enable row level security;
alter table public.sera_community_members enable row level security;
alter table public.sera_community_posts enable row level security;
revoke all on public.sera_communities,public.sera_community_members,public.sera_community_posts from public,anon,authenticated;
grant select on public.sera_communities,public.sera_community_members,public.sera_community_posts to authenticated;
create policy community_directory on public.sera_communities for select to authenticated using(auth.uid() is not null);
create policy community_own_membership on public.sera_community_members for select to authenticated using(member_id=auth.uid());
create policy community_member_posts on public.sera_community_posts for select to authenticated using(
 exists(select 1 from public.sera_community_members m where m.community_id=sera_community_posts.community_id and m.member_id=auth.uid())
 and not exists(select 1 from public.professional_blocks b where (b.blocker_id=auth.uid() and b.blocked_id=author_id) or (b.blocker_id=author_id and b.blocked_id=auth.uid()))
);
create function public.sera_community_directory(p_query text default '',p_topic text default '') returns table(id uuid,name text,description text,topic text,member_count bigint,joined boolean,is_owner boolean)
language sql stable security definer set search_path='' as $$
 select c.id,c.name,c.description,c.topic,(select count(*) from public.sera_community_members m where m.community_id=c.id),
 exists(select 1 from public.sera_community_members m where m.community_id=c.id and m.member_id=auth.uid()),c.owner_id=auth.uid()
 from public.sera_communities c where auth.uid() is not null
 and (coalesce(p_query,'')='' or strpos(lower(c.name||' '||c.description),lower(left(p_query,80)))>0)
 and (coalesce(p_topic,'')='' or c.topic=p_topic)
 order by c.created_at desc limit 60;
$$;
create function public.sera_create_community(p_name text,p_description text,p_topic text) returns uuid language plpgsql security definer set search_path='' as $$
declare v uuid;
begin
 if auth.uid() is null or not exists(select 1 from auth.users where id=auth.uid() and email_confirmed_at is not null) then raise exception 'Verified account required'; end if;
 if (select count(*) from public.sera_communities where owner_id=auth.uid())>=20 then raise exception 'Community creation limit reached'; end if;
 insert into public.sera_communities(owner_id,name,description,topic) values(auth.uid(),trim(p_name),trim(coalesce(p_description,'')),p_topic) returning id into v;
 insert into public.sera_community_members(community_id,member_id) values(v,auth.uid()); return v;
end; $$;
create function public.sera_join_community(p_id uuid,p_join boolean) returns void language plpgsql security definer set search_path='' as $$
begin
 if auth.uid() is null or not exists(select 1 from auth.users where id=auth.uid() and email_confirmed_at is not null) then raise exception 'Verified account required'; end if;
 if p_join then insert into public.sera_community_members(community_id,member_id) values(p_id,auth.uid()) on conflict do nothing;
 else
 if exists(select 1 from public.sera_communities where id=p_id and owner_id=auth.uid()) then raise exception 'Community owner cannot leave'; end if;
 delete from public.sera_community_members where community_id=p_id and member_id=auth.uid(); end if;
end; $$;
create function public.sera_publish_community_post(p_id uuid,p_body text) returns uuid language plpgsql security definer set search_path='' as $$
declare v uuid;
begin
 if auth.uid() is null or not exists(select 1 from auth.users where id=auth.uid() and email_confirmed_at is not null) then raise exception 'Verified account required'; end if;
 if not exists(select 1 from public.sera_community_members where community_id=p_id and member_id=auth.uid()) then raise exception 'Join this community first'; end if;
 if (select count(*) from public.sera_community_posts where author_id=auth.uid() and created_at>now()-interval '1 hour')>=30 then raise exception 'Post limit reached'; end if;
 insert into public.sera_community_posts(community_id,author_id,body) values(p_id,auth.uid(),trim(p_body)) returning id into v;return v;
end; $$;
revoke all on function public.sera_community_directory(text,text),public.sera_create_community(text,text,text),public.sera_join_community(uuid,boolean),public.sera_publish_community_post(uuid,text) from public,anon;
grant execute on function public.sera_community_directory(text,text),public.sera_create_community(text,text,text),public.sera_join_community(uuid,boolean),public.sera_publish_community_post(uuid,text) to authenticated;
