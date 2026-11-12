create table public.sera_portfolio_items (
 id uuid primary key default gen_random_uuid(), user_id uuid not null references auth.users(id) on delete cascade,
 kind text not null check(kind in ('project','award','certification','achievement')),
 title text not null check(char_length(trim(title)) between 1 and 120),
 description text not null default '' check(char_length(description)<=2000),
 issuer text not null default '' check(char_length(issuer)<=120),
 external_url text not null default '' check(char_length(external_url)<=500 and (external_url='' or external_url ~ '^https://[^[:space:]]+$')),
 shared boolean not null default false, created_at timestamptz not null default now()
);
create index sera_portfolio_owner on public.sera_portfolio_items(user_id,created_at desc);
alter table public.sera_portfolio_items enable row level security;
revoke all on public.sera_portfolio_items from public,anon,authenticated;
grant select on public.sera_portfolio_items to anon,authenticated;
create policy portfolio_items_read on public.sera_portfolio_items for select to anon,authenticated using(
 user_id=auth.uid() or (shared and exists(select 1 from public.professional_public_portfolios p where p.user_id=sera_portfolio_items.user_id and p.published)));
create function public.sera_save_portfolio_item(p_id uuid,p_kind text,p_title text,p_description text,p_issuer text,p_url text,p_shared boolean) returns uuid language plpgsql security definer set search_path='' as $$
declare v uuid; u uuid:=auth.uid();
begin
 if u is null or not exists(select 1 from auth.users where id=u and email_confirmed_at is not null) then raise exception 'Verified account required'; end if;
 perform pg_advisory_xact_lock(hashtextextended(u::text,1));
 if p_id is null then
 if (select count(*) from public.sera_portfolio_items where user_id=u)>=50 then raise exception 'Portfolio limit reached'; end if;
 insert into public.sera_portfolio_items(user_id,kind,title,description,issuer,external_url,shared) values(u,p_kind,trim(p_title),trim(coalesce(p_description,'')),trim(coalesce(p_issuer,'')),trim(coalesce(p_url,'')),coalesce(p_shared,false)) returning id into v;
 else
 update public.sera_portfolio_items set kind=p_kind,title=trim(p_title),description=trim(coalesce(p_description,'')),issuer=trim(coalesce(p_issuer,'')),external_url=trim(coalesce(p_url,'')),shared=coalesce(p_shared,false) where id=p_id and user_id=u returning id into v;
 if not found then raise exception 'Item unavailable'; end if;
 end if; return v;
end; $$;
create function public.sera_delete_portfolio_item(p_id uuid) returns void language plpgsql security definer set search_path='' as $$
begin
 delete from public.sera_portfolio_items where id=p_id and user_id=auth.uid();
 if not found then raise exception 'Item unavailable'; end if;
end; $$;
revoke all on function public.sera_save_portfolio_item(uuid,text,text,text,text,text,boolean),public.sera_delete_portfolio_item(uuid) from public,anon;
grant execute on function public.sera_save_portfolio_item(uuid,text,text,text,text,text,boolean),public.sera_delete_portfolio_item(uuid) to authenticated;
