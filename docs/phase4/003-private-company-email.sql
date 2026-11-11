-- SERA Phase 4 company-email onboarding, after organization foundation.
-- Founder contact email is private; it must not appear in public org discovery.
create table if not exists public.sera_organization_contact_emails (
  organization_id uuid primary key references public.sera_organizations(id) on delete cascade,
  owner_user_id uuid not null references auth.users(id) on delete cascade,
  contact_email text not null check (length(contact_email) between 3 and 254 and contact_email = lower(contact_email)),
  recorded_at timestamptz not null default now()
);
alter table public.sera_organization_contact_emails enable row level security;
revoke all on public.sera_organization_contact_emails from anon, authenticated;
grant select, insert on public.sera_organization_contact_emails to authenticated;
create policy "sera_contact_owner_read" on public.sera_organization_contact_emails
  for select to authenticated using (owner_user_id = (select auth.uid()));
create policy "sera_contact_owner_insert" on public.sera_organization_contact_emails
  for insert to authenticated with check (
    owner_user_id = (select auth.uid())
    and contact_email = lower((select auth.jwt())->>'email')
    and exists (select 1 from public.sera_organizations o
      where o.id = organization_id and o.created_by = (select auth.uid()))
  );
-- Keep the existing RPC signature so clients do not face overloaded functions.
-- Contact email is copied from the authenticated JWT, never from user-provided business form text.
create or replace function public.sera_create_organization(
  p_kind text, p_name text, p_slug text, p_description text default null,
  p_city text default null, p_industry text default null
) returns uuid
language plpgsql security invoker set search_path = ''
as $$
declare v_id uuid;
declare v_email text;
begin
  if auth.uid() is null then raise exception 'Authentication required'; end if;
  v_email := lower(trim(coalesce(auth.jwt()->>'email','')));
  if v_email = '' or length(v_email) > 254 or v_email !~ '^[^[:space:]@]+@[^[:space:]@]+[.][^[:space:]@]+$'
    then raise exception 'A valid signed-in email is required'; end if;
  if p_kind not in ('company','organization')
    or p_name is null or length(trim(p_name)) not between 2 and 160
    or p_slug is null or p_slug !~ '^[a-z0-9]+(-[a-z0-9]+)*$'
    or length(p_slug) not between 3 and 80
    or length(coalesce(p_description,'')) > 2000
    or length(coalesce(p_city,'')) > 120
    or length(coalesce(p_industry,'')) > 120
  then raise exception 'Invalid organization details'; end if;
  insert into public.sera_organizations
    (kind,name,slug,description,city,industry,created_by)
  values (p_kind,trim(p_name),p_slug,p_description,p_city,p_industry,auth.uid())
  returning id into v_id;
  insert into public.sera_organization_members (organization_id,user_id,role)
  values (v_id,auth.uid(),'owner');
  insert into public.sera_organization_contact_emails (organization_id,owner_user_id,contact_email)
  values (v_id,auth.uid(),v_email);
  return v_id;
end;
$$;
-- Registration email records mailbox ownership; it never confers company verification.
