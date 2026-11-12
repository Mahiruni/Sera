-- Efficient, participant-scoped inbox previews and history ordering.
create index if not exists professional_messages_thread_history_idx on public.professional_messages(conversation_id,created_at desc,id desc);
create index if not exists professional_messages_thread_unread_idx on public.professional_messages(conversation_id,sender_id) where read_at is null and unsent_at is null;
create or replace function public.sera_inbox_v2(p_limit integer default 80)
returns table(id uuid,member_a uuid,member_b uuid,last_message_at timestamptz,last_message_id uuid,last_body text,last_sender_id uuid,last_created_at timestamptz,last_delivered_at timestamptz,unread_count bigint)
language sql stable security invoker set search_path='' as $$
 select c.id,c.member_a,c.member_b,c.last_message_at,m.id,m.body,m.sender_id,m.created_at,m.delivered_at,
  (select count(*) from public.professional_messages u where u.conversation_id=c.id and u.sender_id<>(select auth.uid()) and u.read_at is null and u.unsent_at is null)
 from public.professional_conversations c
 left join lateral (select x.id,x.body,x.sender_id,x.created_at,x.delivered_at from public.professional_messages x where x.conversation_id=c.id order by x.created_at desc,x.id desc limit 1) m on true
 where (select auth.uid()) in (c.member_a,c.member_b)
 order by c.last_message_at desc nulls last,c.created_at desc,c.id desc
 limit greatest(1,least(coalesce(p_limit,80),200));
$$;
revoke all on function public.sera_inbox_v2(integer) from public,anon;
grant execute on function public.sera_inbox_v2(integer) to authenticated;
