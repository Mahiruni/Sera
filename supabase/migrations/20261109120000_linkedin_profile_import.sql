alter table public.profiles
  add column linkedin_imported_at timestamptz,
  add column linkedin_imported_fields text[] not null default '{}'
    check (cardinality(linkedin_imported_fields) <= 5);
grant update(linkedin_imported_at, linkedin_imported_fields) on public.profiles to authenticated;
