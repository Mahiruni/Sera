create table public.sera_space_messages(id uuid primary key default gen_random_uuid(),space_id uuid not null references public.sera_collaboration_spaces(id) on delete cascade,sender_id uuid not null references auth.users(id) on delete cascade,body text not null check(char_length(trim(body)) between 1 and 4000),created_at timestamptz not null default now());
create index sera_space_messages_timeline on public.sera_space_messages(space_id,created_at desc,id desc);
alter table public.sera_space_messages enable row level security;
revoke all on public.sera_space_messages from public,anon,authenticated;
create function public.sera_list_space_messages(p_space uuid,p_before timestamptz default null) returns table(id uuid,sender_id uuid,full_name text,body text,created_at timestamptz)
language sql stable security definer set search_path='' as $$
 select m.id,m.sender_id,coalesce(p.full_name,'SERA member'),m.body,m.created_at
 from public.sera_space_messages m left join public.profiles p on p.id=m.sender_id
 where m.space_id=p_space and (p_before is null or m.created_at<p_before)
 and exists(select 1 from public.sera_collaboration_members x where x.space_id=p_space and x.member_id=auth.uid() and x.status='active')
 and not exists(select 1 from public.professional_blocks b where (b.blocker_id=auth.uid() and b.blocked_id=m.sender_id) or (b.blocker_id=m.sender_id and b.blocked_id=auth.uid()))
 order by m.created_at desc,m.id desc limit 100;
$$;
create function public.sera_send_space_message(p_space uuid,p_body text) returns uuid language plpgsql security definer set search_path='' as $$
declare u uuid:=auth.uid(); v uuid;
begin
 if u is null or not exists(select 1 from auth.users where id=u and email_confirmed_at is not null) then raise exception 'Verified account required'; end if;
 if not exists(select 1 from public.sera_collaboration_members where space_id=p_space and member_id=u and status='active' for update) then raise exception 'Team membership required'; end if;
 perform pg_advisory_xact_lock(hashtextextended(u::text,4));
 if (select count(*) from public.sera_space_messages where sender_id=u and created_at>now()-interval '1 minute')>=30 then raise exception 'Message rate limit'; end if;
 insert into public.sera_space_messages(space_id,sender_id,body) values(p_space,u,trim(p_body)) returning id into v; return v;
end; $$;
revoke all on function public.sera_list_space_messages(uuid,timestamptz),public.sera_send_space_message(uuid,text) from public,anon;
grant execute on function public.sera_list_space_messages(uuid,timestamptz),public.sera_send_space_message(uuid,text) to authenticated;
