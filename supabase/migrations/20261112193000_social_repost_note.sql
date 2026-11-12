alter table public.posts add column if not exists repost_note text not null default '' check(char_length(repost_note)<=400);
grant insert(repost_note) on public.posts to authenticated;
create function public.sera_feed_headlines(p_ids uuid[]) returns table(id uuid,headline text) language sql stable security definer set search_path='' as $$
select p.id,p.headline from public.profiles p where auth.uid() is not null and p.id=any(p_ids[1:100]) and exists(select 1 from public.posts x where x.author_id=p.id and x.status='published') and not exists(select 1 from public.professional_blocks b where (b.blocker_id=auth.uid() and b.blocked_id=p.id) or (b.blocker_id=p.id and b.blocked_id=auth.uid()));
$$;
revoke all on function public.sera_feed_headlines(uuid[]) from public,anon;
grant execute on function public.sera_feed_headlines(uuid[]) to authenticated;