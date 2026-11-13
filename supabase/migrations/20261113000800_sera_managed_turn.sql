-- Managed relay support. No provider key is stored in the database or sent to clients.
alter table sera_call_private.settings add column if not exists relay_provider text not null default 'coturn' check(relay_provider in ('coturn','cloudflare'));
create or replace function sera_call_private.allowed(u uuid,v uuid) returns boolean language sql stable security definer set search_path='' as $$
 select u is not null and exists(select 1 from auth.users where id=u and email_confirmed_at is not null)
 and exists(select 1 from auth.users where id=v and email_confirmed_at is not null)
 and not exists(select 1 from public.professional_blocks b where (b.blocker_id=u and b.blocked_id=v) or (b.blocker_id=v and b.blocked_id=u))
 and exists(select 1 from sera_call_private.settings s where s.id and s.enabled
 and (s.relay_provider='cloudflare' or (s.turn_url is not null and s.turn_secret is not null))
 and u=any(s.pilot_members) and v=any(s.pilot_members));
$$;
create or replace function sera_call_private.config(p_conversation uuid) returns jsonb language plpgsql security definer set search_path='' as $$
declare u uuid:=auth.uid(); v uuid; s sera_call_private.settings%rowtype; uname text;
begin
 select case when member_a=u then member_b else member_a end into v from public.professional_conversations where id=p_conversation and u in(member_a,member_b);
 if not sera_call_private.allowed(u,v) then return jsonb_build_object('enabled',false); end if;
 select * into s from sera_call_private.settings where id;
 -- Old clients cannot use managed credentials. Fail closed rather than showing a broken call button.
 if s.relay_provider='cloudflare' then return jsonb_build_object('enabled',false);end if;
 uname=(extract(epoch from now()+interval '3 hours')::bigint)::text||':'||u::text;
 return jsonb_build_object('enabled',true,'iceServers',jsonb_build_array(jsonb_build_object('urls',s.turn_url,'username',uname,'credential',encode(extensions.hmac(uname,s.turn_secret,'sha1'),'base64'))));
end; $$;
create table if not exists sera_call_private.relay_issues(id bigint generated always as identity primary key, member_id uuid not null,issued_at timestamptz not null default now());
alter table sera_call_private.relay_issues enable row level security;
revoke all on sera_call_private.relay_issues from public,anon,authenticated;
create index if not exists sera_relay_issue_member_time on sera_call_private.relay_issues(member_id,issued_at);
create or replace function sera_call_private.relay_access(p_conversation uuid,p_issue boolean) returns text language plpgsql security definer set search_path='' as $$
declare u uuid:=auth.uid(); v uuid; provider text;
begin
 select case when member_a=u then member_b else member_a end into v from public.professional_conversations where id=p_conversation and u in(member_a,member_b);
 if not sera_call_private.allowed(u,v) then return null;end if;
 select relay_provider into provider from sera_call_private.settings where id;
 if p_issue and provider='cloudflare' then
   perform pg_advisory_xact_lock(2026111308);
   delete from sera_call_private.relay_issues where issued_at<now()-interval '1 day';
   if (select count(*) from sera_call_private.relay_issues where member_id=u)>=20 or (select count(*) from sera_call_private.relay_issues)>=100 then return null;end if;
   insert into sera_call_private.relay_issues(member_id) values(u);
 end if;
 return provider;
end; $$;
revoke all on function sera_call_private.relay_access(uuid,boolean) from public,anon,authenticated;
grant execute on function sera_call_private.relay_access(uuid,boolean) to authenticated;
create or replace function public.sera_call_relay_access(p_conversation uuid,p_issue boolean default false) returns text language sql security invoker set search_path='' as $$ select sera_call_private.relay_access(p_conversation,p_issue) $$;
revoke all on function public.sera_call_relay_access(uuid,boolean) from public,anon;
grant execute on function public.sera_call_relay_access(uuid,boolean) to authenticated;
