select cron.schedule('sera-scheduled-message-delivery','* * * * *',$cron$select private.sera_deliver_scheduled_messages(50);$cron$);
-- The production recovery endpoint has been verified before enabling transactional enqueue.
update private.sera_message_push_rollout set enabled=true where singleton;
