-- Phase 4: INDIVIDUAL social activity visibility.
-- Only personal profile authors use this presence table on feeds and profiles.
-- Company/organization identities must never expose this presence on public surfaces.
-- Company team availability belongs only inside an authorized company inbox,
-- using a separate, consented inbox heartbeat, not an employee's presence.
-- New activity records default to public for signed-in members; existing hidden
-- and connections-only preferences stay unchanged.
alter table public.professional_presence
  alter column visibility set default 'public';
alter table public.professional_presence
  drop constraint if exists professional_presence_visibility_check;
alter table public.professional_presence
  add constraint professional_presence_visibility_check
  check (visibility in ('public','connections','hidden'));
drop policy if exists "Members see authorized presence" on public.professional_presence;
create policy "Members see authorized presence" on public.professional_presence
for select to authenticated using (
  member_id = (select auth.uid()) or (
    visibility <> 'hidden' and (
      visibility = 'public' or exists (
        select 1 from public.professional_connection_requests r
        where r.status = 'accepted' and (
          (r.requester_id = (select auth.uid()) and r.recipient_id = member_id)
          or (r.recipient_id = (select auth.uid()) and r.requester_id = member_id)
        )
      )
    )
    and not exists (
      select 1 from public.professional_blocks b
      where (b.blocker_id = member_id and b.blocked_id = (select auth.uid()))
         or (b.blocker_id = (select auth.uid()) and b.blocked_id = member_id)
    )
  )
);
-- Existing user presence rows and their visibility preferences are not modified.
