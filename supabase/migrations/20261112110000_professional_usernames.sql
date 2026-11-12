create table public.professional_usernames (
 user_id uuid primary key references auth.users(id) on delete cascade,
 username text not null unique check(username ~ '^[a-z][a-z_]{2,29}$'),
 changed_at timestamptz not null default now()
);
alter table public.professional_usernames enable row level security;
revoke all on public.professional_usernames from public,anon,authenticated;
grant select on public.professional_usernames to authenticated;
create policy own_username on public.professional_usernames for select to authenticated using(user_id=auth.uid());
create function public.sera_username_valid(p text) returns boolean language sql immutable set search_path='' as $$
 select coalesce(p ~ '^[a-z][a-z_]{2,29}$' and p not in ('about','account','applications','categories','connections','contact','dashboard','demo','discover','education','employer','feed','guides','help','institutions','jobs','languages','matches','messages','moderation','notifications','onboarding','opportunities','pricing','privacy','profile','saved','settings','terms','workspace','admin','api','auth','members','posts','portfolio','downloads','en','am','ar','om','ti','aa','so','sera','support','root','login','signup','register','www','sitemap','robots'),false);
$$;
create function public.sera_username_available(p_username text) returns boolean language sql stable security definer set search_path='' as $$
 select auth.uid() is not null and public.sera_username_valid(p_username) and not exists(select 1 from public.professional_usernames u where u.username=p_username and u.user_id<>auth.uid());
$$;
create function public.sera_set_username(p_username text) returns text language plpgsql security definer set search_path='' as $$
declare current_row public.professional_usernames%rowtype;
begin
 if auth.uid() is null or not exists(select 1 from auth.users where id=auth.uid() and email_confirmed_at is not null) then raise exception 'Verified account required'; end if;
 if not public.sera_username_valid(p_username) then raise exception 'Invalid or reserved username'; end if;
 perform pg_advisory_xact_lock(hashtextextended(auth.uid()::text,0));
 select * into current_row from public.professional_usernames where user_id=auth.uid();
 if found then
 if current_row.username=p_username then return p_username; end if;
 if current_row.changed_at>now()-interval '60 days' then raise exception 'Username can be changed after %',current_row.changed_at+interval '60 days'; end if;
 end if;
 insert into public.professional_usernames(user_id,username) values(auth.uid(),p_username)
 on conflict(user_id) do update set username=excluded.username,changed_at=now();
 return p_username;
exception when unique_violation then raise exception 'Username already taken';
end; $$;
create function public.sera_resolve_username(p_username text) returns table(member_id uuid,public_portfolio boolean) language sql stable security definer set search_path='' as $$
 select u.user_id,exists(select 1 from public.professional_public_portfolios p where p.user_id=u.user_id and p.published)
 from public.professional_usernames u where u.username=p_username and (
 exists(select 1 from public.professional_public_portfolios p where p.user_id=u.user_id and p.published)
 or (auth.uid() is not null and (u.user_id=auth.uid() or (
 not exists(select 1 from public.professional_blocks b where (b.blocker_id=u.user_id and b.blocked_id=auth.uid()) or (b.blocked_id=u.user_id and b.blocker_id=auth.uid()))
 and (not exists(select 1 from public.professional_profile_visibility v where v.user_id=u.user_id and v.visibility='connections')
 or exists(select 1 from public.professional_connection_requests r where r.status='accepted' and ((r.requester_id=u.user_id and r.recipient_id=auth.uid()) or (r.recipient_id=u.user_id and r.requester_id=auth.uid()))))))));
$$;
revoke all on function public.sera_username_available(text),public.sera_set_username(text),public.sera_resolve_username(text) from public,anon,authenticated;
grant execute on function public.sera_username_available(text),public.sera_set_username(text) to authenticated;
grant execute on function public.sera_resolve_username(text) to anon,authenticated;
