-- Apply after docs/phase4/001-organization-foundation.sql, following schema review.
-- SECURITY INVOKER RPC ensures the caller's RLS policies apply.
create policy "sera_org_create" on public.sera_organizations for insert to authenticated
  with check (created_by = (select auth.uid()) and verification_status = 'unverified');
create policy "sera_org_owner_membership" on public.sera_organization_members for insert to authenticated
  with check (
    user_id = (select auth.uid()) and role = 'owner'
    and exists (
      select 1 from public.sera_organizations o
      where o.id = organization_id and o.created_by = (select auth.uid())
    )
  );
-- Creation of the organization and its initial owner is atomic.
create or replace function public.sera_create_organization(
  p_kind text, p_name text, p_slug text, p_description text default null,
  p_city text default null, p_industry text default null
) returns uuid
language plpgsql security invoker set search_path = ''
as $$
declare v_id uuid;
begin
  if auth.uid() is null then raise exception 'Authentication required'; end if;
  if p_kind not in ('company', 'organization') then raise exception 'Invalid organization type'; end if;
  if length(trim(p_name)) not between 2 and 160
    or p_slug !~ '^[a-z0-9]+(-[a-z0-9]+)*$'
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
  return v_id;
end;
$$;
revoke all on function public.sera_create_organization(text,text,text,text,text,text) from public, anon;
grant execute on function public.sera_create_organization(text,text,text,text,text,text) to authenticated;
-- Table grants are required for the invoker RPC and are still restricted by RLS.
grant select, insert on public.sera_organizations to authenticated;
grant select, insert on public.sera_organization_members to authenticated;
