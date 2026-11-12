create function public.sera_unread_activity() returns jsonb language sql stable security invoker set search_path='' as $$
select jsonb_build_object('notifications',(select count(*) from public.professional_notifications where recipient_id=auth.uid() and read_at is null),'inbox',(select count(*) from public.professional_messages m join public.professional_conversations c on c.id=m.conversation_id where (c.member_a=auth.uid() or c.member_b=auth.uid()) and m.sender_id<>auth.uid() and m.read_at is null)) where auth.uid() is not null;
$$;
revoke all on function public.sera_unread_activity() from public,anon;
grant execute on function public.sera_unread_activity() to authenticated;
