-- Moderation queue. Admin privileges must be assigned via trusted auth.app_metadata.
alter table public.professional_reports
 add column status text not null default 'open' check(status in ('open','reviewing','resolved','dismissed')),
 add column reviewed_at timestamptz,
 add column reviewed_by uuid references auth.users(id) on delete set null;
grant update on public.professional_reports to authenticated;
create index professional_reports_queue_idx on public.professional_reports(status,created_at desc);
create policy "Moderators review member reports" on public.professional_reports
for select to authenticated using (
 coalesce(auth.jwt()->'app_metadata'->>'role','') in ('admin','moderator')
);
create policy "Moderators update member reports" on public.professional_reports
for update to authenticated using (
 coalesce(auth.jwt()->'app_metadata'->>'role','') in ('admin','moderator')
) with check (
 coalesce(auth.jwt()->'app_metadata'->>'role','') in ('admin','moderator')
 and reviewed_by=(select auth.uid())
 and reviewed_at is not null
);

-- Keep confidential internal notes separate from reporter-visible report rows.
create table public.professional_report_reviews (
 report_id uuid primary key references public.professional_reports(id) on delete cascade,
 moderator_id uuid not null references auth.users(id),
 note text not null check(char_length(note)<=2000),
 updated_at timestamptz not null default now()
);
alter table public.professional_report_reviews enable row level security;
revoke all on public.professional_report_reviews from anon;
grant select,insert,update on public.professional_report_reviews to authenticated;
create policy "Moderators read private review notes" on public.professional_report_reviews
for select to authenticated using (
 coalesce(auth.jwt()->'app_metadata'->>'role','') in ('admin','moderator')
);
create policy "Moderators create private review notes" on public.professional_report_reviews
for insert to authenticated with check (
 coalesce(auth.jwt()->'app_metadata'->>'role','') in ('admin','moderator')
 and moderator_id=(select auth.uid())
);
create policy "Moderators update private review notes" on public.professional_report_reviews
for update to authenticated using (
 coalesce(auth.jwt()->'app_metadata'->>'role','') in ('admin','moderator')
) with check (
 coalesce(auth.jwt()->'app_metadata'->>'role','') in ('admin','moderator')
 and moderator_id=(select auth.uid())
);
