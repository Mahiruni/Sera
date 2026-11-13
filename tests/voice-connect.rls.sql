begin;
create temporary table connect_fixture(a uuid,b uuid,c uuid,thread uuid,call_id uuid,offer text,restart text);
insert into connect_fixture values(gen_random_uuid(),gen_random_uuid(),gen_random_uuid(),gen_random_uuid(),gen_random_uuid(),
 'v=0'||chr(10)||'m=audio 9 UDP/TLS/RTP/SAVPF 111'||chr(10)||'a=ice-ufrag:old'||chr(10)||'a=candidate:1 1 udp 1 192.0.2.1 9999 typ relay',
 'v=0'||chr(10)||'m=audio 9 UDP/TLS/RTP/SAVPF 111'||chr(10)||'a=ice-ufrag:new'||chr(10)||'a=candidate:1 1 udp 1 192.0.2.1 9999 typ relay');
grant select on connect_fixture to authenticated;
insert into auth.users(id,email_confirmed_at) select unnest(array[a,b,c]),now() from connect_fixture;
update sera_call_private.settings set enabled=true,relay_provider='cloudflare',pilot_members='{}' where id;
insert into public.professional_conversations(id,member_a,member_b) select thread,least(a,b),greatest(a,b) from connect_fixture;
insert into sera_call_private.sessions(id,conversation_id,caller,callee,state,expires_at,offer)
select call_id,thread,a,b,'ringing',now()+interval '45 seconds',offer from connect_fixture;
select set_config('request.jwt.claims',json_build_object('sub',b)::text,true),set_config('request.jwt.claim.sub',b::text,true) from connect_fixture;
set local role authenticated;
do $test$ declare f record; r jsonb; begin
 select * into f from connect_fixture;
 r:=public.sera_call_pending_incoming();
 if r is null or r->>'id'<>f.call_id::text or r->>'conversation_id'<>f.thread::text or r?'offer' or r?'answer' then raise exception 'Recipient pointer unavailable or overexposed'; end if;
 if public.sera_call_restart(f.thread,f.call_id,'ready',0,null) is not null then raise exception 'Ringing restart allowed';end if;
end $test$;
reset role;
select set_config('request.jwt.claims',json_build_object('sub',c)::text,true),set_config('request.jwt.claim.sub',c::text,true) from connect_fixture;
set local role authenticated;
do $test$ begin
 if public.sera_call_pending_incoming() is not null then raise exception 'Outsider sees incoming call'; end if;
 if public.sera_call_restart((select thread from connect_fixture),(select call_id from connect_fixture),'ready',0,null) is not null then raise exception 'Outsider restart allowed';end if;
end $test$;
reset role;
update auth.users set banned_until=now()+interval '1 hour' where id=(select b from connect_fixture);
select set_config('request.jwt.claims',json_build_object('sub',b)::text,true),set_config('request.jwt.claim.sub',b::text,true) from connect_fixture;
set local role authenticated;
do $test$ begin
 if public.sera_call_pending_incoming() is not null then raise exception 'Banned recipient sees incoming call';end if;
end $test$;
reset role;
update auth.users set banned_until=null where id=(select b from connect_fixture);
update sera_call_private.sessions set state='accepted',accepted_at=now(),expires_at=now()+interval '2 hours',answer=offer where id=(select call_id from connect_fixture);
set local role authenticated;
do $test$ declare f record; begin
 select * into f from connect_fixture;
 if public.sera_call_pending_incoming() is not null then raise exception 'Accepted call returned as ringing';end if;
 if public.sera_call_restart(f.thread,f.call_id,'request',0,null) is not null then raise exception 'Restart allowed before handshake';end if;
 if public.sera_call_restart(f.thread,f.call_id,'ready',0,null) is null then raise exception 'Callee readiness failed';end if;
end $test$;
reset role;
select set_config('request.jwt.claims',json_build_object('sub',a)::text,true),set_config('request.jwt.claim.sub',a::text,true) from connect_fixture;
set local role authenticated;
do $test$ declare f record; r jsonb; begin
 select * into f from connect_fixture;
 perform public.sera_call_restart(f.thread,f.call_id,'ready',0,null);
 r:=public.sera_call_restart(f.thread,f.call_id,'request',0,null);
 if r is null or not (r->>'restart_requested')::boolean then raise exception 'Restart request failed';end if;
 if public.sera_call_restart(f.thread,f.call_id,'offer',1,f.offer) is not null then raise exception 'Old ICE credentials accepted';end if;
 r:=public.sera_call_restart(f.thread,f.call_id,'offer',1,f.restart);
 if r is null or (r->>'restart_generation')::int<>1 then raise exception 'Restart offer failed';end if;
 if public.sera_call_restart(f.thread,f.call_id,'answer',1,f.restart) is not null then raise exception 'Caller answered own restart';end if;
end $test$;
reset role;
select set_config('request.jwt.claims',json_build_object('sub',b)::text,true),set_config('request.jwt.claim.sub',b::text,true) from connect_fixture;
set local role authenticated;
do $test$ declare f record; r jsonb; begin
 select * into f from connect_fixture;
 r:=public.sera_call_restart(f.thread,f.call_id,'answer',1,f.restart);
 if r is null or r->>'restart_answer'<>f.restart then raise exception 'Restart answer failed';end if;
 if public.sera_call_restart(f.thread,f.call_id,'answer',1,f.restart) is null then raise exception 'Idempotent retry failed';end if;
end $test$;
reset role;
update sera_call_private.sessions set state='ended',ended_at=now(),offer=null,answer=null where id=(select call_id from connect_fixture);
do $test$ begin
 if exists(select 1 from sera_call_private.sessions where id=(select call_id from connect_fixture) and (restart_offer is not null or restart_answer is not null or restart_requested)) then raise exception 'Terminal restart SDP retained';end if;
 if has_function_privilege('anon','public.sera_call_pending_incoming()','EXECUTE') then raise exception 'Anonymous pending lookup granted';end if;
end $test$;
rollback;
select 'PASS: global incoming discovery, bans, ICE restart permissions, idempotence and terminal cleanup' as result;
