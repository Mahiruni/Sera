-- Phase 2: authenticated professional follows and mutual connection requests.
create table if not exists public.professional_follows (
  follower_id uuid not null references auth.users(id) on delete cascade,
  followed_id uuid not null references auth.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (follower_id, followed_id),
  constraint no_self_follow check (follower_id <> followed_id)
);
create index if not exists professional_follows_followed_idx on public.professional_follows(followed_id, created_at desc);
alter table public.professional_follows enable row level security;
revoke all on public.professional_follows from anon;
grant select, insert, delete on public.professional_follows to authenticated;
create policy "Members can see follows" on public.professional_follows for select to authenticated using (true);
create policy "Members follow as themselves" on public.professional_follows for insert to authenticated with check (follower_id = (select auth.uid()));
create policy "Members unfollow as themselves" on public.professional_follows for delete to authenticated using (follower_id = (select auth.uid()));

create table if not exists public.professional_connection_requests (
  id uuid primary key default gen_random_uuid(),
  requester_id uuid not null references auth.users(id) on delete cascade,
  recipient_id uuid not null references auth.users(id) on delete cascade,
  status text not null default 'pending' check (status in ('pending','accepted','declined','withdrawn')),
  created_at timestamptz not null default now(),
  responded_at timestamptz,
  constraint no_self_connection check (requester_id <> recipient_id),
  constraint one_directional_request unique (requester_id, recipient_id)
);
create index if not exists professional_connections_inbox_idx on public.professional_connection_requests(recipient_id, status, created_at desc);
create unique index if not exists professional_connections_one_pending_pair on public.professional_connection_requests (least(requester_id, recipient_id), greatest(requester_id, recipient_id)) where status = 'pending';
alter table public.professional_connection_requests enable row level security;
revoke all on public.professional_connection_requests from anon;
grant select, insert, update, delete on public.professional_connection_requests to authenticated;
create policy "Participants see connection requests" on public.professional_connection_requests for select to authenticated using (requester_id = (select auth.uid()) or recipient_id = (select auth.uid()));
create policy "Members initiate connection requests" on public.professional_connection_requests for insert to authenticated with check (requester_id = (select auth.uid()) and status = 'pending' and responded_at is null);
-- Do not grant unrestricted updates: only participants can mutate, and column-level
-- authorization is enforced by a trigger below.
create or replace function public.guard_professional_connection_request()
returns trigger language plpgsql security invoker set search_path = '' as $$
begin
  if new.id is distinct from old.id or new.requester_id is distinct from old.requester_id
     or new.recipient_id is distinct from old.recipient_id or new.created_at is distinct from old.created_at then
    raise exception 'Connection participants are immutable';
  end if;
  if old.status <> 'pending' then raise exception 'Connection request already resolved'; end if;
  if auth.uid() = old.recipient_id and new.status in ('accepted','declined') then
    new.responded_at := now();
  elsif auth.uid() = old.requester_id and new.status = 'withdrawn' then
    new.responded_at := now();
  else
    raise exception 'Invalid connection transition';
  end if;
  return new;
end; $$;
create trigger guard_professional_connection_request_before_update
before update on public.professional_connection_requests
for each row execute function public.guard_professional_connection_request();
create policy "Participants resolve pending requests" on public.professional_connection_requests
for update to authenticated using (status = 'pending' and (requester_id = (select auth.uid()) or recipient_id = (select auth.uid())))
with check (requester_id = (select auth.uid()) or recipient_id = (select auth.uid()));
