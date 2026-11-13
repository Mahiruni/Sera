alter table public.sera_message_preferences add column read_receipts_visible boolean not null default true;
create table public.sera_private_message_reads(
 message_id uuid primary key references public.professional_messages(id) on delete cascade,
 recipient_id uuid not null references auth.users(id) on delete cascade,
 read_at timestamptz not null default now()
);
alter table public.sera_private_message_reads enable row level security;
revoke all on public.sera_private_message_reads from public,anon,authenticated;
grant select,insert on public.sera_private_message_reads to authenticated;
grant select on public.sera_private_message_reads to service_role;
create policy "Recipient reads own private state" on public.sera_private_message_reads for select to authenticated using(recipient_id=(select auth.uid()));
create policy "Recipient records own private state" on public.sera_private_message_reads for insert to authenticated with check(recipient_id=(select auth.uid()) and exists(select 1 from public.professional_messages m join public.professional_conversations c on c.id=m.conversation_id where m.id=message_id and m.sender_id<>(select auth.uid()) and (select auth.uid()) in(c.member_a,c.member_b)));
create function public.sera_set_read_receipt_visibility(p_visible boolean) returns void language plpgsql security invoker set search_path='' as $$
begin
 if (select auth.uid()) is null or p_visible is null then raise exception 'Authentication required'; end if;
 insert into public.sera_message_preferences(user_id,read_receipts_visible) values((select auth.uid()),p_visible) on conflict(user_id) do update set read_receipts_visible=excluded.read_receipts_visible,updated_at=now();
end; $$;
revoke all on function public.sera_set_read_receipt_visibility(boolean) from public,anon;
grant execute on function public.sera_set_read_receipt_visibility(boolean) to authenticated;
-- Runs after the existing immutable-message guard. Older direct PATCH clients
-- and RPC clients cannot accidentally publish a private read timestamp.
create function public.sera_guard_private_read() returns trigger language plpgsql security invoker set search_path='' as $$
begin
 if old.read_at is null and new.read_at is not null and new.sender_id<>(select auth.uid()) then
  if exists(select 1 from public.sera_message_preferences where user_id=(select auth.uid()) and not read_receipts_visible)
  or exists(select 1 from public.sera_private_message_reads where message_id=old.id and recipient_id=(select auth.uid())) then
   insert into public.sera_private_message_reads(message_id,recipient_id) values(old.id,(select auth.uid())) on conflict do nothing;
   new.read_at=null;
   -- No realtime row update for a private read if delivery was already confirmed.
   if new is not distinct from old then return null; end if;
  end if;
 end if;
 return new;
end; $$;
revoke all on function public.sera_guard_private_read() from public,anon,authenticated;
create trigger sera_guard_private_read before update on public.professional_messages for each row execute function public.sera_guard_private_read();

create or replace function public.sera_inbox_v2(p_limit integer default 80)
returns table(id uuid,member_a uuid,member_b uuid,last_message_at timestamptz,last_message_id uuid,last_body text,last_sender_id uuid,last_created_at timestamptz,last_delivered_at timestamptz,unread_count bigint)
language sql stable security invoker set search_path='' as $$
 select c.id,c.member_a,c.member_b,c.last_message_at,m.id,m.body,m.sender_id,m.created_at,m.delivered_at,
 (select count(*) from public.professional_messages u where u.conversation_id=c.id and u.sender_id<>(select auth.uid()) and u.read_at is null and u.unsent_at is null and not exists(select 1 from public.sera_private_message_reads r where r.message_id=u.id and r.recipient_id=(select auth.uid())))
 from public.professional_conversations c
 left join lateral (select x.id,x.body,x.sender_id,x.created_at,x.delivered_at from public.professional_messages x where x.conversation_id=c.id order by x.created_at desc,x.id desc limit 1) m on true
 where (select auth.uid()) in(c.member_a,c.member_b)
 order by c.last_message_at desc nulls last,c.created_at desc,c.id desc limit greatest(1,least(coalesce(p_limit,80),200));
$$;
create or replace function public.sera_unread_activity() returns jsonb language sql stable security invoker set search_path='' as $$
select jsonb_build_object('notifications',(select count(*) from public.professional_notifications where recipient_id=auth.uid() and read_at is null),'inbox',(select count(*) from public.professional_messages m join public.professional_conversations c on c.id=m.conversation_id where (c.member_a=auth.uid() or c.member_b=auth.uid()) and m.sender_id<>auth.uid() and m.read_at is null and m.unsent_at is null and not exists(select 1 from public.sera_private_message_reads r where r.message_id=m.id and r.recipient_id=auth.uid()))) where auth.uid() is not null;
$$;
