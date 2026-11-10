-- Opt-in ephemeral presence, visible only to accepted connections.
create table public.professional_presence (
 member_id uuid primary key references auth.users(id) on delete cascade,
 last_seen_at timestamptz not null default now(),
 visibility text not null default 'connections' check(visibility in ('connections','hidden'))
);
alter table public.professional_presence enable row level security;
revoke all on public.professional_presence from anon;
grant select,insert,update,delete on public.professional_presence to authenticated;
create policy "Members see authorized presence" on public.professional_presence for select to authenticated
using(member_id=(select auth.uid()) or (
 visibility='connections' and exists(
  select 1 from public.professional_connection_requests r
  where r.status='accepted' and (
    (r.requester_id=(select auth.uid()) and r.recipient_id=member_id)
    or (r.recipient_id=(select auth.uid()) and r.requester_id=member_id)
  )
 ) and not exists(select 1 from public.professional_blocks b where
  (b.blocker_id=member_id and b.blocked_id=(select auth.uid()))
  or (b.blocker_id=(select auth.uid()) and b.blocked_id=member_id))
));
create policy "Members set own presence" on public.professional_presence for insert to authenticated
with check(member_id=(select auth.uid()) and last_seen_at<=now());
create policy "Members refresh own presence" on public.professional_presence for update to authenticated
using(member_id=(select auth.uid()))
with check(member_id=(select auth.uid()) and last_seen_at<=now());
create policy "Members remove own presence" on public.professional_presence for delete to authenticated
using(member_id=(select auth.uid()));
