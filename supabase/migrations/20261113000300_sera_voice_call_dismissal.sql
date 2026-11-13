-- Only a participant may claim dismissal of a real, no-longer-ringing call.
alter table sera_call_private.sessions add column if not exists dismissal_claimed_at timestamptz;
create or replace function sera_call_private.claim_dismissal(p_id uuid) returns jsonb
language plpgsql security definer set search_path='' as $$
declare u uuid:=auth.uid(); r sera_call_private.sessions%rowtype;
begin
 select * into r from sera_call_private.sessions where id=p_id and u in(caller,callee)
 and state<>'ringing' and push_claimed_at is not null
 and coalesce(ended_at,accepted_at)>now()-interval '2 minutes' for update;
 if not found or r.dismissal_claimed_at is not null then return null; end if;
 update sera_call_private.sessions set dismissal_claimed_at=now() where id=r.id;
 return jsonb_build_object('id',r.id,'recipients',jsonb_build_array(r.caller,r.callee),'expires_at',now()+interval '45 seconds');
end; $$;
revoke all on function sera_call_private.claim_dismissal(uuid) from public,anon,authenticated;
grant execute on function sera_call_private.claim_dismissal(uuid) to authenticated;
create or replace function public.sera_call_claim_dismissal(p_id uuid) returns jsonb
language sql security invoker set search_path='' as $$ select sera_call_private.claim_dismissal(p_id) $$;
revoke all on function public.sera_call_claim_dismissal(uuid) from public,anon;
grant execute on function public.sera_call_claim_dismissal(uuid) to authenticated;
