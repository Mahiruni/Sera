create extension if not exists pg_net with schema extensions;
-- The secret is provisioned separately in Vault; never included in migration source.
select cron.schedule('sera-message-push-recovery','* * * * *',$cron$
 select net.http_get(
   url:='https://sera.hisabtech.com/api/cron/message-push-recovery',
   headers:=jsonb_build_object('Authorization','Bearer '||(select decrypted_secret from vault.decrypted_secrets where name='sera_message_recovery_secret')),
   timeout_milliseconds:=55000
 ) where exists(select 1 from vault.secrets where name='sera_message_recovery_secret')
 and exists(select 1 from public.sera_message_push_jobs where state in ('pending','leased') and ((state='pending' and next_attempt_at<=now()) or (state='leased' and lease_until<=now())));
$cron$);
-- Keep operational metadata for seven days, without retaining tokens or message bodies.
select cron.schedule('sera-message-push-prune','25 3 * * *',$cron$
 delete from public.sera_message_push_jobs where state in ('accepted','skipped','expired','failed') and created_at<now()-interval '7 days';
$cron$);
