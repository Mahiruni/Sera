-- Personalized ranking is caller-scoped and respects the existing posts RLS.
create function public.sera_ranked_feed(p_offset integer default 0,p_limit integer default 20)
returns setof public.posts language sql stable security invoker set search_path='' as $$
with viewer as (select auth.uid() as uid),
skills as (select distinct lower(btrim(t)) as skill from public.profiles p cross join lateral regexp_split_to_table(coalesce(p.skills,''),'[,;\n]+') t where p.id=auth.uid() and length(btrim(t)) between 1 and 80 limit 100),
ranked as (
 select p.id,p.published_at,
 case when p.published_at >= (current_date-14)::timestamptz then
 (case when exists(select 1 from public.professional_follows f where f.follower_id=auth.uid() and f.followed_id=p.author_id) then 3 else 0 end)
 + (case when exists(select 1 from public.professional_connection_requests c where c.status='accepted' and ((c.requester_id=auth.uid() and c.recipient_id=p.author_id) or (c.recipient_id=auth.uid() and c.requester_id=p.author_id))) then 3 else 0 end)
 + least(4,2*(select count(*) from unnest(p.skills) s where lower(btrim(s)) in (select skill from skills)))
 else 0 end as score
 from public.posts p where p.status='published' and auth.uid() is not null
)
select p.* from ranked r join public.posts p on p.id=r.id order by r.score desc,r.published_at desc nulls last,r.id desc
offset greatest(0,coalesce(p_offset,0)) limit greatest(1,least(21,coalesce(p_limit,20)));
$$;
revoke all on function public.sera_ranked_feed(integer,integer) from public,anon;
grant execute on function public.sera_ranked_feed(integer,integer) to authenticated;
