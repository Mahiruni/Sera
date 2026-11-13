-- Enable voice calling for all verified members without per-account allowlisting.
-- Preserve the existing global kill switch, relay configuration and block checks.
create or replace function sera_call_private.allowed(u uuid,v uuid)
returns boolean language sql stable security definer set search_path='' as $$
 select u is not null and v is not null and u<>v
 and exists(select 1 from auth.users where id=u and email_confirmed_at is not null and (banned_until is null or banned_until<=now()))
 and exists(select 1 from auth.users where id=v and email_confirmed_at is not null and (banned_until is null or banned_until<=now()))
 and not exists(select 1 from public.professional_blocks b where (b.blocker_id=u and b.blocked_id=v) or (b.blocker_id=v and b.blocked_id=u))
 and exists(select 1 from sera_call_private.settings s where s.id and s.enabled
 and (s.relay_provider='cloudflare' or (s.turn_url is not null and s.turn_secret is not null)));
$$;
revoke all on function sera_call_private.allowed(uuid,uuid) from public,anon,authenticated;
-- Existing RPCs continue to require authenticated conversation membership.
