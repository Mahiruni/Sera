create table public.sera_talent_shortlist(owner_id uuid not null references auth.users(id) on delete cascade,member_id uuid not null references auth.users(id) on delete cascade,created_at timestamptz not null default now(),primary key(owner_id,member_id),check(owner_id<>member_id));
alter table public.sera_talent_shortlist enable row level security;
revoke all on public.sera_talent_shortlist from public,anon,authenticated;
grant select on public.sera_talent_shortlist to authenticated;
create policy shortlist_owner_read on public.sera_talent_shortlist for select to authenticated using(owner_id=auth.uid());
create function public.sera_save_talent(p_member uuid,p_saved boolean) returns void language plpgsql security definer set search_path='' as $$
declare u uuid:=auth.uid();
begin
 if u is null or u=p_member or not exists(select 1 from auth.users where id=u and email_confirmed_at is not null) then raise exception 'Verified account required'; end if;
 if not p_saved then delete from public.sera_talent_shortlist where owner_id=u and member_id=p_member; return; end if;
 perform pg_advisory_xact_lock(hashtextextended(u::text,2));
 if exists(select 1 from public.sera_talent_shortlist where owner_id=u and member_id=p_member) then return; end if;
 if (select count(*) from public.sera_talent_shortlist where owner_id=u)>=200 then raise exception 'Shortlist limit reached'; end if;
 if not exists(select 1 from public.professional_public_portfolios p where p.user_id=p_member and p.published) or exists(select 1 from public.professional_blocks b where (b.blocker_id=u and b.blocked_id=p_member) or (b.blocker_id=p_member and b.blocked_id=u)) then raise exception 'Portfolio unavailable'; end if;
 insert into public.sera_talent_shortlist(owner_id,member_id) values(u,p_member);
end; $$;
create function public.sera_list_saved_talent() returns table(member_id uuid,full_name text,headline text,skills text,username text,showcase_title text,showcase_kind text) language sql stable security definer set search_path='' as $$
 select s.member_id,p.full_name,p.headline,p.skills,u.username,i.title,i.kind
 from public.sera_talent_shortlist s join public.professional_public_portfolios p on p.user_id=s.member_id
 left join public.professional_usernames u on u.user_id=s.member_id
 left join lateral(select x.title,x.kind from public.sera_portfolio_items x where x.user_id=s.member_id and x.shared order by x.featured_at desc nulls last,x.created_at desc limit 1)i on true
 where s.owner_id=auth.uid() and p.published and not exists(select 1 from public.professional_blocks b where (b.blocker_id=auth.uid() and b.blocked_id=s.member_id) or (b.blocker_id=s.member_id and b.blocked_id=auth.uid()))
 order by s.created_at desc limit 200;
$$;
revoke all on function public.sera_save_talent(uuid,boolean),public.sera_list_saved_talent() from public,anon;
grant execute on function public.sera_save_talent(uuid,boolean),public.sera_list_saved_talent() to authenticated;
