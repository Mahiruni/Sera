create table public.sera_events(id uuid primary key default gen_random_uuid(),owner_id uuid not null references auth.users(id) on delete cascade,title text not null check(char_length(trim(title)) between 3 and 120),description text not null default '' check(char_length(description)<=2000),location text not null check(char_length(trim(location)) between 2 and 180),starts_at timestamptz not null,capacity integer not null check(capacity between 1 and 500),cancelled boolean not null default false,created_at timestamptz not null default now());
create table public.sera_event_attendees(event_id uuid not null references public.sera_events(id) on delete cascade,member_id uuid not null references auth.users(id) on delete cascade,created_at timestamptz not null default now(),primary key(event_id,member_id));
create index sera_events_start on public.sera_events(starts_at);
alter table public.sera_events enable row level security;alter table public.sera_event_attendees enable row level security;
revoke all on public.sera_events,public.sera_event_attendees from public,anon,authenticated;
create function public.sera_events_list(p_mine boolean default false) returns table(id uuid,owner_id uuid,host_name text,title text,description text,location text,starts_at timestamptz,capacity integer,attendees bigint,going boolean,cancelled boolean) language sql stable security definer set search_path='' as $$
select e.id,e.owner_id,coalesce(p.full_name,'SERA member'),e.title,e.description,e.location,e.starts_at,e.capacity,(select count(*) from public.sera_event_attendees a where a.event_id=e.id),exists(select 1 from public.sera_event_attendees a where a.event_id=e.id and a.member_id=auth.uid()),e.cancelled
from public.sera_events e left join public.profiles p on p.id=e.owner_id
where auth.uid() is not null and (case when p_mine then e.owner_id=auth.uid() or exists(select 1 from public.sera_event_attendees a where a.event_id=e.id and a.member_id=auth.uid()) else not e.cancelled and e.starts_at>now() end)
and not exists(select 1 from public.professional_blocks b where (b.blocker_id=auth.uid() and b.blocked_id=e.owner_id) or (b.blocker_id=e.owner_id and b.blocked_id=auth.uid()))
order by e.starts_at,e.id limit 100;
$$;
create function public.sera_create_event(p_title text,p_description text,p_location text,p_starts timestamptz,p_capacity integer) returns uuid language plpgsql security definer set search_path='' as $$
declare u uuid:=auth.uid();v uuid;
begin
if u is null or not exists(select 1 from auth.users where id=u and email_confirmed_at is not null) then raise exception 'Verified account required';end if;
if p_starts is null or p_starts<=now() or p_starts>now()+interval '2 years' then raise exception 'Choose a future event date';end if;
perform pg_advisory_xact_lock(hashtextextended(u::text,7));
if (select count(*) from public.sera_events where owner_id=u and not cancelled and starts_at>now())>=20 then raise exception 'Upcoming event limit reached';end if;
insert into public.sera_events(owner_id,title,description,location,starts_at,capacity) values(u,trim(p_title),trim(coalesce(p_description,'')),trim(p_location),p_starts,p_capacity) returning id into v;return v;
end;$$;
create function public.sera_event_rsvp(p_event uuid,p_going boolean) returns void language plpgsql security definer set search_path='' as $$
declare u uuid:=auth.uid();e public.sera_events%rowtype;
begin
if u is null or not exists(select 1 from auth.users where id=u and email_confirmed_at is not null) then raise exception 'Verified account required';end if;
select * into e from public.sera_events where id=p_event for update;
if not found then raise exception 'Event unavailable';end if;
if not p_going then delete from public.sera_event_attendees where event_id=p_event and member_id=u;return;end if;
if e.cancelled or e.starts_at<=now() or exists(select 1 from public.professional_blocks b where (b.blocker_id=u and b.blocked_id=e.owner_id) or (b.blocker_id=e.owner_id and b.blocked_id=u)) then raise exception 'Event unavailable';end if;
if exists(select 1 from public.sera_event_attendees where event_id=p_event and member_id=u) then return;end if;
if (select count(*) from public.sera_event_attendees where event_id=p_event)>=e.capacity then raise exception 'Event full';end if;
insert into public.sera_event_attendees(event_id,member_id) values(p_event,u);
end;$$;
create function public.sera_cancel_event(p_event uuid) returns void language plpgsql security definer set search_path='' as $$
begin update public.sera_events set cancelled=true where id=p_event and owner_id=auth.uid() and not cancelled;if not found then raise exception 'Event unavailable';end if;end;$$;
revoke all on function public.sera_events_list(boolean),public.sera_create_event(text,text,text,timestamptz,integer),public.sera_event_rsvp(uuid,boolean),public.sera_cancel_event(uuid) from public,anon;
grant execute on function public.sera_events_list(boolean),public.sera_create_event(text,text,text,timestamptz,integer),public.sera_event_rsvp(uuid,boolean),public.sera_cancel_event(uuid) to authenticated;
