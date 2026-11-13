-- A recipient can discover a ringing invitation without already opening its thread.
-- Return only a minimal pointer, never SDP or TURN credentials.
create or replace function sera_call_private.pending_incoming() returns jsonb language sql stable security definer set search_path='' as $$
 select jsonb_build_object('id',s.id,'conversation_id',s.conversation_id,'caller',s.caller,'expires_at',s.expires_at)
 from sera_call_private.sessions s
 where s.callee=auth.uid() and s.state='ringing' and s.expires_at>now()
 and sera_call_private.allowed(s.caller,s.callee)
 order by s.created_at desc limit 1;
$$;
revoke all on function sera_call_private.pending_incoming() from public,anon,authenticated;
grant execute on function sera_call_private.pending_incoming() to authenticated;
create or replace function public.sera_call_pending_incoming() returns jsonb language sql stable security invoker set search_path='' as $$ select sera_call_private.pending_incoming() $$;
revoke all on function public.sera_call_pending_incoming() from public,anon;
grant execute on function public.sera_call_pending_incoming() to authenticated;
