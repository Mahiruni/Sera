alter table public.sera_community_posts add column pinned boolean not null default false;
create function public.sera_manage_community(p_id uuid,p_name text,p_description text,p_topic text) returns void language plpgsql security definer set search_path='' as $$
begin
 update public.sera_communities set name=trim(p_name),description=trim(coalesce(p_description,'')),topic=p_topic where id=p_id and owner_id=auth.uid();
 if not found then raise exception 'Owner access required'; end if;
end; $$;
create function public.sera_manage_community_post(p_id uuid,p_action text) returns void language plpgsql security definer set search_path='' as $$
declare p public.sera_community_posts%rowtype; owner uuid;
begin
 select * into p from public.sera_community_posts where id=p_id;
 if not found or auth.uid() is null then raise exception 'Post unavailable'; end if;
 select owner_id into owner from public.sera_communities where id=p.community_id;
 if p_action='delete' and (auth.uid()=owner or auth.uid()=p.author_id) then delete from public.sera_community_posts where id=p_id;
 elsif p_action in ('pin','unpin') and auth.uid()=owner then update public.sera_community_posts set pinned=(p_action='pin') where id=p_id;
 else raise exception 'Action not permitted'; end if;
end; $$;
create function public.sera_recommended_communities() returns table(id uuid,reason text) language sql stable security definer set search_path='' as $$
 select c.id,'Matches your interests'::text from public.sera_communities c join public.profiles p on p.id=auth.uid()
 where not exists(select 1 from public.sera_community_members m where m.community_id=c.id and m.member_id=auth.uid())
 and (strpos(lower(coalesce(p.skills,'')||' '||coalesce(p.headline,'')||' '||coalesce(p.preferred_roles,'')),c.topic)>0)
 order by c.created_at desc limit 12;
$$;
revoke all on function public.sera_manage_community(uuid,text,text,text),public.sera_manage_community_post(uuid,text),public.sera_recommended_communities() from public,anon;
grant execute on function public.sera_manage_community(uuid,text,text,text),public.sera_manage_community_post(uuid,text),public.sera_recommended_communities() to authenticated;
