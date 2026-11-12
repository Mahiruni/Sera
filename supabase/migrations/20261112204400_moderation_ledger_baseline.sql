-- The hosted moderation ledger predates the repository migration history.
-- Reproduce its minimum trusted schema on fresh databases, without changing hosted policies.
do $$ begin
 if to_regclass('public.moderation_decisions') is null then
  create table public.moderation_decisions (
   id uuid primary key default gen_random_uuid(), post_id uuid not null references public.posts(id) on delete cascade,
   action text not null check(action in ('approve','quarantine','remove','restore')),
   policy text not null, explanation text not null, actor_id uuid references auth.users(id), created_at timestamptz not null default now()
  );
  alter table public.moderation_decisions enable row level security;
  revoke all on public.moderation_decisions from public,anon,authenticated;
  grant select,insert,update,delete on public.moderation_decisions to service_role;
 end if;
end $$;