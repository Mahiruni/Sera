-- Author cards deliberately expose only four professional fields; profiles retain owner RLS.
-- Keep the curated cross-member lookup in the private schema, with explicit session gating.
create or replace function private.social_authors()
returns table(id uuid,full_name text,headline text,location text)
language sql stable security definer set search_path='' as $$
  select p.id,p.full_name,p.headline,p.location from public.profiles p
  where auth.uid() is not null and (
    exists(select 1 from public.posts x where x.author_id=p.id and x.status='published')
    or exists(select 1 from public.post_comments c join public.posts x on x.id=c.post_id where c.author_id=p.id and x.status='published')
  )
$$;
revoke all on function private.social_authors() from public,anon,authenticated;
grant execute on function private.social_authors() to authenticated;
create or replace view public.post_authors with (security_invoker=true) as
  select id,full_name,headline,location from private.social_authors();
revoke all on public.post_authors from public,anon,authenticated;
grant select on public.post_authors to authenticated;
