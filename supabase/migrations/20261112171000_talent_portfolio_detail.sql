create or replace function public.sera_talent_portfolio_detail(p_member_id uuid)
returns jsonb language sql stable security definer set search_path='' as $$
 select jsonb_build_object('name',p.full_name,'headline',p.headline,'summary',p.summary,'location',p.location,'skills',p.skills,'experience',p.experience,'education',p.education,'items',coalesce((
 select jsonb_agg(to_jsonb(i) order by i.featured_at desc nulls last,i.created_at desc) from (
 select x.id,x.kind,x.title,x.description,x.issuer,x.external_url,x.featured_at,x.created_at
 from public.sera_portfolio_items x where x.user_id=p.user_id and x.shared
 order by x.featured_at desc nulls last,x.created_at desc limit 50
 ) i),'[]'::jsonb))
 from public.professional_public_portfolios p
 where p.user_id=p_member_id and p.published and auth.uid() is not null
 and not exists(select 1 from public.professional_blocks b where (b.blocker_id=auth.uid() and b.blocked_id=p.user_id) or (b.blocker_id=p.user_id and b.blocked_id=auth.uid()));
$$;
revoke all on function public.sera_talent_portfolio_detail(uuid) from public,anon;
grant execute on function public.sera_talent_portfolio_detail(uuid) to authenticated;
