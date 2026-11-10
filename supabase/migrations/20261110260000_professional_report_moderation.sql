-- Moderation queue. Admin privileges must be assigned via trusted auth.app_metadata.
alter table public.professional_reports
 add column status text not null default 'open' check(status in ('open','reviewing','resolved','dismissed')),
 add column reviewed_at timestamptz,
 add column reviewed_by uuid references auth.users(id) on delete set null,
 add column moderator_note text check(char_length(moderator_note)<=2000);
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
