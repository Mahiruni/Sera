-- Presence uses server time and keeps the existing opt-out across devices.
create or replace function public.sera_presence_heartbeat(p_visibility text default null) returns text
language plpgsql security invoker set search_path='' as $$
declare v text;
begin
 if auth.uid() is null then raise exception 'Authentication required'; end if;
 if p_visibility is not null and p_visibility not in ('connections','hidden') then raise exception 'Invalid visibility'; end if;
 insert into public.professional_presence(member_id,last_seen_at,visibility)
 values(auth.uid(),now(),coalesce(p_visibility,'connections'))
 on conflict(member_id) do update set last_seen_at=now(),visibility=coalesce(p_visibility,professional_presence.visibility)
 returning visibility into v;
 return v;
end; $$;
revoke all on function public.sera_presence_heartbeat(text) from public,anon;
grant execute on function public.sera_presence_heartbeat(text) to authenticated;
-- Feed: accepted connections only; employer accounts never expose presence here.
create or replace function public.sera_feed_presence(p_members uuid[]) returns table(member_id uuid,online boolean)
language sql stable security definer set search_path='' as $$
 select p.member_id,true from public.professional_presence p join public.profiles f on f.id=p.member_id
 where auth.uid() is not null and cardinality(p_members)<=100 and p.member_id=any(p_members)
 and f.journey='candidate' and p.visibility='connections' and p.last_seen_at>now()-interval '90 seconds'
 and (p.member_id=auth.uid() or exists(select 1 from public.professional_connection_requests r where r.status='accepted' and ((r.requester_id=auth.uid() and r.recipient_id=p.member_id) or (r.recipient_id=auth.uid() and r.requester_id=p.member_id))))
 and not exists(select 1 from public.professional_blocks b where (b.blocker_id=auth.uid() and b.blocked_id=p.member_id) or (b.blocker_id=p.member_id and b.blocked_id=auth.uid()));
$$;
revoke all on function public.sera_feed_presence(uuid[]) from public,anon;
grant execute on function public.sera_feed_presence(uuid[]) to authenticated;
