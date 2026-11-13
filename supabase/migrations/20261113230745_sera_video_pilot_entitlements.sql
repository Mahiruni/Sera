-- Video pilot is off until the new clients and media tests are verified.
-- Premium is per participant's own outgoing camera, not a shared paid call.
alter table sera_call_private.settings add column if not exists video_enabled boolean not null default false;
create table if not exists sera_call_private.video_entitlements(
 member_id uuid primary key references auth.users(id) on delete cascade,
 valid_until timestamptz not null,
 updated_at timestamptz not null default now()
);
create table if not exists sera_call_private.video_clients(
 member_id uuid primary key references auth.users(id) on delete cascade,
 seen_at timestamptz not null default now()
);
alter table sera_call_private.video_entitlements enable row level security;
alter table sera_call_private.video_clients enable row level security;
revoke all on sera_call_private.video_entitlements,sera_call_private.video_clients from public,anon,authenticated;
-- No client-side entitlement writes; a verified payment webhook or authorized admin must grant access.
create or replace function sera_call_private.video_premium(u uuid) returns boolean language sql stable security definer set search_path='' as $$
 select exists(select 1 from sera_call_private.video_entitlements where member_id=u and valid_until>now());
$$;
revoke all on function sera_call_private.video_premium(uuid) from public,anon,authenticated;
alter table sera_call_private.sessions
 add column if not exists media_mode text not null default 'voice' check(media_mode in ('voice','video')),
 add column if not exists caller_video_quality text not null default 'standard' check(caller_video_quality in ('standard','hd','super_hd')),
 add column if not exists callee_video_quality text not null default 'standard' check(callee_video_quality in ('standard','hd','super_hd'));
create or replace function sera_call_private.valid_media_sdp(s text,m text) returns boolean language sql immutable security invoker set search_path='' as $$
 select case when m='voice' then sera_call_private.valid_sdp(s) when m='video' then
 s is not null and length(s)<=65536 and left(s,3)='v=0'
 and (select count(*) from regexp_split_to_table(s,chr(10)) l where l like 'm=%')=2
 and (select count(*) from regexp_split_to_table(s,chr(10)) l where l like 'm=audio %')=1
 and (select count(*) from regexp_split_to_table(s,chr(10)) l where l like 'm=video %')=1
 and exists(select 1 from regexp_split_to_table(s,chr(10)) l where rtrim(l,chr(13)) ~ '^a=candidate:.* typ relay( |$)')
 and not exists(select 1 from regexp_split_to_table(s,chr(10)) l where l like 'a=candidate:%' and rtrim(l,chr(13)) !~ ' typ relay( |$)')
 else false end;
$$;
revoke all on function sera_call_private.valid_media_sdp(text,text) from public,anon,authenticated;
alter table sera_call_private.sessions drop constraint sera_call_audio_only_offer,drop constraint sera_call_audio_only_answer,drop constraint sera_restart_offer_relay,drop constraint sera_restart_answer_relay;
alter table sera_call_private.sessions add constraint sera_call_media_offer check(offer is null or sera_call_private.valid_media_sdp(offer,media_mode)),add constraint sera_call_media_answer check(answer is null or sera_call_private.valid_media_sdp(answer,media_mode)),add constraint sera_restart_media_offer check(restart_offer is null or sera_call_private.valid_media_sdp(restart_offer,media_mode)),add constraint sera_restart_media_answer check(restart_answer is null or sera_call_private.valid_media_sdp(restart_answer,media_mode));
create or replace function sera_call_private.video_ready() returns boolean language plpgsql security definer set search_path='' as $$
declare u uuid:=auth.uid();
begin
 if not exists(select 1 from auth.users where id=u and email_confirmed_at is not null and (banned_until is null or banned_until<=now())) or not exists(select 1 from sera_call_private.settings where id and enabled and video_enabled) then return false;end if;
 insert into sera_call_private.video_clients(member_id,seen_at) values(u,now()) on conflict(member_id) do update set seen_at=excluded.seen_at;
 return true;
end;$$;
create or replace function sera_call_private.video_access(p_conversation uuid) returns jsonb language plpgsql stable security definer set search_path='' as $$
declare u uuid:=auth.uid();v uuid;enabled boolean;
begin
 select case when member_a=u then member_b else member_a end into v from public.professional_conversations where id=p_conversation and u in(member_a,member_b);
 enabled:=sera_call_private.allowed(u,v) and exists(select 1 from sera_call_private.settings where id and enabled and video_enabled);
 return jsonb_build_object('enabled',enabled,'premium',enabled and sera_call_private.video_premium(u),'peer_ready',enabled and exists(select 1 from sera_call_private.video_clients where member_id=v and seen_at>now()-interval '60 seconds'));
