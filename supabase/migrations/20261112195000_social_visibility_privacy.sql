-- Only expose a viewer's own block visibility, not arbitrary block pairs.
create function private.sera_social_visible_to_me(p_author uuid) returns boolean language sql stable security definer set search_path='' as $$select private.sera_social_visible(auth.uid(),p_author)$$;
revoke all on function private.sera_social_visible_to_me(uuid) from public,anon,authenticated;
grant execute on function private.sera_social_visible_to_me(uuid) to authenticated;
drop policy post_comments_read on public.post_comments;
create policy post_comments_read on public.post_comments for select to authenticated using(private.can_read_post(post_id) and private.sera_social_visible_to_me(author_id));
revoke execute on function private.sera_social_visible(uuid,uuid) from authenticated;
create or replace function public.sera_feed_headlines(p_ids uuid[]) returns table(id uuid,headline text) language sql stable security definer set search_path='' as $$
select p.id,p.headline from public.profiles p where p.id=any(p_ids[1:100]) and private.sera_social_visible(auth.uid(),p.id) and (p.id=auth.uid() or exists(select 1 from public.posts x where x.author_id=p.id and x.status='published' and private.can_read_post(x.id)) or exists(select 1 from public.post_comments c where c.author_id=p.id and private.can_read_post(c.post_id)));
$$;