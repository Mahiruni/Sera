-- Retain all existing pins; allow more than one per conversation.
alter table public.sera_pinned_messages drop constraint sera_pinned_messages_pkey;
alter table public.sera_pinned_messages add primary key(conversation_id,message_id);
create or replace function public.sera_set_message_pin(p_conversation uuid,p_message uuid,p_pin boolean) returns void language plpgsql security invoker set search_path='' as $$
begin
 if (select auth.uid()) is null or p_pin is null or not exists(select 1 from public.professional_conversations c where c.id=p_conversation) then raise exception 'Conversation unavailable'; end if;
 if p_pin then insert into public.sera_pinned_messages(conversation_id,message_id,pinned_by) values(p_conversation,p_message,(select auth.uid())) on conflict(conversation_id,message_id) do nothing;
 else delete from public.sera_pinned_messages where conversation_id=p_conversation and message_id=p_message; end if;
end; $$;
-- Existing RPC remains compatible with old clients. New RPC supports global private stars and server-side filters.
create function public.sera_message_library_v2(p_conversation uuid,p_mode text,p_query text default '',p_sender text default 'all',p_from timestamptz default null,p_until timestamptz default null,p_before timestamptz default null,p_before_id uuid default null,p_limit integer default 30)
returns table(id uuid,conversation_id uuid,sender_id uuid,body text,created_at timestamptz,edited_at timestamptz)
language sql stable security invoker set search_path='' as $$
 select m.id,m.conversation_id,m.sender_id,m.body,m.created_at,m.edited_at from public.professional_messages m
 where (select auth.uid()) is not null and m.unsent_at is null
 and p_mode in ('search','star','pin') and (m.conversation_id=p_conversation or (p_conversation is null and p_mode='star'))
 and (p_mode<>'search' or (char_length(btrim(p_query)) between 1 and 120 and strpos(lower(m.body),lower(btrim(p_query)))>0))
 and (p_mode<>'star' or exists(select 1 from public.sera_starred_messages s where s.message_id=m.id and s.user_id=(select auth.uid())))
 and (p_mode<>'pin' or exists(select 1 from public.sera_pinned_messages p where p.message_id=m.id and p.conversation_id=m.conversation_id))
 and (p_sender='all' or (p_sender='you' and m.sender_id=(select auth.uid())) or (p_sender='contact' and m.sender_id<>(select auth.uid())))
 and (p_from is null or m.created_at>=p_from) and (p_until is null or m.created_at<p_until)
 and (p_before is null or (m.created_at,m.id)<(p_before,p_before_id))
 order by m.created_at desc,m.id desc limit greatest(1,least(coalesce(p_limit,30),50));
$$;
-- A bounded window of actual message IDs around a visible, non-removed anchor.
-- RLS applies to both the anchor and every result; no privileged history access.
create function public.sera_message_context(p_conversation uuid,p_message uuid,p_radius integer default 20)
returns table(id uuid,created_at timestamptz)
language sql stable security invoker set search_path='' as $$
 with anchor as (select m.id,m.created_at from public.professional_messages m where m.id=p_message and m.conversation_id=p_conversation and m.unsent_at is null and (select auth.uid()) is not null),
 earlier as (select m.id,m.created_at from public.professional_messages m cross join anchor a where m.conversation_id=p_conversation and (m.created_at,m.id)<(a.created_at,a.id) order by m.created_at desc,m.id desc limit greatest(1,least(coalesce(p_radius,20),50))),
 later as (select m.id,m.created_at from public.professional_messages m cross join anchor a where m.conversation_id=p_conversation and (m.created_at,m.id)>(a.created_at,a.id) order by m.created_at,m.id limit greatest(1,least(coalesce(p_radius,20),50)))
 select x.id,x.created_at from (select * from earlier union all select * from anchor union all select * from later) x order by x.created_at,x.id;
$$;
revoke all on function public.sera_message_library_v2(uuid,text,text,text,timestamptz,timestamptz,timestamptz,uuid,integer),public.sera_message_context(uuid,uuid,integer) from public,anon;
grant execute on function public.sera_message_library_v2(uuid,text,text,text,timestamptz,timestamptz,timestamptz,uuid,integer),public.sera_message_context(uuid,uuid,integer) to authenticated;
