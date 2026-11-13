alter table public.sera_message_preferences add column typing_visible boolean not null default true;
-- Existing clients and direct table writes obey the same account preference.
create function public.sera_guard_typing_visibility() returns trigger language plpgsql security invoker set search_path='' as $$
begin
 if new.member_id=(select auth.uid()) and exists(select 1 from public.sera_message_preferences p where p.user_id=new.member_id and not p.typing_visible) then return null; end if;
 return new;
end; $$;
create trigger sera_guard_typing_visibility before insert or update on public.professional_typing_status for each row execute function public.sera_guard_typing_visibility();
create function public.sera_clear_hidden_typing() returns trigger language plpgsql security invoker set search_path='' as $$
begin
 if new.user_id=(select auth.uid()) and not new.typing_visible then delete from public.professional_typing_status where member_id=new.user_id; end if;
 return new;
end; $$;
create trigger sera_clear_hidden_typing after insert or update of typing_visible on public.sera_message_preferences for each row execute function public.sera_clear_hidden_typing();
create function public.sera_set_typing_visibility(p_visible boolean) returns void language plpgsql security invoker set search_path='' as $$
begin
 if (select auth.uid()) is null or p_visible is null then raise exception 'Authentication required'; end if;
 insert into public.sera_message_preferences(user_id,typing_visible) values((select auth.uid()),p_visible) on conflict(user_id) do update set typing_visible=excluded.typing_visible,updated_at=now();
end; $$;
revoke all on function public.sera_set_typing_visibility(boolean) from public,anon;
grant execute on function public.sera_set_typing_visibility(boolean) to authenticated;
revoke all on function public.sera_guard_typing_visibility() from public,anon,authenticated;
revoke all on function public.sera_clear_hidden_typing() from public,anon,authenticated;
