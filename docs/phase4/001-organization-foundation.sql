-- SERA Phase 4: organization identity foundation (review before applying).
-- Additive migration; no existing SERA tables are altered.
create table if not exists public.sera_organizations (
  id uuid primary key default gen_random_uuid(),
  kind text not null check (kind in ('company','organization')),
  name text not null check (char_length(trim(name)) between 2 and 160),
  slug text not null unique check (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
  description text,
  country_code char(2) not null default 'ET',
  city text,
  industry text,
  verification_status text not null default 'unverified'
    check (verification_status in ('unverified','pending','verified','rejected')),
  created_by uuid not null references auth.users(id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create table if not exists public.sera_organization_members (
  organization_id uuid not null references public.sera_organizations(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  role text not null check (role in ('owner','admin','member')),
  joined_at timestamptz not null default now(),
  primary key (organization_id,user_id)
);
create index if not exists sera_organization_members_user_idx on public.sera_organization_members(user_id);
create index if not exists sera_organizations_city_idx on public.sera_organizations(country_code,city);
alter table public.sera_organizations enable row level security;
alter table public.sera_organization_members enable row level security;
-- Organization profiles are discoverable; membership is only visible to members.
create policy "sera_org_read" on public.sera_organizations for select to authenticated using (true);
create policy "sera_org_members_read" on public.sera_organization_members for select to authenticated
 using (user_id = (select auth.uid()));
-- Creation must be transactional through a privileged, validated server-side function:
-- no direct INSERT/UPDATE/DELETE policies are granted to client roles.
-- Never expose service-role credentials to the browser or Android application.
-- Verification status may only be changed through an administrator-reviewed workflow.
