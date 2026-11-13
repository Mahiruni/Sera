-- Record first media-connected acknowledgement for each authenticated participant.
alter table sera_call_private.sessions add column if not exists caller_connected_at timestamptz, add column if not exists callee_connected_at timestamptz;
create or replace function sera_call_private.confirm_media(p_conversation uuid,p_id uuid) returns boolean language plpgsql security definer set search_path='' as $$
declare u uuid:=auth.uid(); r sera_call_private.sessions%rowtype;
begin
 select * into r from sera_call_private.sessions where id=p_id and conversation_id=p_conversation and u in(caller,callee) for update;
 if not found or r.state<>'accepted' or r.expires_at<=now() or not sera_call_private.allowed(r.caller,r.callee) then return false; end if;
 update sera_call_private.sessions set caller_connected_at=case when caller=u then coalesce(caller_connected_at,now()) else caller_connected_at end,
 callee_connected_at=case when callee=u then coalesce(callee_connected_at,now()) else callee_connected_at end where id=p_id;
 return true;
end; $$;
revoke all on function sera_call_private.confirm_media(uuid,uuid) from public,anon,authenticated;
grant execute on function sera_call_private.confirm_media(uuid,uuid) to authenticated;
create or replace function public.sera_call_confirm_media(p_conversation uuid,p_id uuid) returns boolean language sql security invoker set search_path='' as $$ select sera_call_private.confirm_media(p_conversation,p_id) $$;
revoke all on function public.sera_call_confirm_media(uuid,uuid) from public,anon;
grant execute on function public.sera_call_confirm_media(uuid,uuid) to authenticated;
create or replace function sera_call_private.history(p_conversation uuid) returns jsonb language plpgsql security definer set search_path='' as $$
declare u uuid:=auth.uid(); v uuid;
begin
 select case when member_a=u then member_b else member_a end into v from public.professional_conversations where id=p_conversation and u in(member_a,member_b);
 if not sera_call_private.allowed(u,v) then return '[]'::jsonb; end if;
 perform sera_call_private.expire(u);
 return coalesce((select jsonb_agg(to_jsonb(t) order by t.created_at desc) from
 (select id,caller,callee,state,created_at,accepted_at,ended_at,
 case when caller_connected_at is not null and callee_connected_at is not null and ended_at is not null
 then greatest(0,floor(extract(epoch from ended_at-greatest(caller_connected_at,callee_connected_at))))::bigint else null end as connected_seconds
 from sera_call_private.sessions where conversation_id=p_conversation and u in(caller,callee) and state not in ('ringing','accepted') order by created_at desc limit 30)t),'[]'::jsonb);
end; $$;
