-- One caller-led ICE restart at a time; both clients must advertise support.
alter table sera_call_private.sessions
 add column if not exists caller_restart_ready boolean not null default false,
 add column if not exists callee_restart_ready boolean not null default false,
 add column if not exists restart_requested boolean not null default false,
 add column if not exists restart_generation integer not null default 0,
 add column if not exists restart_offer text,
 add column if not exists restart_answer text,
 add column if not exists restart_at timestamptz;
alter table sera_call_private.sessions add constraint sera_restart_offer_relay check(restart_offer is null or sera_call_private.valid_sdp(restart_offer)),
 add constraint sera_restart_answer_relay check(restart_answer is null or sera_call_private.valid_sdp(restart_answer));
create or replace function sera_call_private.clear_restart() returns trigger language plpgsql security invoker set search_path='' as $$
begin
 if new.state not in ('ringing','accepted') then new.restart_offer=null;new.restart_answer=null;new.restart_requested=false; end if;
 return new;
end; $$;
drop trigger if exists sera_clear_restart on sera_call_private.sessions;
create trigger sera_clear_restart before update on sera_call_private.sessions for each row execute function sera_call_private.clear_restart();
create or replace function sera_call_private.restart(p_conversation uuid,p_id uuid,p_action text,p_generation integer default 0,p_sdp text default null)
returns jsonb language plpgsql security definer set search_path='' as $$
declare u uuid:=auth.uid(); r sera_call_private.sessions%rowtype;
begin
 select * into r from sera_call_private.sessions where id=p_id and conversation_id=p_conversation and u in(caller,callee) for update;
 if not found or r.state<>'accepted' or r.expires_at<=now() or not sera_call_private.allowed(r.caller,r.callee) then return null; end if;
 if p_action='ready' then
  update sera_call_private.sessions set caller_restart_ready=caller_restart_ready or caller=u,callee_restart_ready=callee_restart_ready or callee=u where id=r.id returning * into r;
 elsif not(r.caller_restart_ready and r.callee_restart_ready) then return null;
 elsif p_action='request' then
  if p_generation<>r.restart_generation or r.restart_generation>=3 or (r.restart_offer is not null and r.restart_answer is null) then return to_jsonb(r); end if;
  if r.restart_at is null or r.restart_at<=now()-interval '8 seconds' then
   update sera_call_private.sessions set restart_requested=true where id=r.id returning * into r;
  end if;
 elsif p_action='offer' and u=r.caller then
  if p_generation=r.restart_generation and p_sdp=r.restart_offer then return to_jsonb(r); end if;
  if p_generation<>r.restart_generation+1 or not r.restart_requested or p_generation>3 or not sera_call_private.valid_sdp(p_sdp)
  or (r.restart_offer is not null and r.restart_answer is null) then return null; end if;
  if substring(p_sdp from 'a=ice-ufrag:([^\r\n]+)') is null or substring(p_sdp from 'a=ice-ufrag:([^\r\n]+)')=substring(coalesce(r.restart_offer,r.offer) from 'a=ice-ufrag:([^\r\n]+)') then return null; end if;
  update sera_call_private.sessions set restart_generation=p_generation,restart_offer=p_sdp,restart_answer=null,restart_requested=false,restart_at=now() where id=r.id returning * into r;
 elsif p_action='answer' and u=r.callee then
  if p_generation<>r.restart_generation or r.restart_offer is null or not sera_call_private.valid_sdp(p_sdp) then return null; end if;
  if r.restart_answer is not null then return case when p_sdp=r.restart_answer then to_jsonb(r) else null end; end if;
  update sera_call_private.sessions set restart_answer=p_sdp where id=r.id returning * into r;
 else return null;
 end if;
 return to_jsonb(r);
end; $$;
revoke all on function sera_call_private.restart(uuid,uuid,text,integer,text) from public,anon,authenticated;
grant execute on function sera_call_private.restart(uuid,uuid,text,integer,text) to authenticated;
create or replace function public.sera_call_restart(p_conversation uuid,p_id uuid,p_action text,p_generation integer default 0,p_sdp text default null)
returns jsonb language sql security invoker set search_path='' as $$ select sera_call_private.restart(p_conversation,p_id,p_action,p_generation,p_sdp) $$;
revoke all on function public.sera_call_restart(uuid,uuid,text,integer,text) from public,anon;
grant execute on function public.sera_call_restart(uuid,uuid,text,integer,text) to authenticated;
