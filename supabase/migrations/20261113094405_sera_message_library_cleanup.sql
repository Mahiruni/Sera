create function private.sera_cleanup_removed_message_library() returns trigger language plpgsql security definer set search_path='' as $$
begin
 delete from public.sera_pinned_messages where message_id=new.id;
 delete from public.sera_starred_messages where message_id=new.id;
 return new;
end; $$;
revoke all on function private.sera_cleanup_removed_message_library() from public,anon,authenticated;
create trigger sera_cleanup_removed_message_library after update of unsent_at on public.professional_messages for each row when (old.unsent_at is null and new.unsent_at is not null) execute function private.sera_cleanup_removed_message_library();
create or replace function public.sera_guard_edit_insert() returns trigger language plpgsql security invoker set search_path='' as $$
begin
 if new.edited_at is not null then raise exception 'Edit timestamp is server managed'; end if;
 if (select auth.uid()) is not null then new.created_at=now(); end if;
 return new;
end; $$;
