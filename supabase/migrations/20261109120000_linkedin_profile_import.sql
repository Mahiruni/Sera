-- These columns already exist on the inspected hosted database without a migration record.
alter table public.profiles
  add column if not exists linkedin_imported_at timestamptz,
  add column if not exists linkedin_imported_fields text[] not null default '{}'
    check (cardinality(linkedin_imported_fields) <= 5);
grant update(linkedin_imported_at, linkedin_imported_fields) on public.profiles to authenticated;
