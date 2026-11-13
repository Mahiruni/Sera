-- Account-owned messaging controls. No mutation of message receipts.
create table public.sera_message_preferences (
 user_id uuid primary key references auth.users(id) on delete cascade,
 notifications_enabled boolean not null default true,
 quiet_enabled boolean not null default false,
 quiet_start integer not null default 1320 check (quiet_start between 0 and 1439),
 quiet_end integer not null default 420 check (quiet_end between 0 and 1439),
 quiet_timezone text not null default 'Africa/Addis_Ababa' check (length(quiet_timezone) between 1 and 80),
 updated_at timestamptz not null default now(),
 check (quiet_start<>quiet_end)
);
alter table public.sera_message_preferences enable row level security;
revoke all on public.sera_message_preferences from public,anon,authenticated;
grant select,insert,update on public.sera_message_preferences to authenticated;
grant select on public.sera_message_preferences to service_role;
create policy "Own message preferences" on public.sera_message_preferences for all to authenticated using (user_id=(select auth.uid())) with check (user_id=(select auth.uid()));

create table public.sera_conversation_preferences (
 user_id uuid not null references auth.users(id) on delete cascade,
 conversation_id uuid not null references public.professional_conversations(id) on delete cascade,
 pinned boolean not null default false,
 archived boolean not null default false,
 marked_unread boolean not null default false,
 muted_until timestamptz,
 updated_at timestamptz not null default now(),
 primary key(user_id,conversation_id)
);
alter table public.sera_conversation_preferences enable row level security;
revoke all on public.sera_conversation_preferences from public,anon,authenticated;
grant select,insert,update on public.sera_conversation_preferences to authenticated;
grant select on public.sera_conversation_preferences to service_role;
create policy "Own participant conversation preferences" on public.sera_conversation_preferences for all to authenticated
 using (user_id=(select auth.uid()) and exists(select 1 from public.professional_conversations c where c.id=conversation_id and (select auth.uid()) in(c.member_a,c.member_b)))
 with check (user_id=(select auth.uid()) and exists(select 1 from public.professional_conversations c where c.id=conversation_id and (select auth.uid()) in(c.member_a,c.member_b)));

create function public.sera_set_conversation_preference(p_conversation uuid,p_action text,p_value boolean default false,p_mute_minutes integer default 0)
returns void language plpgsql security invoker set search_path='' as $$
begin
 if (select auth.uid()) is null or not exists(select 1 from public.professional_conversations c where c.id=p_conversation and (select auth.uid()) in(c.member_a,c.member_b)) then raise exception 'Conversation unavailable'; end if;
 if p_action not in ('pin','archive','unread','mute') or p_action is null or p_value is null then raise exception 'Invalid action'; end if;
 if p_action='mute' and (p_mute_minutes is null or p_mute_minutes not in (0,60,480,1440,-1)) then raise exception 'Invalid mute interval'; end if;
 insert into public.sera_conversation_preferences(user_id,conversation_id) values((select auth.uid()),p_conversation) on conflict do nothing;
 update public.sera_conversation_preferences set
 pinned=case when p_action='pin' then p_value else pinned end,
 archived=case when p_action='archive' then p_value else archived end,
 marked_unread=case when p_action='unread' then p_value else marked_unread end,
 muted_until=case when p_action='mute' then case when p_mute_minutes=-1 then 'infinity'::timestamptz when p_mute_minutes=0 then null else now()+make_interval(mins=>p_mute_minutes) end else muted_until end,
 updated_at=now() where user_id=(select auth.uid()) and conversation_id=p_conversation;
end; $$;
revoke all on function public.sera_set_conversation_preference(uuid,text,boolean,integer) from public,anon;
grant execute on function public.sera_set_conversation_preference(uuid,text,boolean,integer) to authenticated;

create function public.sera_save_message_preferences(p_enabled boolean,p_quiet boolean,p_start integer,p_end integer,p_timezone text)
returns void language plpgsql security invoker set search_path='' as $$
begin
 if (select auth.uid()) is null then raise exception 'Authentication required'; end if;
 if p_timezone is null or not exists(select 1 from pg_catalog.pg_timezone_names where name=p_timezone) then raise exception 'Invalid timezone'; end if;
 insert into public.sera_message_preferences(user_id,notifications_enabled,quiet_enabled,quiet_start,quiet_end,quiet_timezone)
 values((select auth.uid()),p_enabled,p_quiet,p_start,p_end,p_timezone)
 on conflict(user_id) do update set notifications_enabled=excluded.notifications_enabled,quiet_enabled=excluded.quiet_enabled,quiet_start=excluded.quiet_start,quiet_end=excluded.quiet_end,quiet_timezone=excluded.quiet_timezone,updated_at=now();
end; $$;
revoke all on function public.sera_save_message_preferences(boolean,boolean,integer,integer,text) from public,anon;
grant execute on function public.sera_save_message_preferences(boolean,boolean,integer,integer,text) to authenticated;
