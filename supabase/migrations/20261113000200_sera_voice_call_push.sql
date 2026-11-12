-- Authoritative, expiring incoming-call push claims; no SDP or TURN credentials in push payloads.
alter table sera_call_private.sessions add column if not exists push_claimed_at timestamptz;
create or replace function sera_call_private.claim_push(p_id uuid) returns jsonb language plpgsql security definer set search_path='' as $$
declare u uuid:=auth.uid(); r sera_call_private.sessions%rowtype;
begin
 select * into r from sera_call_private.sessions where id=p_id and caller=u and state='ringing' and expires_at>now()+interval '3 seconds' for update;
 if not found or not sera_call_private.allowed(r.caller,r.callee) then return null; end if;
 if r.push_claimed_at is not null then return null; end if;
 update sera_call_private.sessions set push_claimed_at=now() where id=r.id;
 return jsonb_build_object('id',r.id,'conversation_id',r.conversation_id,'recipient',r.callee,'expires_at',r.expires_at);
end; $$;
create or replace function sera_call_private.incoming(p_id uuid) returns jsonb language plpgsql security definer set search_path='' as $$
declare u uuid:=auth.uid(); r sera_call_private.sessions%rowtype;
begin
 select * into r from sera_call_private.sessions where id=p_id and callee=u and state='ringing' and expires_at>now();
 if not found or not sera_call_private.allowed(r.caller,r.callee) then return null; end if;
 return jsonb_build_object('id',r.id,'conversation_id',r.conversation_id,'caller',r.caller,'expires_at',r.expires_at);
end; $$;
revoke all on function sera_call_private.claim_push(uuid),sera_call_private.incoming(uuid) from public,anon,authenticated;
grant execute on function sera_call_private.claim_push(uuid),sera_call_private.incoming(uuid) to authenticated;
create or replace function public.sera_call_claim_push(p_id uuid) returns jsonb language sql security invoker set search_path='' as $$ select sera_call_private.claim_push(p_id) $$;
create or replace function public.sera_call_incoming(p_id uuid) returns jsonb language sql security invoker set search_path='' as $$ select sera_call_private.incoming(p_id) $$;
revoke all on function public.sera_call_claim_push(uuid),public.sera_call_incoming(uuid) from public,anon;
grant execute on function public.sera_call_claim_push(uuid),public.sera_call_incoming(uuid) to authenticated;
