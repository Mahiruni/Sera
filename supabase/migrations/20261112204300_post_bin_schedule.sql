do $$ begin
if exists(select 1 from pg_available_extensions where name='pg_cron') then
execute 'create extension if not exists pg_cron with schema pg_catalog';
execute $job$select cron.schedule('sera-purge-expired-posts','*/15 * * * *','select public.sera_purge_expired_posts();')$job$;
end if;
end $$;