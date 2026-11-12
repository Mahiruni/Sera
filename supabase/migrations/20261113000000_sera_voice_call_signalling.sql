-- SERA voice calling: private signalling, server-authoritative state and expiring relay credentials.
create schema if not exists sera_call_private;
revoke all on schema sera_call_private from public,anon,authenticated;
grant usage on schema sera_call_private to authenticated;
create table if not exists sera_call_private.settings (
 id boolean primary key default true check(id), enabled boolean not null default false,
 turn_url text, turn_secret text, pilot_members uuid[] not null default '{}'
);
insert into sera_call_private.settings(id) values(true) on conflict do nothing;
create table if not exists sera_call_private.sessions (
 id uuid primary key, conversation_id uuid not null references public.professional_conversations(id) on delete cascade,
 caller uuid not null references auth.users(id) on delete cascade,
 callee uuid not null references auth.users(id) on delete cascade,
 state text not null check(state in ('ringing','accepted','declined','cancelled','ended','missed','failed')),
 offer text, answer text, created_at timestamptz not null default now(),
 accepted_at timestamptz, ended_at timestamptz, expires_at timestamptz not null,
 check(caller<>callee), check(length(offer)<=65536), check(length(answer)<=65536)
);
create index if not exists sera_call_caller_idx on sera_call_private.sessions(caller,created_at desc);
create index if not exists sera_call_callee_idx on sera_call_private.sessions(callee,created_at desc);
create index if not exists sera_call_conversation_idx on sera_call_private.sessions(conversation_id,created_at desc);
alter table sera_call_private.sessions enable row level security;
alter table sera_call_private.settings enable row level security;
revoke all on all tables in schema sera_call_private from public,anon,authenticated;
create or replace function sera_call_private.allowed(u uuid,v uuid) returns boolean language sql stable security definer set search_path='' as $$
 select u is not null and exists(select 1 from auth.users where id=u and email_confirmed_at is not null)
 and exists(select 1 from auth.users where id=v and email_confirmed_at is not null)
 and not exists(select 1 from public.professional_blocks b where (b.blocker_id=u and b.blocked_id=v) or (b.blocker_id=v and b.blocked_id=u))
 and exists(select 1 from sera_call_private.settings s where s.id and s.enabled and s.turn_url is not null and s.turn_secret is not null
 and u=any(s.pilot_members) and v=any(s.pilot_members));
$$;
create or replace function sera_call_private.config(p_conversation uuid) returns jsonb language plpgsql security definer set search_path='' as $$
declare u uuid:=auth.uid(); v uuid; s sera_call_private.settings%rowtype; uname text;
begin
 select case when member_a=u then member_b else member_a end into v from public.professional_conversations where id=p_conversation and u in(member_a,member_b);
 if not sera_call_private.allowed(u,v) then return jsonb_build_object('enabled',false); end if;
 select * into s from sera_call_private.settings where id;
 uname=(extract(epoch from now()+interval '2 hours')::bigint)::text||':'||u::text;
 return jsonb_build_object('enabled',true,'iceServers',jsonb_build_array(jsonb_build_object('urls',s.turn_url,'username',uname,'credential',encode(extensions.hmac(uname,s.turn_secret,'sha1'),'base64'))));
end; $$;
create or replace function sera_call_private.expire(u uuid) returns void language sql security definer set search_path='' as $$
 update sera_call_private.sessions set state=case when state='ringing' then 'missed' else 'ended' end, ended_at=now(),offer=null,answer=null
 where state in ('ringing','accepted') and expires_at<=now() and u in(caller,callee);
$$;
create or replace function sera_call_private.start(p_conversation uuid,p_id uuid,p_offer text) returns jsonb language plpgsql security definer set search_path='' as $$
declare u uuid:=auth.uid(); v uuid; r sera_call_private.sessions%rowtype;
begin
 select case when member_a=u then member_b else member_a end into v from public.professional_conversations where id=p_conversation and u in(member_a,member_b);
 if not sera_call_private.allowed(u,v) then raise exception 'Voice calling is unavailable'; end if;
 if p_id is null or p_offer is null or length(p_offer)>65536 or left(p_offer,3)<>'v=0' then raise exception 'Invalid call offer'; end if;
 perform pg_advisory_xact_lock(hashtextextended(least(u,v)::text,20261113));
 perform pg_advisory_xact_lock(hashtextextended(greatest(u,v)::text,20261113));
 perform sera_call_private.expire(u); perform sera_call_private.expire(v);
 select * into r from sera_call_private.sessions where id=p_id and caller=u and conversation_id=p_conversation;
 if found then return to_jsonb(r); end if;
 if exists(select 1 from sera_call_private.sessions where state in ('ringing','accepted') and (caller in(u,v) or callee in(u,v))) then raise exception 'Already in a call'; end if;
 if (select count(*) from sera_call_private.sessions where caller=u and created_at>now()-interval '1 minute')>=4 then raise exception 'Too many call attempts'; end if;
 insert into sera_call_private.sessions(id,conversation_id,caller,callee,state,offer,expires_at) values(p_id,p_conversation,u,v,'ringing',p_offer,now()+interval '45 seconds') returning * into r;
 return to_jsonb(r);
