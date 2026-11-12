create function public.sera_open_talent_conversation(p_peer uuid) returns uuid language plpgsql security definer set search_path='' as $$
declare u uuid:=auth.uid(); v uuid;
begin
 if u is null or u=p_peer or not exists(select 1 from auth.users where id=u and email_confirmed_at is not null) then raise exception 'Verified account required'; end if;
 if exists(select 1 from public.professional_blocks b where (b.blocker_id=u and b.blocked_id=p_peer) or (b.blocker_id=p_peer and b.blocked_id=u)) then raise exception 'Conversation unavailable'; end if;
 if not exists(select 1 from public.professional_connection_requests r where r.status='accepted' and ((r.requester_id=u and r.recipient_id=p_peer) or (r.requester_id=p_peer and r.recipient_id=u))) then raise exception 'Accept a connection first'; end if;
 insert into public.professional_conversations(member_a,member_b) values(least(u,p_peer),greatest(u,p_peer)) on conflict(member_a,member_b) do nothing;
 select id into v from public.professional_conversations where member_a=least(u,p_peer) and member_b=greatest(u,p_peer);
 return v;
end; $$;
revoke all on function public.sera_open_talent_conversation(uuid) from public,anon;
grant execute on function public.sera_open_talent_conversation(uuid) to authenticated;
