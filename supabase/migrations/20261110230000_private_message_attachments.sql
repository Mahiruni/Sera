-- Private attachments belong to one message and inherit the message's participant RLS.
create table public.professional_message_attachments (
 id uuid primary key default gen_random_uuid(),
 message_id uuid not null references public.professional_messages(id) on delete cascade,
 owner_id uuid not null references auth.users(id) on delete cascade,
 storage_path text not null unique,
 file_name text not null check(char_length(file_name) between 1 and 180),
 mime_type text not null check(mime_type in ('image/jpeg','image/png','image/webp','application/pdf')),
 byte_size integer not null check(byte_size between 1 and 5242880),
 created_at timestamptz not null default now()
);
create index professional_message_attachments_message_idx on public.professional_message_attachments(message_id);
alter table public.professional_message_attachments enable row level security;
revoke all on public.professional_message_attachments from anon;
grant select,insert on public.professional_message_attachments to authenticated;
create policy "Participants read attachment metadata" on public.professional_message_attachments
for select to authenticated using (
 exists(select 1 from public.professional_messages m where m.id=message_id)
);
create policy "Sender registers own attachment" on public.professional_message_attachments
for insert to authenticated with check (
 owner_id=(select auth.uid())
 and storage_path like (select auth.uid())::text || '/%'
 and exists(select 1 from public.professional_messages m where m.id=message_id and m.sender_id=(select auth.uid()))
);
