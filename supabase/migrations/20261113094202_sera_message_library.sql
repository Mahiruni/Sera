alter table public.professional_messages add column edited_at timestamptz;
create or replace function public.guard_professional_message_update() returns trigger
language plpgsql security invoker set search_path='' as $$
begin
 if new.id is distinct from old.id or new.conversation_id is distinct from old.conversation_id
 or new.sender_id is distinct from old.sender_id or new.created_at is distinct from old.created_at
 or new.reply_to_id is distinct from old.reply_to_id or new.is_forwarded is distinct from old.is_forwarded
 or new.client_message_id is distinct from old.client_message_id then
  raise exception 'Message identity is immutable';
 end if;
 if old.unsent_at is null and new.unsent_at is not null then
  if (select auth.uid()) is distinct from old.sender_id
    or new.read_at is distinct from old.read_at or new.delivered_at is distinct from old.delivered_at then
   raise exception 'Only the sender can unsend a message';
  end if;
  new.body='Message unsent'; new.unsent_at=now(); new.edited_at=old.edited_at; return new;
 end if;
 if new.unsent_at is distinct from old.unsent_at then raise exception 'Message removal is immutable'; end if;
 if new.body is distinct from old.body then
  if (select auth.uid()) is distinct from old.sender_id or old.unsent_at is not null or old.created_at < now()-interval '15 minutes'
   or new.read_at is distinct from old.read_at or new.delivered_at is distinct from old.delivered_at
   or char_length(btrim(new.body)) not between 1 and 4000
   or exists(select 1 from public.professional_message_attachments a where a.message_id=old.id)
  then raise exception 'Message cannot be edited'; end if;
  new.body=btrim(new.body); new.edited_at=now();
 elsif new.edited_at is distinct from old.edited_at then raise exception 'Edit timestamp is server managed';
 end if;
 if (select auth.uid())=old.sender_id and
  (new.read_at is distinct from old.read_at or new.delivered_at is distinct from old.delivered_at) then
  raise exception 'Senders cannot forge receipts';
 end if;
 if old.read_at is not null and new.read_at is distinct from old.read_at then raise exception 'Read receipt is immutable'; end if;
 if old.delivered_at is not null and new.delivered_at is distinct from old.delivered_at then raise exception 'Delivery receipt is immutable'; end if;
 if new.read_at is not null and old.read_at is null then new.read_at=now(); end if;
 if new.delivered_at is not null and old.delivered_at is null then new.delivered_at=now(); end if;
 if new.read_at is not null and new.delivered_at is null then new.delivered_at=new.read_at; end if;
 return new;
end; $$;
create policy "Sender edits own recent message" on public.professional_messages for update to authenticated
 using(sender_id=(select auth.uid()) and unsent_at is null and created_at>=now()-interval '15 minutes' and exists(select 1 from public.professional_conversations c where c.id=conversation_id))
 with check(sender_id=(select auth.uid()) and unsent_at is null and exists(select 1 from public.professional_conversations c where c.id=conversation_id));
