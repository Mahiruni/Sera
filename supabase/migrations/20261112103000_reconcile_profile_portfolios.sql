-- Reconcile the already-live profile visibility and portfolio tables for clean migration replay.
create table if not exists public.professional_profile_visibility(user_id uuid primary key references auth.users(id) on delete cascade,visibility text not null default 'members' check(visibility in ('members','connections','public')),updated_at timestamptz not null default now());
create table if not exists public.professional_public_portfolios(user_id uuid primary key references auth.users(id) on delete cascade,full_name text not null,headline text,summary text,location text,skills text,experience text,education text,published boolean not null default false,updated_at timestamptz not null default now());
alter table public.professional_profile_visibility enable row level security;
alter table public.professional_public_portfolios enable row level security;
grant select,insert,update on public.professional_profile_visibility to authenticated;
grant select on public.professional_public_portfolios to anon;
grant select,insert,update on public.professional_public_portfolios to authenticated;
do $$ begin
 if not exists(select 1 from pg_policies where schemaname='public' and tablename='professional_profile_visibility' and policyname='profile_visibility_member_read') then create policy profile_visibility_member_read on public.professional_profile_visibility for select to authenticated using(true); end if;
 if not exists(select 1 from pg_policies where schemaname='public' and tablename='professional_profile_visibility' and policyname='profile_visibility_owner_insert') then create policy profile_visibility_owner_insert on public.professional_profile_visibility for insert to authenticated with check(auth.uid()=user_id); end if;
 if not exists(select 1 from pg_policies where schemaname='public' and tablename='professional_profile_visibility' and policyname='profile_visibility_owner_update') then create policy profile_visibility_owner_update on public.professional_profile_visibility for update to authenticated using(auth.uid()=user_id) with check(auth.uid()=user_id); end if;
 if not exists(select 1 from pg_policies where schemaname='public' and tablename='professional_public_portfolios' and policyname='public_portfolio_read_published') then create policy public_portfolio_read_published on public.professional_public_portfolios for select to anon,authenticated using(published or auth.uid()=user_id); end if;
 if not exists(select 1 from pg_policies where schemaname='public' and tablename='professional_public_portfolios' and policyname='public_portfolio_owner_insert') then create policy public_portfolio_owner_insert on public.professional_public_portfolios for insert to authenticated with check(auth.uid()=user_id); end if;
 if not exists(select 1 from pg_policies where schemaname='public' and tablename='professional_public_portfolios' and policyname='public_portfolio_owner_update') then create policy public_portfolio_owner_update on public.professional_public_portfolios for update to authenticated using(auth.uid()=user_id) with check(auth.uid()=user_id); end if;
end $$;
