create or replace function private.social_authors()
returns table(id uuid,full_name text,headline text,location text)
language sql stable security definer set search_path='' as $$
 select p.id,p.full_name,p.headline,p.location from public.profiles p
 where auth.uid() is not null and private.sera_social_visible(auth.uid(),p.id) and (
 exists(select 1 from public.posts x where x.author_id=p.id and private.post_published(x.id))
 or exists(select 1 from public.post_comments c where c.author_id=p.id and private.post_published(c.post_id))
 )
$$;
revoke all on function private.social_authors() from public,anon,authenticated;
grant execute on function private.social_authors() to authenticated;
create or replace view public.post_authors with (security_invoker=true) as select id,full_name,headline,location from private.social_authors();
revoke all on public.post_authors from public,anon,authenticated;
grant select on public.post_authors to authenticated;