end;$$;
create or replace function sera_call_private.start_video(p_conversation uuid,p_id uuid,p_offer text,p_quality text) returns jsonb language plpgsql security definer set search_path='' as $$
declare u uuid:=auth.uid(); v uuid; r sera_call_private.sessions%rowtype;
begin
 select case when member_a=u then member_b else member_a end into v from public.professional_conversations where id=p_conversation and u in(member_a,member_b);
 if not sera_call_private.allowed(u,v) then raise exception 'Voice calling is unavailable'; end if;
 if not exists(select 1 from sera_call_private.settings where id and enabled and video_enabled) then raise exception 'Video calling is unavailable'; end if;
 if not exists(select 1 from sera_call_private.video_clients where member_id=v and seen_at>now()-interval '60 seconds') then raise exception 'Recipient video client is unavailable'; end if;
 if p_quality is null or p_quality not in ('standard','hd','super_hd') or (p_quality<>'standard' and not sera_call_private.video_premium(u)) then raise exception 'Video subscription required';end if;
 if p_id is null or not sera_call_private.valid_media_sdp(p_offer,'video') then raise exception 'Invalid video offer';end if;
 perform pg_advisory_xact_lock(hashtextextended(least(u,v)::text,20261113));
 perform pg_advisory_xact_lock(hashtextextended(greatest(u,v)::text,20261113));
 perform sera_call_private.expire(u); perform sera_call_private.expire(v);
 select * into r from sera_call_private.sessions where id=p_id and caller=u and conversation_id=p_conversation;
 if found then if r.media_mode<>'video' or r.caller_video_quality<>p_quality then raise exception 'Call ID conflict';end if;return to_jsonb(r); end if;
 if exists(select 1 from sera_call_private.sessions where state in ('ringing','accepted') and (caller in(u,v) or callee in(u,v))) then raise exception 'Already in a call'; end if;
 if (select count(*) from sera_call_private.sessions where caller=u and created_at>now()-interval '1 minute')>=4 then raise exception 'Too many call attempts'; end if;
 insert into sera_call_private.sessions(id,conversation_id,caller,callee,state,offer,expires_at,media_mode,caller_video_quality,callee_video_quality) values(p_id,p_conversation,u,v,'ringing',p_offer,now()+interval '45 seconds','video',p_quality,'standard') returning * into r;
 return to_jsonb(r);
end; $$;
create or replace function sera_call_private.media_status(p_conversation uuid,p_id uuid default null,p_video boolean default false) returns jsonb language plpgsql security definer set search_path='' as $$
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
 return to_jsonb(r);
end; $$;

-- Legacy Android and browser clients never receive video SDP.
create or replace function sera_call_private.status(p_conversation uuid,p_id uuid default null) returns jsonb language sql security definer set search_path='' as $$select sera_call_private.media_status(p_conversation,p_id,false)$$;
create or replace function sera_call_private.pending_incoming() returns jsonb language sql stable security definer set search_path='' as $$
 select jsonb_build_object('id',s.id,'conversation_id',s.conversation_id,'caller',s.caller,'expires_at',s.expires_at)
 from sera_call_private.sessions s where s.callee=auth.uid() and s.state='ringing' and s.expires_at>now() and s.media_mode='voice' and sera_call_private.allowed(s.caller,s.callee) order by s.created_at desc limit 1;
$$;
create or replace function sera_call_private.media_pending_incoming() returns jsonb language sql stable security definer set search_path='' as $$
 select jsonb_build_object('id',s.id,'conversation_id',s.conversation_id,'caller',s.caller,'expires_at',s.expires_at,'media_mode',s.media_mode)
 from sera_call_private.sessions s where s.callee=auth.uid() and s.state='ringing' and s.expires_at>now() and sera_call_private.allowed(s.caller,s.callee) order by s.created_at desc limit 1;
$$;
-- Quality is authorized on every upgrade; downgrade is always permitted.
create or replace function sera_call_private.video_quality(p_conversation uuid,p_id uuid,p_quality text) returns jsonb language plpgsql security definer set search_path='' as $$
declare u uuid:=auth.uid();r sera_call_private.sessions%rowtype;
begin
 select * into r from sera_call_private.sessions where id=p_id and conversation_id=p_conversation and u in(caller,callee) and media_mode='video' and state in ('ringing','accepted') and expires_at>now() for update;
 if not found or not sera_call_private.allowed(r.caller,r.callee) or p_quality is null or p_quality not in ('standard','hd','super_hd') or (p_quality<>'standard' and not sera_call_private.video_premium(u)) then return null;end if;
 update sera_call_private.sessions set caller_video_quality=case when caller=u then p_quality else caller_video_quality end,callee_video_quality=case when callee=u then p_quality else callee_video_quality end where id=r.id returning * into r;
 return to_jsonb(r);
