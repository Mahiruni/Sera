-- Evaluate the row itself in SELECT policies: an INSERT ... RETURNING row
-- is not visible to a separate STABLE query of posts until the next statement.
create or replace function private.can_read_post(p_id uuid) returns boolean language sql stable security definer set search_path='' as $$
select auth.uid() is not null and exists(select 1 from public.posts p
where p.id=p_id and (p.status='published' or p.author_id=auth.uid()) and private.sera_social_visible(auth.uid(),p.author_id)
and (p.repost_of is null or p.author_id=auth.uid() or exists(select 1 from public.posts o where o.id=p.repost_of and o.status='published' and private.sera_social_visible(auth.uid(),o.author_id))));
$$;
drop policy posts_read on public.posts;
create policy posts_read on public.posts for select to authenticated using(
 (status='published' or author_id=auth.uid()) and private.sera_social_visible_to_me(author_id)
 and (repost_of is null or author_id=auth.uid() or private.can_read_post(repost_of))
);
