-- Private 1:1 conversations. A participant may read only their own conversations.
create table public.professional_conversations (
  id uuid primary key default gen_random_uuid(),
  member_a uuid not null references auth.users(id) on delete cascade,
  member_b uuid not null references auth.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  last_message_at timestamptz not null default now(),
  constraint professional_conversation_distinct check (member_a < member_b),
  unique (member_a, member_b)
);
create index professional_conversations_b_idx on public.professional_conversations(member_b, last_message_at desc);
create index professional_conversations_a_idx on public.professional_conversations(member_a, last_message_at desc);
alter table public.professional_conversations enable row level security;
revoke all on public.professional_conversations from anon;
grant select, insert on public.professional_conversations to authenticated;
create policy "Conversation participants read" on public.professional_conversations
for select to authenticated using (member_a = (select auth.uid()) or member_b = (select auth.uid()));
create policy "Participant starts a conversation" on public.professional_conversations
for insert to authenticated with check (
  (member_a = (select auth.uid()) or member_b = (select auth.uid()))
  and exists (
    select 1 from public.professional_connection_requests r
    where r.status = 'accepted'
      and ((r.requester_id = member_a and r.recipient_id = member_b)
        or (r.requester_id = member_b and r.recipient_id = member_a))
  )
);

create table public.professional_messages (
  id uuid primary key default gen_random_uuid(),
  conversation_id uuid not null references public.professional_conversations(id) on delete cascade,
  sender_id uuid not null references auth.users(id) on delete cascade,
  body text not null check (char_length(trim(body)) between 1 and 4000),
  created_at timestamptz not null default now(),
  read_at timestamptz
);
create index professional_messages_thread_idx on public.professional_messages(conversation_id, created_at desc);
alter table public.professional_messages enable row level security;
revoke all on public.professional_messages from anon;
grant select, insert, update on public.professional_messages to authenticated;
create policy "Participants read messages" on public.professional_messages
for select to authenticated using (exists (
  select 1 from public.professional_conversations c
  where c.id = conversation_id and (c.member_a = (select auth.uid()) or c.member_b = (select auth.uid()))
));
create policy "Participants send as themselves" on public.professional_messages
for insert to authenticated with check (
  sender_id = (select auth.uid()) and read_at is null
  and exists (select 1 from public.professional_conversations c where c.id = conversation_id
    and (c.member_a = (select auth.uid()) or c.member_b = (select auth.uid())))
);
create policy "Recipient marks messages read" on public.professional_messages
for update to authenticated using (
  sender_id <> (select auth.uid()) and exists (
    select 1 from public.professional_conversations c
    where c.id = conversation_id and (c.member_a = (select auth.uid()) or c.member_b = (select auth.uid()))
  )
) with check (
  sender_id <> (select auth.uid()) and read_at is not null and exists (
    select 1 from public.professional_conversations c
    where c.id = conversation_id and (c.member_a = (select auth.uid()) or c.member_b = (select auth.uid()))
  )
);
create function public.guard_professional_message_update() returns trigger
language plpgsql security invoker set search_path = '' as $$
begin
  if new.id is distinct from old.id or new.conversation_id is distinct from old.conversation_id
     or new.sender_id is distinct from old.sender_id or new.body is distinct from old.body
     or new.created_at is distinct from old.created_at or old.read_at is not null
     or new.read_at is null then
    raise exception 'Only unread messages can be marked as read';
  end if;
  return new;
end; $$;
create trigger guard_professional_message_before_update
before update on public.professional_messages
for each row execute function public.guard_professional_message_update();

-- Enable publication for this table in the hosted Supabase project after schema verification.
-- Local PGlite test databases do not provide Supabase's publication.
