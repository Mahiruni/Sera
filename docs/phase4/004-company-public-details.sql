-- SERA Phase 4: optional public company profile details.
-- Public details are separate from private founder contact records and memberships.
create table if not exists public.sera_organization_details (
  organization_id uuid primary key references public.sera_organizations(id) on delete cascade,
  street_address text not null default '' check (char_length(street_address) <= 300),
  region text not null default '' check (char_length(region) <= 120),
  phone_public text not null default '' check (char_length(phone_public) <= 40),
  email_public text not null default '' check (char_length(email_public) <= 254),
  website_url text not null default '' check (char_length(website_url) <= 400),
  services text not null default '' check (char_length(services) <= 2000),
  business_hours text not null default '' check (char_length(business_hours) <= 350),
  founded_year integer check (founded_year between 1800 and 2100),
  company_size text not null default '' check (company_size in ('','1-10','11-50','51-200','201-500','501-1000','1000+')),
  linkedin_url text not null default '' check (char_length(linkedin_url) <= 400),
  updated_at timestamptz not null default now()
);
alter table public.sera_organization_details enable row level security;
revoke all on public.sera_organization_details from anon, authenticated;
grant select, insert, update on public.sera_organization_details to authenticated;
create policy "sera_org_details_read" on public.sera_organization_details
  for select to authenticated using (true);
create policy "sera_org_details_insert_manager" on public.sera_organization_details
  for insert to authenticated with check (
    exists (select 1 from public.sera_organization_members m
      where m.organization_id = sera_organization_details.organization_id
        and m.user_id = (select auth.uid()) and m.role in ('owner','admin'))
  );
create policy "sera_org_details_update_manager" on public.sera_organization_details
  for update to authenticated
  using (exists (select 1 from public.sera_organization_members m
      where m.organization_id = sera_organization_details.organization_id
        and m.user_id = (select auth.uid()) and m.role in ('owner','admin')))
  with check (exists (select 1 from public.sera_organization_members m
      where m.organization_id = sera_organization_details.organization_id
        and m.user_id = (select auth.uid()) and m.role in ('owner','admin')));
-- Do not allow reassignment of profile details to a different organization.
revoke update(organization_id) on public.sera_organization_details from authenticated;
