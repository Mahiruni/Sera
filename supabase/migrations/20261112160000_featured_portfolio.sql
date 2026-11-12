alter table public.sera_portfolio_items add column featured_at timestamptz;
create function public.sera_feature_portfolio_item(p_id uuid,p_feature boolean) returns void language plpgsql security definer set search_path='' as $$
declare u uuid:=auth.uid(); v timestamptz;
begin
 if u is null then raise exception 'Authentication required'; end if;
 perform pg_advisory_xact_lock(hashtextextended(u::text,1));
 select featured_at into v from public.sera_portfolio_items where id=p_id and user_id=u;
 if not found then raise exception 'Item unavailable'; end if;
 if p_feature and v is null and (select count(*) from public.sera_portfolio_items where user_id=u and featured_at is not null)>=3 then raise exception 'Maximum three featured items'; end if;
 update public.sera_portfolio_items set featured_at=case when p_feature then coalesce(v,now()) else null end where id=p_id and user_id=u;
end; $$;
revoke all on function public.sera_feature_portfolio_item(uuid,boolean) from public,anon;
grant execute on function public.sera_feature_portfolio_item(uuid,boolean) to authenticated;
