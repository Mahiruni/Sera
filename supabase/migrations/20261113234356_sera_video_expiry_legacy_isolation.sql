-- Expiring premium access and legacy notification isolation.
CREATE OR REPLACE FUNCTION sera_call_private.claim_push(p_id uuid)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare u uuid:=auth.uid(); r sera_call_private.sessions%rowtype;
begin
 select * into r from sera_call_private.sessions where id=p_id and caller=u and state='ringing' and media_mode='voice' and expires_at>now()+interval '3 seconds' for update;
 if not found or not sera_call_private.allowed(r.caller,r.callee) then return null; end if;
 if r.push_claimed_at is not null then return null; end if;
 update sera_call_private.sessions set push_claimed_at=now() where id=r.id;
 return jsonb_build_object('id',r.id,'conversation_id',r.conversation_id,'recipient',r.callee,'expires_at',r.expires_at);
end; $function$;
CREATE OR REPLACE FUNCTION sera_call_private.incoming(p_id uuid)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare u uuid:=auth.uid(); r sera_call_private.sessions%rowtype;
begin
 select * into r from sera_call_private.sessions where id=p_id and callee=u and state='ringing' and media_mode='voice' and expires_at>now();
 if not found or not sera_call_private.allowed(r.caller,r.callee) then return null; end if;
 return jsonb_build_object('id',r.id,'conversation_id',r.conversation_id,'caller',r.caller,'expires_at',r.expires_at);
end; $function$;
CREATE OR REPLACE FUNCTION sera_call_private.media_status(p_conversation uuid, p_id uuid DEFAULT NULL::uuid, p_video boolean DEFAULT false)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare u uuid:=auth.uid(); v uuid; r sera_call_private.sessions%rowtype;
begin
 select case when member_a=u then member_b else member_a end into v from public.professional_conversations where id=p_conversation and u in(member_a,member_b);
 if not sera_call_private.allowed(u,v) then return null; end if;
 perform sera_call_private.expire(u);
 select * into r from sera_call_private.sessions where conversation_id=p_conversation and u in(caller,callee) and (p_video or media_mode='voice')
 and ((p_id is not null and id=p_id) or (p_id is null and state in ('ringing','accepted'))) order by created_at desc limit 1;
 if not found then return null; end if;
 if r.state in ('ringing','accepted') then
   update sera_call_private.sessions set
    caller_seen_at=case when caller=u then now() else caller_seen_at end,
    callee_seen_at=case when callee=u then now() else callee_seen_at end
   where id=r.id and state in ('ringing','accepted') and
    ((caller=u and caller_seen_at<now()-interval '8 seconds') or (callee=u and callee_seen_at<now()-interval '8 seconds'))
   ;
   select * into r from sera_call_private.sessions where id=r.id and u in(caller,callee);
 end if;
 if r.media_mode='video' and r.state in ('ringing','accepted') and
 ((r.caller_video_quality<>'standard' and not sera_call_private.video_premium(r.caller)) or
  (r.callee_video_quality<>'standard' and not sera_call_private.video_premium(r.callee))) then
  update sera_call_private.sessions s set
    caller_video_quality=case when sera_call_private.video_premium(s.caller) then s.caller_video_quality else 'standard' end,
    callee_video_quality=case when sera_call_private.video_premium(s.callee) then s.callee_video_quality else 'standard' end
  where s.id=r.id returning * into r;
 end if;
 return to_jsonb(r);
end; $function$;
