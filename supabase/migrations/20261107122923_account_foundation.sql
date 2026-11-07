create schema if not exists app_private;
revoke all on schema app_private from public, anon, authenticated;
create table public.profiles (
 id uuid primary key references auth.users(id) on delete cascade,
 full_name text not null default '' check (char_length(full_name)<=100),
 locale text not null default 'en' check (locale in ('en','am','om','ti','ar','aa','so')),
 journey text not null default 'candidate' check (journey in ('candidate','employer')),
 location text not null default '' check (char_length(location)<=500),
 phone text not null default '' check (char_length(phone)<=500),
 skills text not null default '' check (char_length(skills)<=500),
 experience text not null default '' check (char_length(experience)<=500),
 preferred_roles text not null default '' check (char_length(preferred_roles)<=500),
 company_name text not null default '' check (char_length(company_name)<=500),
 company_role text not null default '' check (char_length(company_role)<=500),
 onboarded_at timestamptz,
 created_at timestamptz not null default now(),
 updated_at timestamptz not null default now()
);
comment on column public.profiles.journey is 'Editable onboarding preference only; never an authorization role or employer verification.';
alter table public.profiles enable row level security;
revoke all on public.profiles from anon, authenticated;
grant select on public.profiles to authenticated;
grant update(full_name,locale,journey,location,phone,skills,experience,preferred_roles,company_name,company_role,onboarded_at) on public.profiles to authenticated;
create policy profiles_read_own on public.profiles for select to authenticated using ((select auth.uid())=id);
create policy profiles_update_own on public.profiles for update to authenticated using ((select auth.uid())=id) with check ((select auth.uid())=id);
create function app_private.create_profile() returns trigger language plpgsql security definer set search_path='' as $$
begin
 insert into public.profiles(id,full_name,locale,journey) values(new.id,left(coalesce(new.raw_user_meta_data->>'full_name',''),100),case when new.raw_user_meta_data->>'locale' in ('en','am','om','ti','ar','aa','so') then new.raw_user_meta_data->>'locale' else 'en' end,case when new.raw_user_meta_data->>'journey'='employer' then 'employer' else 'candidate' end);
 return new;
end;$$;
revoke all on function app_private.create_profile() from public,anon,authenticated;
create trigger sera_auth_profile after insert on auth.users for each row execute function app_private.create_profile();
create function app_private.touch_profile() returns trigger language plpgsql set search_path='' as $$begin new.updated_at=now(); return new;end;$$;
revoke all on function app_private.touch_profile() from public,anon,authenticated;
create trigger sera_profile_updated before update on public.profiles for each row execute function app_private.touch_profile();
