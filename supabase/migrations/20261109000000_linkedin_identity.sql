-- Adjust "auth.users" to your users table if you do not use Supabase Auth.
create table if not exists public.linkedin_identities (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null unique references auth.users(id) on delete cascade,
  linkedin_sub text not null unique,
  access_token_enc text,
  token_expires_at timestamptz,
  status text not null default 'connected'
    check (status in ('connected','expired','reconnect_required','disconnected')),
  profile_url text,
  last_import_at timestamptz,
  created_at timestamptz not null default now()
);

create table if not exists public.profile_field_sources (
  user_id uuid not null references auth.users(id) on delete cascade,
  field text not null,
  value text,
  source text not null check (source in ('linkedin','manual')),
  approved boolean not null default false,
  edited_by_user boolean not null default false,
  imported_at timestamptz,
  primary key (user_id, field)
);

-- Server-only: tokens must never be readable from the browser.
alter table public.linkedin_identities enable row level security;
alter table public.profile_field_sources enable row level security;
create policy "own field sources" on public.profile_field_sources
  for select using (auth.uid() = user_id);
-- No policies on linkedin_identities: only the service role (server) can access it.
