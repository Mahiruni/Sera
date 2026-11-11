-- SERA Phase 4: direct company-to-person inquiries, isolated from personal DMs and job applications.
-- A person starts the inquiry; owners/admins may reply as the company.
create table public.sera_company_conversations (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid not null references public.sera_organizations(id) on delete cascade,
  person_id uuid not null references auth.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  unique (organization_id,person_id)
);
create index sera_company_conversations_person_idx on public.sera_company_conversations(person_id,created_at desc);
alter table public.sera_company_conversations enable row level security;
revoke all on public.sera_company_conversations from anon,authenticated;
grant select,insert on public.sera_company_conversations to authenticated;
create policy sera_company_thread_read on public.sera_company_conversations for select to authenticated
using (person_id = (select auth.uid()) or exists (
 select 1 from public.sera_organization_members m
 where m.organization_id = sera_company_conversations.organization_id
 and m.user_id = (select auth.uid()) and m.role in ('owner','admin')
));
create policy sera_company_thread_start on public.sera_company_conversations for insert to authenticated
with check (person_id = (select auth.uid()));

create table public.sera_company_messages (
  id uuid primary key default gen_random_uuid(),
  conversation_id uuid not null references public.sera_company_conversations(id) on delete cascade,
  sender_id uuid not null references auth.users(id),
  sender_kind text not null check (sender_kind in ('person','company')),
  body text not null check (char_length(trim(body)) between 1 and 4000),
  created_at timestamptz not null default now(),
  delivered_at timestamptz,
  read_at timestamptz,
  constraint company_message_receipt_order check (
    read_at is null or (delivered_at is not null and read_at >= delivered_at)
  )
);
create index sera_company_messages_conversation_idx on public.sera_company_messages(conversation_id,created_at desc);
alter table public.sera_company_messages enable row level security;
revoke all on public.sera_company_messages from anon,authenticated;
grant select,insert on public.sera_company_messages to authenticated;
grant update(delivered_at,read_at) on public.sera_company_messages to authenticated;
create policy sera_company_message_read on public.sera_company_messages for select to authenticated
using (exists (
 select 1 from public.sera_company_conversations c where c.id=conversation_id
 and (c.person_id=(select auth.uid()) or exists (
  select 1 from public.sera_organization_members m
  where m.organization_id=c.organization_id and m.user_id=(select auth.uid())
   and m.role in ('owner','admin')
 ))
));
create policy sera_company_message_send on public.sera_company_messages for insert to authenticated
with check (sender_id=(select auth.uid()) and delivered_at is null and read_at is null
 and exists (select 1 from public.sera_company_conversations c
 where c.id=conversation_id and (
  (sender_kind='person' and c.person_id=(select auth.uid()))
  or (sender_kind='company' and exists (
   select 1 from public.sera_organization_members m
   where m.organization_id=c.organization_id and m.user_id=(select auth.uid())
    and m.role in ('owner','admin')
  ))
 )));
-- Only recipients can acknowledge delivery/read; acknowledgements are monotonic.
create policy sera_company_receipt_update on public.sera_company_messages for update to authenticated
using (exists (
 select 1 from public.sera_company_conversations c where c.id=conversation_id
 and ((sender_kind='company' and c.person_id=(select auth.uid()))
   or (sender_kind='person' and exists (
    select 1 from public.sera_organization_members m where m.organization_id=c.organization_id
    and m.user_id=(select auth.uid()) and m.role in ('owner','admin')
   )))
))
with check (exists (
 select 1 from public.sera_company_conversations c where c.id=conversation_id
 and ((sender_kind='company' and c.person_id=(select auth.uid()))
   or (sender_kind='person' and exists (
    select 1 from public.sera_organization_members m where m.organization_id=c.organization_id
    and m.user_id=(select auth.uid()) and m.role in ('owner','admin')
   )))
));
create function public.sera_guard_company_receipt() returns trigger
language plpgsql security invoker set search_path = '' as $$
begin
 if new.id is distinct from old.id or new.conversation_id is distinct from old.conversation_id
  or new.sender_id is distinct from old.sender_id or new.sender_kind is distinct from old.sender_kind
  or new.body is distinct from old.body or new.created_at is distinct from old.created_at
  or (old.delivered_at is not null and new.delivered_at is distinct from old.delivered_at)
  or (old.read_at is not null and new.read_at is distinct from old.read_at)
 then raise exception 'Only forward delivery and read transitions are allowed'; end if;
 if old.delivered_at is null and new.delivered_at is not null then
  new.delivered_at := clock_timestamp();
 end if;
 if old.read_at is null and new.read_at is not null then
  if new.delivered_at is null then new.delivered_at := clock_timestamp(); end if;
  new.read_at := clock_timestamp();
 end if;
 return new;
end;
$$;
revoke all on function public.sera_guard_company_receipt() from public,anon,authenticated;
create trigger sera_guard_company_receipt_update before update on public.sera_company_messages
for each row execute function public.sera_guard_company_receipt();
-- 'Sent' is committed to DB; 'Delivered' requires receiver acknowledgement;
-- 'Read' requires receiver open action. No fake delivery guarantees.
