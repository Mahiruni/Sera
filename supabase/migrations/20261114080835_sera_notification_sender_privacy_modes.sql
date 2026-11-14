alter table public.sera_message_preferences add column if not exists notification_preview_mode text;
update public.sera_message_preferences set notification_preview_mode=case when notification_previews then 'full' else 'hidden' end where notification_preview_mode is null;
alter table public.sera_message_preferences alter column notification_preview_mode set default 'sender', alter column notification_preview_mode set not null;
alter table public.sera_message_preferences add constraint sera_notification_preview_mode_check check(notification_preview_mode in ('full','sender','hidden'));
create or replace function public.sera_set_notification_preview_mode(p_mode text) returns void language plpgsql security invoker set search_path='' as $$
begin
 if (select auth.uid()) is null or p_mode is null or p_mode not in ('full','sender','hidden') then raise exception 'Invalid notification preference'; end if;
 insert into public.sera_message_preferences(user_id,notification_previews,notification_preview_mode) values((select auth.uid()),p_mode='full',p_mode)
 on conflict(user_id) do update set notification_previews=excluded.notification_previews,notification_preview_mode=excluded.notification_preview_mode,updated_at=now();
end; $$;
revoke all on function public.sera_set_notification_preview_mode(text) from public,anon;
grant execute on function public.sera_set_notification_preview_mode(text) to authenticated;
create or replace function public.sera_set_notification_previews(p_enabled boolean) returns void language plpgsql security invoker set search_path='' as $$
begin
 if p_enabled is null then raise exception 'Invalid notification preference'; end if;
 perform public.sera_set_notification_preview_mode(case when p_enabled then 'full' else 'hidden' end);
end; $$;
revoke all on function public.sera_set_notification_previews(boolean) from public,anon;
grant execute on function public.sera_set_notification_previews(boolean) to authenticated;