create function public.sera_guard_edit_insert() returns trigger language plpgsql security invoker set search_path='' as $$
begin if new.edited_at is not null then raise exception 'Edit timestamp is server managed'; end if; return new; end; $$;
create trigger sera_guard_edit_insert before insert on public.professional_messages for each row execute function public.sera_guard_edit_insert();
revoke all on function public.sera_guard_edit_insert() from public,anon,authenticated;
create table public.sera_starred_messages(
 user_id uuid not null references auth.users(id) on delete cascade,
 message_id uuid not null references public.professional_messages(id) on delete cascade,
 created_at timestamptz not null default now(),primary key(user_id,message_id)
);
alter table public.sera_starred_messages enable row level security;
revoke all on public.sera_starred_messages from public,anon,authenticated;
grant select,insert,delete on public.sera_starred_messages to authenticated;
create policy "Own accessible stars" on public.sera_starred_messages for select to authenticated using(user_id=(select auth.uid()) and exists(select 1 from public.professional_messages m where m.id=message_id and m.unsent_at is null));
create policy "Own star insert" on public.sera_starred_messages for insert to authenticated with check(user_id=(select auth.uid()) and exists(select 1 from public.professional_messages m where m.id=message_id and m.unsent_at is null));
create policy "Own star delete" on public.sera_starred_messages for delete to authenticated using(user_id=(select auth.uid()));
create table public.sera_pinned_messages(
 conversation_id uuid primary key references public.professional_conversations(id) on delete cascade,
 message_id uuid not null references public.professional_messages(id) on delete cascade,
 pinned_by uuid not null references auth.users(id) on delete cascade,
 pinned_at timestamptz not null default now()
);
alter table public.sera_pinned_messages enable row level security;
revoke all on public.sera_pinned_messages from public,anon,authenticated;
grant select,insert,update,delete on public.sera_pinned_messages to authenticated;
create policy "Participants see pins" on public.sera_pinned_messages for select to authenticated using(exists(select 1 from public.professional_conversations c where c.id=conversation_id) and exists(select 1 from public.professional_messages m where m.id=message_id and m.conversation_id=sera_pinned_messages.conversation_id and m.unsent_at is null));
create policy "Participants insert pins" on public.sera_pinned_messages for insert to authenticated with check(pinned_by=(select auth.uid()) and exists(select 1 from public.professional_messages m where m.id=message_id and m.conversation_id=sera_pinned_messages.conversation_id and m.unsent_at is null));
create policy "Participants update pins" on public.sera_pinned_messages for update to authenticated using(exists(select 1 from public.professional_conversations c where c.id=conversation_id)) with check(pinned_by=(select auth.uid()) and exists(select 1 from public.professional_messages m where m.id=message_id and m.conversation_id=sera_pinned_messages.conversation_id and m.unsent_at is null));
create policy "Participants delete pins" on public.sera_pinned_messages for delete to authenticated using(exists(select 1 from public.professional_conversations c where c.id=conversation_id));
create function public.sera_edit_message(p_message uuid,p_body text) returns timestamptz language plpgsql security invoker set search_path='' as $$
declare stamp timestamptz; begin
 if (select auth.uid()) is null or p_body is null or char_length(btrim(p_body)) not between 1 and 4000 then raise exception 'Invalid message'; end if;
 update public.professional_messages set body=btrim(p_body) where id=p_message and sender_id=(select auth.uid()) and unsent_at is null and created_at>=now()-interval '15 minutes' returning edited_at into stamp;
 if not found then raise exception 'Message unavailable or edit window expired'; end if; return stamp;
end; $$;
create function public.sera_set_message_star(p_message uuid,p_star boolean) returns void language plpgsql security invoker set search_path='' as $$
begin
 if (select auth.uid()) is null or p_star is null then raise exception 'Authentication required'; end if;
 if p_star then insert into public.sera_starred_messages(user_id,message_id) values((select auth.uid()),p_message) on conflict do nothing;
 else delete from public.sera_starred_messages where user_id=(select auth.uid()) and message_id=p_message; end if;
end; $$;
create function public.sera_set_message_pin(p_conversation uuid,p_message uuid,p_pin boolean) returns void language plpgsql security invoker set search_path='' as $$
begin
 if (select auth.uid()) is null or p_pin is null or not exists(select 1 from public.professional_conversations c where c.id=p_conversation) then raise exception 'Conversation unavailable'; end if;
 if p_pin then insert into public.sera_pinned_messages(conversation_id,message_id,pinned_by) values(p_conversation,p_message,(select auth.uid())) on conflict(conversation_id) do update set message_id=excluded.message_id,pinned_by=excluded.pinned_by,pinned_at=now();
 else delete from public.sera_pinned_messages where conversation_id=p_conversation and message_id=p_message; end if;
end; $$;
create function public.sera_message_library(p_conversation uuid,p_mode text,p_query text default '',p_before timestamptz default null,p_before_id uuid default null,p_limit integer default 30)
returns table(id uuid,sender_id uuid,body text,created_at timestamptz,edited_at timestamptz)
language sql stable security invoker set search_path='' as $$
 select m.id,m.sender_id,m.body,m.created_at,m.edited_at from public.professional_messages m
 where m.conversation_id=p_conversation and m.unsent_at is null and (select auth.uid()) is not null
 and p_mode in ('search','star','pin')
 and (p_mode<>'search' or (char_length(btrim(p_query)) between 1 and 120 and strpos(lower(m.body),lower(btrim(p_query)))>0))
 and (p_mode<>'star' or exists(select 1 from public.sera_starred_messages s where s.message_id=m.id and s.user_id=(select auth.uid())))
 and (p_mode<>'pin' or exists(select 1 from public.sera_pinned_messages p where p.message_id=m.id and p.conversation_id=m.conversation_id))
 and (p_before is null or (m.created_at,m.id)<(p_before,p_before_id))
 order by m.created_at desc,m.id desc limit greatest(1,least(coalesce(p_limit,30),50));
$$;
revoke all on function public.sera_edit_message(uuid,text),public.sera_set_message_star(uuid,boolean),public.sera_set_message_pin(uuid,uuid,boolean),public.sera_message_library(uuid,text,text,timestamptz,uuid,integer) from public,anon;
grant execute on function public.sera_edit_message(uuid,text),public.sera_set_message_star(uuid,boolean),public.sera_set_message_pin(uuid,uuid,boolean),public.sera_message_library(uuid,text,text,timestamptz,uuid,integer) to authenticated;
