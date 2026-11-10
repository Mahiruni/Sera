-- A member can block another member. Both parties lose access to conversation content.
create table public.professional_blocks (
 blocker_id uuid not null references auth.users(id) on delete cascade,
 blocked_id uuid not null references auth.users(id) on delete cascade,
 created_at timestamptz not null default now(),
 primary key(blocker_id,blocked_id),
 constraint professional_no_self_block check(blocker_id <> blocked_id)
);
create index professional_blocks_blocked_idx on public.professional_blocks(blocked_id);
alter table public.professional_blocks enable row level security;
revoke all on public.professional_blocks from anon;
grant select,insert,delete on public.professional_blocks to authenticated;
create policy "Members see their own blocks" on public.professional_blocks for select to authenticated using(blocker_id=(select auth.uid()));
create policy "Members block as themselves" on public.professional_blocks for insert to authenticated with check(blocker_id=(select auth.uid()));
create policy "Members unblock as themselves" on public.professional_blocks for delete to authenticated using(blocker_id=(select auth.uid()));
-- Existing private conversations remain stored, but no participant may access
-- messages when either party has blocked the other.
drop policy "Participants read messages" on public.professional_messages;
create policy "Participants read messages" on public.professional_messages
for select to authenticated using (
 exists (select 1 from public.professional_conversations c
 where c.id=conversation_id and (c.member_a=(select auth.uid()) or c.member_b=(select auth.uid()))
 and not exists(select 1 from public.professional_blocks b where
  (b.blocker_id=c.member_a and b.blocked_id=c.member_b) or
  (b.blocker_id=c.member_b and b.blocked_id=c.member_a)))
);
drop policy "Participants send as themselves" on public.professional_messages;
create policy "Participants send as themselves" on public.professional_messages
for insert to authenticated with check (
 sender_id=(select auth.uid()) and read_at is null
 and exists(select 1 from public.professional_conversations c where c.id=conversation_id
 and (c.member_a=(select auth.uid()) or c.member_b=(select auth.uid()))
 and not exists(select 1 from public.professional_blocks b where
  (b.blocker_id=c.member_a and b.blocked_id=c.member_b) or
  (b.blocker_id=c.member_b and b.blocked_id=c.member_a)))
);
drop policy "Recipient marks messages read" on public.professional_messages;
create policy "Recipient marks messages read" on public.professional_messages
for update to authenticated using (
 sender_id<>(select auth.uid()) and exists(select 1 from public.professional_conversations c
 where c.id=conversation_id and (c.member_a=(select auth.uid()) or c.member_b=(select auth.uid()))
 and not exists(select 1 from public.professional_blocks b where
  (b.blocker_id=c.member_a and b.blocked_id=c.member_b) or
  (b.blocker_id=c.member_b and b.blocked_id=c.member_a)))
) with check (
 sender_id<>(select auth.uid()) and read_at is not null
 and exists(select 1 from public.professional_conversations c
 where c.id=conversation_id and (c.member_a=(select auth.uid()) or c.member_b=(select auth.uid()))
 and not exists(select 1 from public.professional_blocks b where
  (b.blocker_id=c.member_a and b.blocked_id=c.member_b) or
  (b.blocker_id=c.member_b and b.blocked_id=c.member_a)))
);
-- Prevent new conversations with blocked members.
drop policy "Participant starts a conversation" on public.professional_conversations;
create policy "Participant starts a conversation" on public.professional_conversations
for insert to authenticated with check (
 (member_a=(select auth.uid()) or member_b=(select auth.uid()))
 and exists(select 1 from public.professional_connection_requests r where r.status='accepted'
 and ((r.requester_id=member_a and r.recipient_id=member_b)
 or (r.requester_id=member_b and r.recipient_id=member_a)))
 and not exists(select 1 from public.professional_blocks b where
 (b.blocker_id=member_a and b.blocked_id=member_b) or
 (b.blocker_id=member_b and b.blocked_id=member_a))
);
