alter table public.profiles drop constraint if exists profiles_linkedin_imported_fields_check;
alter table public.profiles add constraint profiles_linkedin_imported_fields_check
  check (cardinality(linkedin_imported_fields) <= 12);
