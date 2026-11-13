alter table public.sera_message_preferences add column notification_previews boolean not null default false;
create function public.sera_set_notification_previews(p_enabled boolean) returns void language plpgsql security invoker set search_path='' as $$
begin
 if (select auth.uid()) is null or p_enabled is null then raise exception 'Authentication required'; end if;
 insert into public.sera_message_preferences(user_id,notification_previews) values((select auth.uid()),p_enabled)
 on conflict(user_id) do update set notification_previews=excluded.notification_previews,updated_at=now();
end; $$;
revoke all on function public.sera_set_notification_previews(boolean) from public,anon;
grant execute on function public.sera_set_notification_previews(boolean) to authenticated;
