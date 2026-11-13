-- Retain incomplete uploads for seven days; remove only through Storage API.
-- A durable claim and path advisory lock serialize cleanup against message attachment registration.
create schema if not exists sera_attachment_private;
revoke all on schema sera_attachment_private from public,anon,authenticated;
create table if not exists sera_attachment_private.cleanup_claims(
 path text primary key, object_id uuid not null, claimed_at timestamptz not null default now()
);
alter table sera_attachment_private.cleanup_claims enable row level security;
revoke all on sera_attachment_private.cleanup_claims from public,anon,authenticated;
create or replace function sera_attachment_private.guard_registration() returns trigger
language plpgsql security definer set search_path='' as $$
begin
 perform pg_advisory_xact_lock(hashtextextended(new.storage_path,2026111306));
 if exists(select 1 from sera_attachment_private.cleanup_claims c where c.path=new.storage_path) then
   raise exception 'Attachment cleanup in progress; retry upload';
 end if;
 -- Ensure a stale client cannot register an object already removed by cleanup.
 if not exists(select 1 from storage.objects o where o.bucket_id='professional-message-files' and o.name=new.storage_path and o.owner_id=new.owner_id::text) then
   raise exception 'Attachment upload unavailable';
 end if;
 return new;
end; $$;
revoke all on function sera_attachment_private.guard_registration() from public,anon,authenticated;
drop trigger if exists sera_attachment_cleanup_registration_guard on public.professional_message_attachments;
create trigger sera_attachment_cleanup_registration_guard before insert on public.professional_message_attachments
for each row execute function sera_attachment_private.guard_registration();
create or replace function public.sera_claim_abandoned_chat_uploads() returns table(path text,object_id uuid)
language plpgsql security definer set search_path='' as $$
declare candidate record; n integer:=0;
begin
 -- Drop completed claims whose exact object no longer exists (e.g. a crashed worker after deletion).
 delete from sera_attachment_private.cleanup_claims c
 where c.claimed_at<now()-interval '10 minutes'
 and not exists(select 1 from storage.objects o where o.bucket_id='professional-message-files' and o.id=c.object_id and o.name=c.path);
 for candidate in
   select o.name,o.id from storage.objects o
   where o.bucket_id='professional-message-files' and o.created_at<now()-interval '7 days'
   and o.archived_at is null and coalesce(o.is_delete_marker,false)=false
   and o.name ~ '^[0-9a-f-]{36}/[0-9a-f-]{36}/[0-9a-f-]{36}\.(jpg|jpeg|png|webp|pdf|m4a|mp4|webm|ogg|mp3)$'
   and not exists(select 1 from public.professional_message_attachments a where a.storage_path=o.name)
   and not exists(select 1 from sera_attachment_private.cleanup_claims c where c.path=o.name and c.claimed_at>now()-interval '10 minutes')
   order by o.created_at limit 100
 loop
   if not pg_try_advisory_xact_lock(hashtextextended(candidate.name,2026111306)) then continue; end if;
   -- Recheck under the same lock used by attachment registration.
   if exists(select 1 from public.professional_message_attachments a where a.storage_path=candidate.name) then continue;end if;
   if not exists(select 1 from storage.objects o where o.bucket_id='professional-message-files' and o.id=candidate.id and o.name=candidate.name and o.created_at<now()-interval '7 days') then continue;end if;
   insert into sera_attachment_private.cleanup_claims as c(path,object_id,claimed_at)
   values(candidate.name,candidate.id,now())
   on conflict on constraint cleanup_claims_pkey do update set object_id=excluded.object_id,claimed_at=excluded.claimed_at
   where c.claimed_at<now()-interval '10 minutes';
   if found then path:=candidate.name;object_id:=candidate.id;return next;n:=n+1;end if;
   if n>=50 then exit;end if;
 end loop;
end; $$;
create or replace function public.sera_finish_abandoned_chat_upload(p_path text,p_object uuid) returns boolean
language plpgsql security definer set search_path='' as $$
begin
 perform pg_advisory_xact_lock(hashtextextended(p_path,2026111306));
 if exists(select 1 from storage.objects o where o.bucket_id='professional-message-files' and o.id=p_object and o.name=p_path) then return false;end if;
 delete from sera_attachment_private.cleanup_claims c where c.path=p_path and c.object_id=p_object;
 return found;
end; $$;
revoke all on function public.sera_claim_abandoned_chat_uploads(),public.sera_finish_abandoned_chat_upload(text,uuid) from public,anon,authenticated;
grant execute on function public.sera_claim_abandoned_chat_uploads(),public.sera_finish_abandoned_chat_upload(text,uuid) to service_role;
