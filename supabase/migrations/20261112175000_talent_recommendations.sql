-- Explainable recommendations using exact normalized skill phrases, not inferred ability scores.
create function public.sera_recommend_talent()
returns table(member_id uuid,full_name text,headline text,skills text,username text,showcase_title text,showcase_kind text,matched_skills text)
language sql stable security definer set search_path='' as $$
 with my_skills as (
 select distinct lower(trim(t)) as skill from public.profiles p cross join lateral regexp_split_to_table(coalesce(p.skills,''),'[,;|\n]+') t
 where p.id=auth.uid() and char_length(trim(t)) between 2 and 80
 ), candidates as (
 select p.user_id,p.full_name,p.headline,p.skills,u.username,i.title,i.kind,m.matches,m.n
 from public.professional_public_portfolios p
 left join public.professional_usernames u on u.user_id=p.user_id
 join lateral (select x.title,x.kind from public.sera_portfolio_items x where x.user_id=p.user_id and x.shared order by x.featured_at desc nulls last,x.created_at desc limit 1)i on true
 join lateral (select string_agg(s.skill,', ' order by s.skill) matches,count(*) n from my_skills s where exists(select 1 from regexp_split_to_table(coalesce(p.skills,''),'[,;|\n]+') t where lower(trim(t))=s.skill))m on m.n>0
 where auth.uid() is not null and p.published and p.user_id<>auth.uid()
 and not exists(select 1 from public.professional_blocks b where (b.blocker_id=auth.uid() and b.blocked_id=p.user_id) or (b.blocker_id=p.user_id and b.blocked_id=auth.uid()))
 )
 select c.user_id,c.full_name,c.headline,c.skills,c.username,c.title,c.kind,left(c.matches,300) from candidates c order by c.n desc,c.user_id limit 30;
$$;
revoke all on function public.sera_recommend_talent() from public,anon;
grant execute on function public.sera_recommend_talent() to authenticated;
