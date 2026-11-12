alter table public.sera_community_posts add column pinned_at timestamptz, add column removed_at timestamptz;
update public.sera_community_posts set pinned_at=created_at where pinned;
create table public.sera_community_moderation_log(id uuid primary key default gen_random_uuid(),community_id uuid not null references public.sera_communities(id) on delete cascade,post_id uuid not null,actor_id uuid not null references auth.users(id) on delete cascade,action text not null check(action in ('pin','unpin','delete','restore')),created_at timestamptz not null default now());
alter table public.sera_community_moderation_log enable row level security;
revoke all on public.sera_community_moderation_log from public,anon,authenticated;
grant select on public.sera_community_moderation_log to authenticated;
create policy community_log_owner on public.sera_community_moderation_log for select to authenticated using(exists(select 1 from public.sera_communities c where c.id=community_id and c.owner_id=auth.uid()));
drop policy community_member_posts on public.sera_community_posts;
create policy community_member_posts on public.sera_community_posts for select to authenticated using(
 exists(select 1 from public.sera_community_members m where m.community_id=sera_community_posts.community_id and m.member_id=auth.uid())
 and (removed_at is null or exists(select 1 from public.sera_communities c where c.id=community_id and c.owner_id=auth.uid()))
 and not exists(select 1 from public.professional_blocks b where (b.blocker_id=auth.uid() and b.blocked_id=author_id) or (b.blocker_id=author_id and b.blocked_id=auth.uid())));
create or replace function public.sera_manage_community_post(p_id uuid,p_action text) returns void language plpgsql security definer set search_path='' as $$
declare p public.sera_community_posts%rowtype; owner uuid; u uuid:=auth.uid();
begin
 if u is null then raise exception 'Authentication required'; end if;
 select * into p from public.sera_community_posts where id=p_id;
 if not found then raise exception 'Post unavailable'; end if;
 select owner_id into owner from public.sera_communities where id=p.community_id for update;
 select * into p from public.sera_community_posts where id=p_id for update;
 if p_action='delete' and (u=owner or u=p.author_id) then
 if p.removed_at is not null then return; end if;
 update public.sera_community_posts set removed_at=now(),pinned=false,pinned_at=null where id=p_id;
 elsif p_action='restore' and u=owner then
 if p.removed_at is null then return; end if;
 update public.sera_community_posts set removed_at=null where id=p_id;
 elsif p_action in ('pin','unpin') and u=owner and p.removed_at is null then
 if p.pinned=(p_action='pin') then return; end if;
 if p_action='pin' and (select count(*) from public.sera_community_posts where community_id=p.community_id and pinned and removed_at is null)>=3 then raise exception 'Unpin a discussion first: maximum three pins'; end if;
 update public.sera_community_posts set pinned=(p_action='pin'),pinned_at=case when p_action='pin' then now() else null end where id=p_id;
 else raise exception 'Action not permitted'; end if;
 insert into public.sera_community_moderation_log(community_id,post_id,actor_id,action) values(p.community_id,p_id,u,p_action);
end; $$;
