-- Expiring call leases, audio-only SDP and mandatory relay candidates.
alter table sera_call_private.sessions add column if not exists caller_seen_at timestamptz not null default now(), add column if not exists callee_seen_at timestamptz not null default now();
create index if not exists sera_call_live_expiry_idx on sera_call_private.sessions(expires_at) where state in ('ringing','accepted');
create or replace function sera_call_private.valid_sdp(s text) returns boolean language sql immutable security invoker set search_path='' as $$
 select s is not null and length(s)<=65536 and left(s,3)='v=0'
 and (select count(*) from regexp_split_to_table(s,chr(10)) l where l like 'm=%')=1
 and exists(select 1 from regexp_split_to_table(s,chr(10)) l where l like 'm=audio %')
 and exists(select 1 from regexp_split_to_table(s,chr(10)) l where rtrim(l,chr(13)) ~ '^a=candidate:.* typ relay( |$)')
 and not exists(select 1 from regexp_split_to_table(s,chr(10)) l where l like 'a=candidate:%' and rtrim(l,chr(13)) !~ ' typ relay( |$)');
$$;
revoke all on function sera_call_private.valid_sdp(text) from public,anon,authenticated;
alter table sera_call_private.sessions add constraint sera_call_audio_only_offer check(offer is null or sera_call_private.valid_sdp(offer)), add constraint sera_call_audio_only_answer check(answer is null or sera_call_private.valid_sdp(answer));
create or replace function sera_call_private.expire(u uuid) returns void language sql security definer set search_path='' as $$
 update sera_call_private.sessions set state=case when state='ringing' then 'missed' else 'ended' end, ended_at=now(),offer=null,answer=null
 where state in ('ringing','accepted') and (expires_at<=now() or (state='accepted' and least(caller_seen_at,callee_seen_at)<now()-interval '60 seconds')) and (u is null or u in(caller,callee));
$$;
create or replace function sera_call_private.status(p_conversation uuid,p_id uuid default null) returns jsonb language plpgsql security definer set search_path='' as $$
declare u uuid:=auth.uid(); v uuid; r sera_call_private.sessions%rowtype;
begin
 select case when member_a=u then member_b else member_a end into v from public.professional_conversations where id=p_conversation and u in(member_a,member_b);
 if not sera_call_private.allowed(u,v) then return null; end if;
 perform sera_call_private.expire(u);
 select * into r from sera_call_private.sessions where conversation_id=p_conversation and u in(caller,callee)
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

create or replace function sera_call_private.transition(p_conversation uuid,p_id uuid,p_action text,p_answer text default null) returns jsonb language plpgsql security definer set search_path='' as $$
declare u uuid:=auth.uid(); r sera_call_private.sessions%rowtype; next_state text;
begin
 select * into r from sera_call_private.sessions where id=p_id and conversation_id=p_conversation and u in(caller,callee) for update;
 if not found or not sera_call_private.allowed(r.caller,r.callee) then raise exception 'Call unavailable'; end if;
 if r.state not in ('ringing','accepted') then return to_jsonb(r); end if;
 if r.expires_at<=now() then next_state=case when r.state='ringing' then 'missed' else 'ended' end;
 elsif p_action='accept' and r.state='ringing' and u=r.callee and p_answer is not null and length(p_answer)<=65536 and left(p_answer,3)='v=0' then
   update sera_call_private.sessions set state='accepted',answer=p_answer,accepted_at=now(),caller_seen_at=now(),callee_seen_at=now(),expires_at=now()+interval '2 hours' where id=r.id returning * into r; return to_jsonb(r);
 elsif p_action='decline' and r.state='ringing' and u=r.callee then next_state='declined';
 elsif p_action='end' then next_state=case when r.state='accepted' then 'ended' when u=r.caller then 'cancelled' else 'declined' end;
 elsif p_action='fail' then next_state='failed';
 else raise exception 'Invalid call transition'; end if;
 update sera_call_private.sessions set state=next_state,ended_at=now(),offer=null,answer=null where id=r.id returning * into r; return to_jsonb(r);
end; $$;

-- A closed/crashed client cannot retain signalling data indefinitely.
do $$ begin
 if exists(select 1 from pg_extension where extname='pg_cron') then
  execute 'select cron.schedule(''sera-expire-voice-calls'',''* * * * *'',''select sera_call_private.expire(null::uuid)'')';
 end if;
end $$;
