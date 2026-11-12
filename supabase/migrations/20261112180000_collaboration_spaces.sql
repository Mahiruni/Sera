create table public.sera_collaboration_spaces(id uuid primary key default gen_random_uuid(),owner_id uuid not null references auth.users(id) on delete cascade,name text not null check(char_length(trim(name)) between 3 and 80),description text not null default '' check(char_length(description)<=500),created_at timestamptz not null default now());
create table public.sera_collaboration_members(space_id uuid not null references public.sera_collaboration_spaces(id) on delete cascade,member_id uuid not null references auth.users(id) on delete cascade,status text not null check(status in ('invited','active','declined')),created_at timestamptz not null default now(),primary key(space_id,member_id));
alter table public.sera_collaboration_spaces enable row level security;
alter table public.sera_collaboration_members enable row level security;
revoke all on public.sera_collaboration_spaces,public.sera_collaboration_members from public,anon,authenticated;
create function public.sera_list_spaces() returns table(id uuid,name text,description text,owner_id uuid,status text,member_count bigint) language sql stable security definer set search_path='' as $$
select s.id,s.name,s.description,s.owner_id,m.status,(select count(*) from public.sera_collaboration_members x where x.space_id=s.id and x.status='active') from public.sera_collaboration_spaces s join public.sera_collaboration_members m on m.space_id=s.id where m.member_id=auth.uid() and m.status in ('active','invited') order by s.created_at desc limit 100;
$$;
create function public.sera_create_space(p_name text,p_description text) returns uuid language plpgsql security definer set search_path='' as $$
declare u uuid:=auth.uid(); v uuid;
begin
 if u is null or not exists(select 1 from auth.users where id=u and email_confirmed_at is not null) then raise exception 'Verified account required'; end if;
 perform pg_advisory_xact_lock(hashtextextended(u::text,3));
 if (select count(*) from public.sera_collaboration_spaces where owner_id=u)>=20 then raise exception 'Space limit reached'; end if;
 insert into public.sera_collaboration_spaces(owner_id,name,description) values(u,trim(p_name),trim(coalesce(p_description,''))) returning id into v;
 insert into public.sera_collaboration_members(space_id,member_id,status) values(v,u,'active');return v;
end; $$;
create function public.sera_space_invite(p_space uuid,p_member uuid) returns void language plpgsql security definer set search_path='' as $$
declare u uuid:=auth.uid();
begin
 if u is null or u=p_member or not exists(select 1 from public.sera_collaboration_spaces where id=p_space and owner_id=u for update) then raise exception 'Owner access required'; end if;
 if not exists(select 1 from public.professional_connection_requests r where r.status='accepted' and ((r.requester_id=u and r.recipient_id=p_member) or (r.recipient_id=u and r.requester_id=p_member))) or exists(select 1 from public.professional_blocks b where (b.blocker_id=u and b.blocked_id=p_member) or (b.blocker_id=p_member and b.blocked_id=u)) then raise exception 'Accepted connection required'; end if;
 if (select count(*) from public.sera_collaboration_members where space_id=p_space and status in ('active','invited'))>=50 then raise exception 'Space member limit reached'; end if;
 insert into public.sera_collaboration_members(space_id,member_id,status) values(p_space,p_member,'invited') on conflict(space_id,member_id) do nothing;
end; $$;
create function public.sera_space_respond(p_space uuid,p_accept boolean) returns void language plpgsql security definer set search_path='' as $$
declare u uuid:=auth.uid(); v uuid;
begin
 select owner_id into v from public.sera_collaboration_spaces where id=p_space;
 if u is null or v is null or u=v then raise exception 'Invitation unavailable'; end if;
 if p_accept and exists(select 1 from public.professional_blocks b where (b.blocker_id=u and b.blocked_id=v) or (b.blocker_id=v and b.blocked_id=u)) then raise exception 'Invitation unavailable'; end if;
 update public.sera_collaboration_members set status=case when p_accept then 'active' else 'declined' end where space_id=p_space and member_id=u and status='invited';
 if not found then raise exception 'Invitation unavailable'; end if;
end; $$;
create function public.sera_space_remove(p_space uuid,p_member uuid) returns void language plpgsql security definer set search_path='' as $$
declare u uuid:=auth.uid(); v uuid;
begin
 select owner_id into v from public.sera_collaboration_spaces where id=p_space;
 if u is null or v is null or p_member=v or (u<>v and u<>p_member) then raise exception 'Action not permitted'; end if;
 delete from public.sera_collaboration_members where space_id=p_space and member_id=p_member;
end; $$;
create function public.sera_space_members(p_space uuid) returns table(member_id uuid,full_name text,status text) language sql stable security definer set search_path='' as $$
 select m.member_id,coalesce(p.full_name,'SERA member'),m.status from public.sera_collaboration_members m left join public.profiles p on p.id=m.member_id
 where m.space_id=p_space and exists(select 1 from public.sera_collaboration_members x where x.space_id=p_space and x.member_id=auth.uid() and x.status='active')
 and (m.status='active' or (m.status='invited' and exists(select 1 from public.sera_collaboration_spaces s where s.id=p_space and s.owner_id=auth.uid())))
 order by m.created_at limit 50;
$$;
revoke all on function public.sera_list_spaces(),public.sera_create_space(text,text),public.sera_space_invite(uuid,uuid),public.sera_space_respond(uuid,boolean),public.sera_space_remove(uuid,uuid),public.sera_space_members(uuid) from public,anon;
grant execute on function public.sera_list_spaces(),public.sera_create_space(text,text),public.sera_space_invite(uuid,uuid),public.sera_space_respond(uuid,boolean),public.sera_space_remove(uuid,uuid),public.sera_space_members(uuid) to authenticated;
