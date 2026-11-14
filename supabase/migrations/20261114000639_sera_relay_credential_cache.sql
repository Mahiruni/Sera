create table if not exists public.sera_relay_credential_cache (
 member_id uuid primary key references auth.users(id) on delete cascade,
 sealed text not null check(length(sealed) between 1 and 16384),
 reuse_until timestamptz not null
);
alter table public.sera_relay_credential_cache enable row level security;
revoke all on public.sera_relay_credential_cache from public,anon,authenticated;
grant select,insert,update,delete on public.sera_relay_credential_cache to service_role;
comment on table public.sera_relay_credential_cache is 'Server-only encrypted temporary TURN credential cache. No client grants; no RLS policies.';
