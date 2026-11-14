-- Allow sustained legitimate calling while retaining burst and global safeguards.
create or replace function sera_call_private.relay_access(p_conversation uuid, p_issue boolean)
returns text language plpgsql security definer set search_path='' as $$
declare u uuid:=auth.uid(); v uuid; provider text;
begin
 select case when member_a=u then member_b else member_a end into v from public.professional_conversations where id=p_conversation and u in(member_a,member_b);
 if not sera_call_private.allowed(u,v) then return null;end if;
 select relay_provider into provider from sera_call_private.settings where id;
 if p_issue and provider='cloudflare' then
   perform pg_advisory_xact_lock(2026111308);
   delete from sera_call_private.relay_issues where issued_at<now()-interval '1 day';
   if (select count(*) from sera_call_private.relay_issues where member_id=u)>=60
      or (select count(*) from sera_call_private.relay_issues where member_id=u and issued_at>=now()-interval '1 hour')>=20
      or (select count(*) from sera_call_private.relay_issues)>=100 then return null;end if;
   insert into sera_call_private.relay_issues(member_id) values(u);
 end if;
 return provider;
end; $$;
