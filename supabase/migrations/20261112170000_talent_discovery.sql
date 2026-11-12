create function public.sera_discover_talent(p_query text default '',p_offset integer default 0)
returns table(member_id uuid,full_name text,headline text,skills text,username text,showcase_title text,showcase_kind text)
language sql stable security definer set search_path='' as $$
 select p.user_id,p.full_name,p.headline,p.skills,u.username,i.title,i.kind
 from public.professional_public_portfolios p
 left join public.professional_usernames u on u.user_id=p.user_id
 join lateral (select x.title,x.kind from public.sera_portfolio_items x where x.user_id=p.user_id and x.shared
 and (trim(coalesce(p_query,''))='' or strpos(lower(concat_ws(' ',p.full_name,p.headline,p.skills,u.username,x.title,x.description,x.kind)),lower(left(trim(p_query),80)))>0)
 order by x.featured_at desc nulls last,x.created_at desc limit 1) i on true
 where auth.uid() is not null and p.published and p.user_id<>auth.uid()
 and not exists(select 1 from public.professional_blocks b where (b.blocker_id=auth.uid() and b.blocked_id=p.user_id) or (b.blocker_id=p.user_id and b.blocked_id=auth.uid()))
 order by p.updated_at desc,p.user_id limit 30 offset greatest(0,least(coalesce(p_offset,0),3000));
$$;
revoke all on function public.sera_discover_talent(text,integer) from public,anon;
grant execute on function public.sera_discover_talent(text,integer) to authenticated;
