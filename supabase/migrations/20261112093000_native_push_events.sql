create table public.sera_native_push_events(event_id uuid not null,kind text not null,created_at timestamptz not null default now(),primary key(event_id,kind));
alter table public.sera_native_push_events enable row level security;
revoke all on public.sera_native_push_events from public,anon,authenticated;
grant select,insert on public.sera_native_push_events to service_role;
