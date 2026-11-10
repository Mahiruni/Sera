-- Private, event-driven professional notifications. Only trusted triggers insert.
create table public.professional_notifications (
 id uuid primary key default gen_random_uuid(),
 recipient_id uuid not null references auth.users(id) on delete cascade,
 actor_id uuid not null references auth.users(id) on delete cascade,
 kind text not null check(kind in ('like','comment','share','follow','connection_request','connection_accepted')),
 post_id uuid references public.posts(id) on delete cascade,
 created_at timestamptz not null default now(),
 read_at timestamptz,
 constraint no_self_notification check(recipient_id<>actor_id)
);
create index professional_notifications_inbox_idx on public.professional_notifications(recipient_id,created_at desc);
alter table public.professional_notifications enable row level security;
revoke all on public.professional_notifications from public,anon,authenticated;
grant select on public.professional_notifications to authenticated;
grant update(read_at) on public.professional_notifications to authenticated;
create policy "Recipients read notifications" on public.professional_notifications
 for select to authenticated using(recipient_id=(select auth.uid()));
create policy "Recipients mark notifications read" on public.professional_notifications
 for update to authenticated using(recipient_id=(select auth.uid()))
 with check(recipient_id=(select auth.uid()) and read_at is not null);
create function private.notify_social_activity() returns trigger language plpgsql security definer set search_path='' as $$
declare owner_id uuid; actor uuid; event_kind text;
begin
 if tg_table_name='post_comments' then actor:=new.author_id; event_kind:='comment';
 elsif tg_table_name='post_likes' then actor:=new.user_id; event_kind:='like';
 else actor:=new.user_id; event_kind:='share'; end if;
 select p.author_id into owner_id from public.posts p where p.id=new.post_id and p.status='published';
 if owner_id is not null and owner_id<>actor then
  insert into public.professional_notifications(recipient_id,actor_id,kind,post_id)
  values(owner_id,actor,event_kind,new.post_id);
 end if;
 return new;
end $$;
create trigger notify_post_like after insert on public.post_likes for each row execute function private.notify_social_activity();
create trigger notify_post_comment after insert on public.post_comments for each row execute function private.notify_social_activity();
create trigger notify_post_share after insert on public.post_shares for each row execute function private.notify_social_activity();
create function private.notify_professional_relationship() returns trigger language plpgsql security definer set search_path='' as $$
begin
 if tg_table_name='professional_follows' then
  if new.follower_id<>new.followed_id then
   insert into public.professional_notifications(recipient_id,actor_id,kind)
   values(new.followed_id,new.follower_id,'follow');
  end if;
 elsif tg_op='INSERT' and new.status='pending' then
  insert into public.professional_notifications(recipient_id,actor_id,kind)
  values(new.recipient_id,new.requester_id,'connection_request');
 elsif tg_op='UPDATE' and old.status='pending' and new.status='accepted' then
  insert into public.professional_notifications(recipient_id,actor_id,kind)
  values(new.requester_id,new.recipient_id,'connection_accepted');
 end if;
 return new;
end $$;
create trigger notify_professional_follow after insert on public.professional_follows for each row execute function private.notify_professional_relationship();
create trigger notify_professional_connection_insert after insert on public.professional_connection_requests for each row execute function private.notify_professional_relationship();
create trigger notify_professional_connection_update after update of status on public.professional_connection_requests for each row execute function private.notify_professional_relationship();
