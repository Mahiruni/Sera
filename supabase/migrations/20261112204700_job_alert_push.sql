-- Native preference API and bounded, server-only push claim. Inbox remains canonical.
create function public.sera_set_job_alert_preference(p_enabled boolean) returns void language plpgsql security definer set search_path='' as $$
begin
 if auth.uid() is null or not private.account_verified() then raise exception 'Verified account required';end if;
 insert into public.sera_job_alert_preferences(user_id,enabled) values(auth.uid(),p_enabled) on conflict(user_id) do update set enabled=excluded.enabled,updated_at=now();
end$$;
revoke all on function public.sera_set_job_alert_preference(boolean) from public,anon;
grant execute on function public.sera_set_job_alert_preference(boolean) to authenticated;
create table private.sera_job_push_claims(notification_id uuid primary key references public.professional_notifications(id) on delete cascade,claimed_at timestamptz not null default now());
revoke all on private.sera_job_push_claims from public,anon,authenticated;
create function public.sera_claim_job_pushes() returns table(notification_id uuid,recipient_id uuid) language plpgsql security definer set search_path='' as $$
begin
 return query with candidates as (
 select n.id,n.recipient_id from public.professional_notifications n join public.sera_job_alert_preferences p on p.user_id=n.recipient_id and p.enabled
 join public.jobs j on j.id=n.job_id and j.status='open'
 where n.kind='job_match' and n.read_at is null and n.created_at>now()-interval '1 day'
 and private.sera_social_visible(n.recipient_id,n.actor_id)
 and not exists(select 1 from private.sera_job_push_claims c where c.notification_id=n.id)
 order by n.created_at,n.id limit 10 for update of n skip locked
 ), claimed as (insert into private.sera_job_push_claims(notification_id) select id from candidates on conflict do nothing returning sera_job_push_claims.notification_id)
 select c.id,c.recipient_id from candidates c join claimed k on k.notification_id=c.id;
end$$;
revoke all on function public.sera_claim_job_pushes() from public,anon,authenticated;
grant execute on function public.sera_claim_job_pushes() to service_role;
