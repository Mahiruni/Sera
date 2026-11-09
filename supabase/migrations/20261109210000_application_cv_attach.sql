alter table public.applications
  add column cv_path text,
  add column cv_name text;
alter table public.applications
  add constraint applications_cv_path_owner check (cv_path is null or cv_path like candidate_id::text || '/cv/%'),
  add constraint applications_cv_name_len check (cv_name is null or char_length(cv_name) <= 180);
create index applications_cv_path on public.applications(cv_path) where cv_path is not null;
create or replace function private.employer_reads_cv(p_name text) returns boolean language sql stable security definer set search_path = '' as $$
  select auth.uid() is not null and exists(
    select 1 from public.applications a join public.jobs j on j.id = a.job_id
    where a.cv_path = p_name and a.stage <> 'withdrawn' and j.employer_id = (select auth.uid())
  )
$$;
revoke all on function private.employer_reads_cv(text) from public, anon, authenticated;
grant execute on function private.employer_reads_cv(text) to authenticated;
create policy account_files_employer_cv on storage.objects for select to authenticated
  using (bucket_id = 'sera-account-private' and private.employer_reads_cv(name));
