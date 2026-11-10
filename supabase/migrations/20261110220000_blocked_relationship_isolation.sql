-- Blocked pairs cannot see a private conversation, follow, or send new invitations.
drop policy "Conversation participants read" on public.professional_conversations;
create policy "Conversation participants read" on public.professional_conversations
for select to authenticated using (
 (member_a=(select auth.uid()) or member_b=(select auth.uid()))
 and not exists(select 1 from public.professional_blocks b where
 (b.blocker_id=member_a and b.blocked_id=member_b) or
 (b.blocker_id=member_b and b.blocked_id=member_a))
);
drop policy "Members follow as themselves" on public.professional_follows;
create policy "Members follow as themselves" on public.professional_follows
for insert to authenticated with check (
 follower_id=(select auth.uid())
 and not exists(select 1 from public.professional_blocks b where
 (b.blocker_id=follower_id and b.blocked_id=followed_id) or
 (b.blocker_id=followed_id and b.blocked_id=follower_id))
);
drop policy "Members initiate connection requests" on public.professional_connection_requests;
create policy "Members initiate connection requests" on public.professional_connection_requests
for insert to authenticated with check (
 requester_id=(select auth.uid()) and status='pending' and responded_at is null
 and not exists(select 1 from public.professional_blocks b where
 (b.blocker_id=requester_id and b.blocked_id=recipient_id) or
 (b.blocker_id=recipient_id and b.blocked_id=requester_id))
);
