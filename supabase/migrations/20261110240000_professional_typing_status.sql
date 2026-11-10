-- Ephemeral typing signals: no message content, no persistent online tracking.
create table public.professional_typing_status (
 conversation_id uuid not null references public.professional_conversations(id) on delete cascade,
 member_id uuid not null references auth.users(id) on delete cascade,
 updated_at timestamptz not null default now(),
 primary key(conversation_id,member_id)
);
create index professional_typing_status_updated_idx on public.professional_typing_status(updated_at);
alter table public.professional_typing_status enable row level security;
revoke all on public.professional_typing_status from anon;
grant select,insert,update,delete on public.professional_typing_status to authenticated;
create policy "Participants read recent typing" on public.professional_typing_status
for select to authenticated using (
 updated_at > now() - interval '12 seconds'
 and exists(select 1 from public.professional_conversations c where c.id=conversation_id
 and (c.member_a=(select auth.uid()) or c.member_b=(select auth.uid())))
);
create policy "Members announce own typing" on public.professional_typing_status
for insert to authenticated with check (
 member_id=(select auth.uid()) and updated_at <= now()
 and exists(select 1 from public.professional_conversations c where c.id=conversation_id
 and (c.member_a=(select auth.uid()) or c.member_b=(select auth.uid())))
);
create policy "Members refresh own typing" on public.professional_typing_status
for update to authenticated using (member_id=(select auth.uid()))
with check (member_id=(select auth.uid()) and updated_at <= now());
create policy "Members stop own typing" on public.professional_typing_status
for delete to authenticated using (member_id=(select auth.uid()));
