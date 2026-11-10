-- Confidential abuse reports, readable by the reporting member only.
create table public.professional_reports (
 id uuid primary key default gen_random_uuid(),
 reporter_id uuid not null references auth.users(id) on delete cascade,
 reported_id uuid not null references auth.users(id) on delete cascade,
 reason text not null check(reason in ('spam','harassment','impersonation','inappropriate','other')),
 details text check(char_length(details)<=1000),
 created_at timestamptz not null default now(),
 constraint professional_reports_not_self check(reporter_id<>reported_id)
);
create index professional_reports_reporter_idx on public.professional_reports(reporter_id,created_at desc);
alter table public.professional_reports enable row level security;
revoke all on public.professional_reports from anon;
grant insert,select on public.professional_reports to authenticated;
create policy "Members see their own reports" on public.professional_reports for select to authenticated using(reporter_id=(select auth.uid()));
create policy "Members submit their own reports" on public.professional_reports for insert to authenticated with check(reporter_id=(select auth.uid()));