end; $$;
create or replace function sera_call_private.status(p_conversation uuid,p_id uuid default null) returns jsonb language plpgsql security definer set search_path='' as $$
declare u uuid:=auth.uid(); v uuid; r sera_call_private.sessions%rowtype;
begin
 select case when member_a=u then member_b else member_a end into v from public.professional_conversations where id=p_conversation and u in(member_a,member_b);
 if not sera_call_private.allowed(u,v) then return null; end if;
 perform sera_call_private.expire(u);
 select * into r from sera_call_private.sessions where conversation_id=p_conversation and u in(caller,callee)
 and ((p_id is not null and id=p_id) or (p_id is null and state in ('ringing','accepted'))) order by created_at desc limit 1;
 if not found then return null; end if; return to_jsonb(r);
end; $$;
create or replace function sera_call_private.transition(p_conversation uuid,p_id uuid,p_action text,p_answer text default null) returns jsonb language plpgsql security definer set search_path='' as $$
declare u uuid:=auth.uid(); r sera_call_private.sessions%rowtype; next_state text;
begin
 select * into r from sera_call_private.sessions where id=p_id and conversation_id=p_conversation and u in(caller,callee) for update;
 if not found or not sera_call_private.allowed(r.caller,r.callee) then raise exception 'Call unavailable'; end if;
 if r.state not in ('ringing','accepted') then return to_jsonb(r); end if;
 if r.expires_at<=now() then next_state=case when r.state='ringing' then 'missed' else 'ended' end;
 elsif p_action='accept' and r.state='ringing' and u=r.callee and p_answer is not null and length(p_answer)<=65536 and left(p_answer,3)='v=0' then
   update sera_call_private.sessions set state='accepted',answer=p_answer,accepted_at=now(),expires_at=now()+interval '2 hours' where id=r.id returning * into r; return to_jsonb(r);
 elsif p_action='decline' and r.state='ringing' and u=r.callee then next_state='declined';
 elsif p_action='end' then next_state=case when r.state='accepted' then 'ended' when u=r.caller then 'cancelled' else 'declined' end;
 elsif p_action='fail' then next_state='failed';
 else raise exception 'Invalid call transition'; end if;
 update sera_call_private.sessions set state=next_state,ended_at=now(),offer=null,answer=null where id=r.id returning * into r; return to_jsonb(r);
end; $$;
create or replace function sera_call_private.history(p_conversation uuid) returns jsonb language plpgsql security definer set search_path='' as $$
declare u uuid:=auth.uid(); v uuid;
begin
 select case when member_a=u then member_b else member_a end into v from public.professional_conversations where id=p_conversation and u in(member_a,member_b);
 if not sera_call_private.allowed(u,v) then return '[]'::jsonb; end if;
 perform sera_call_private.expire(u);
 return coalesce((select jsonb_agg(to_jsonb(t) order by t.created_at desc) from (select id,caller,callee,state,created_at,accepted_at,ended_at from sera_call_private.sessions where conversation_id=p_conversation and u in(caller,callee) and state not in ('ringing','accepted') order by created_at desc limit 30)t),'[]'::jsonb);
end; $$;
revoke all on all functions in schema sera_call_private from public,anon,authenticated;
grant execute on function sera_call_private.config(uuid),sera_call_private.start(uuid,uuid,text),sera_call_private.status(uuid,uuid),sera_call_private.transition(uuid,uuid,text,text),sera_call_private.history(uuid) to authenticated;
create or replace function public.sera_call_config(p_conversation uuid) returns jsonb language sql security invoker set search_path='' as $$ select sera_call_private.config(p_conversation) $$;
create or replace function public.sera_call_start(p_conversation uuid,p_id uuid,p_offer text) returns jsonb language sql security invoker set search_path='' as $$ select sera_call_private.start(p_conversation,p_id,p_offer) $$;
create or replace function public.sera_call_status(p_conversation uuid,p_id uuid default null) returns jsonb language sql security invoker set search_path='' as $$ select sera_call_private.status(p_conversation,p_id) $$;
create or replace function public.sera_call_transition(p_conversation uuid,p_id uuid,p_action text,p_answer text default null) returns jsonb language sql security invoker set search_path='' as $$ select sera_call_private.transition(p_conversation,p_id,p_action,p_answer) $$;
create or replace function public.sera_call_history(p_conversation uuid) returns jsonb language sql security invoker set search_path='' as $$ select sera_call_private.history(p_conversation) $$;
revoke all on function public.sera_call_config(uuid),public.sera_call_start(uuid,uuid,text),public.sera_call_status(uuid,uuid),public.sera_call_transition(uuid,uuid,text,text),public.sera_call_history(uuid) from public,anon;
grant execute on function public.sera_call_config(uuid),public.sera_call_start(uuid,uuid,text),public.sera_call_status(uuid,uuid),public.sera_call_transition(uuid,uuid,text,text),public.sera_call_history(uuid) to authenticated;
