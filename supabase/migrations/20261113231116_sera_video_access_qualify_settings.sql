create or replace function sera_call_private.video_access(p_conversation uuid) returns jsonb language plpgsql stable security definer set search_path='' as $$
declare u uuid:=auth.uid();v uuid;is_enabled boolean;
begin
 select case when member_a=u then member_b else member_a end into v from public.professional_conversations where id=p_conversation and u in(member_a,member_b);
 is_enabled:=sera_call_private.allowed(u,v) and exists(select 1 from sera_call_private.settings s where s.id and s.enabled and s.video_enabled);
 return jsonb_build_object('enabled',is_enabled,'premium',is_enabled and sera_call_private.video_premium(u),'peer_ready',is_enabled and exists(select 1 from sera_call_private.video_clients where member_id=v and seen_at>now()-interval '60 seconds'));
end;$$;
