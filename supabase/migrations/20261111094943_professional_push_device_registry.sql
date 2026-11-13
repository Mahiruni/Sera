create table if not exists public.professional_push_devices (
 id uuid primary key default gen_random_uuid(),
 user_id uuid not null references auth.users(id) on delete cascade,
 token text not null unique,
 platform text not null check (platform in ('android','web')),
 enabled boolean not null default true,
 created_at timestamptz not null default now(),
 updated_at timestamptz not null default now(),
 constraint push_token_length check (char_length(token) between 20 and 4096)
);
create index if not exists professional_push_devices_user_idx on public.professional_push_devices(user_id);
alter table public.professional_push_devices enable row level security;
revoke all on public.professional_push_devices from anon;
grant select, insert, update, delete on public.professional_push_devices to authenticated;
create policy "push_devices_select_own" on public.professional_push_devices for select to authenticated using (user_id = (select auth.uid()));
create policy "push_devices_insert_own" on public.professional_push_devices for insert to authenticated with check (user_id = (select auth.uid()));
create policy "push_devices_update_own" on public.professional_push_devices for update to authenticated using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));
create policy "push_devices_delete_own" on public.professional_push_devices for delete to authenticated using (user_id = (select auth.uid()));