end;$$;
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
  if p_generation<>r.restart_generation+1 or not r.restart_requested or p_generation>3 or not sera_call_private.valid_media_sdp(p_sdp,r.media_mode)
  or (r.restart_offer is not null and r.restart_answer is null) then return null; end if;
  if substring(p_sdp from 'a=ice-ufrag:([^\r\n]+)') is null or substring(p_sdp from 'a=ice-ufrag:([^\r\n]+)')=substring(coalesce(r.restart_offer,r.offer) from 'a=ice-ufrag:([^\r\n]+)') then return null; end if;
  update sera_call_private.sessions set restart_generation=p_generation,restart_offer=p_sdp,restart_answer=null,restart_requested=false,restart_at=now() where id=r.id returning * into r;
 elsif p_action='answer' and u=r.callee then
  if p_generation<>r.restart_generation or r.restart_offer is null or not sera_call_private.valid_media_sdp(p_sdp,r.media_mode) then return null; end if;
  if r.restart_answer is not null then return case when p_sdp=r.restart_answer then to_jsonb(r) else null end; end if;
  update sera_call_private.sessions set restart_answer=p_sdp where id=r.id returning * into r;
 else return null;
 end if;
 return to_jsonb(r);
end; $$;
revoke all on function sera_call_private.video_ready() from public,anon,authenticated;
grant execute on function sera_call_private.video_ready() to authenticated;
create or replace function public.sera_call_video_ready() returns boolean language sql security invoker set search_path='' as $$select sera_call_private.video_ready()$$;
revoke all on function public.sera_call_video_ready() from public,anon;
grant execute on function public.sera_call_video_ready() to authenticated;
revoke all on function sera_call_private.video_access(uuid) from public,anon,authenticated;
grant execute on function sera_call_private.video_access(uuid) to authenticated;
create or replace function public.sera_call_video_access(p_conversation uuid) returns jsonb language sql security invoker set search_path='' as $$select sera_call_private.video_access(p_conversation)$$;
revoke all on function public.sera_call_video_access(uuid) from public,anon;
grant execute on function public.sera_call_video_access(uuid) to authenticated;
revoke all on function sera_call_private.start_video(uuid,uuid,text,text) from public,anon,authenticated;
grant execute on function sera_call_private.start_video(uuid,uuid,text,text) to authenticated;
create or replace function public.sera_call_start_video(p_conversation uuid,p_id uuid,p_offer text,p_quality text) returns jsonb language sql security invoker set search_path='' as $$select sera_call_private.start_video(p_conversation,p_id,p_offer,p_quality)$$;
revoke all on function public.sera_call_start_video(uuid,uuid,text,text) from public,anon;
grant execute on function public.sera_call_start_video(uuid,uuid,text,text) to authenticated;
revoke all on function sera_call_private.media_status(uuid,uuid,boolean) from public,anon,authenticated;
grant execute on function sera_call_private.media_status(uuid,uuid,boolean) to authenticated;
create or replace function public.sera_call_media_status(p_conversation uuid,p_id uuid default null,p_video boolean default true) returns jsonb language sql security invoker set search_path='' as $$select sera_call_private.media_status(p_conversation,p_id,p_video)$$;
revoke all on function public.sera_call_media_status(uuid,uuid,boolean) from public,anon;
grant execute on function public.sera_call_media_status(uuid,uuid,boolean) to authenticated;
revoke all on function sera_call_private.media_pending_incoming() from public,anon,authenticated;
grant execute on function sera_call_private.media_pending_incoming() to authenticated;
create or replace function public.sera_call_media_pending_incoming() returns jsonb language sql security invoker set search_path='' as $$select sera_call_private.media_pending_incoming()$$;
revoke all on function public.sera_call_media_pending_incoming() from public,anon;
grant execute on function public.sera_call_media_pending_incoming() to authenticated;
revoke all on function sera_call_private.video_quality(uuid,uuid,text) from public,anon,authenticated;
grant execute on function sera_call_private.video_quality(uuid,uuid,text) to authenticated;
create or replace function public.sera_call_video_quality(p_conversation uuid,p_id uuid,p_quality text) returns jsonb language sql security invoker set search_path='' as $$select sera_call_private.video_quality(p_conversation,p_id,p_quality)$$;
revoke all on function public.sera_call_video_quality(uuid,uuid,text) from public,anon;
grant execute on function public.sera_call_video_quality(uuid,uuid,text) to authenticated;
