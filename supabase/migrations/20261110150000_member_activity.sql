create table public.member_notifications (
  id bigint generated always as identity primary key,
  recipient_id uuid not null references auth.users on delete cascade,
  actor_id uuid not null references auth.users on delete cascade,
  post_id uuid not null references public.posts on delete cascade,
  comment_id uuid references public.post_comments on delete cascade,
  kind text not null check (kind in ('like','share','comment','reply')),
  created_at timestamptz not null default clock_timestamp(),
  read_at timestamptz,
  check (recipient_id <> actor_id),
  unique nulls not distinct (recipient_id, actor_id, post_id, comment_id, kind)
);
create index member_notifications_inbox on public.member_notifications(recipient_id,id desc);
create index member_notifications_unread on public.member_notifications(recipient_id,id desc) where read_at is null;
alter table public.member_notifications enable row level security;
revoke all on public.member_notifications from public,anon,authenticated;
grant select on public.member_notifications to authenticated;
grant update(read_at) on public.member_notifications to authenticated;
create policy notification_recipient_read on public.member_notifications for select to authenticated
using (recipient_id=(select auth.uid()) and (select private.account_verified()) and exists(select from public.posts p where p.id=post_id and p.status='published'));
create policy notification_recipient_update on public.member_notifications for update to authenticated
using (recipient_id=(select auth.uid()) and (select private.account_verified()))
with check (recipient_id=(select auth.uid()) and read_at is not null);

create function private.record_member_activity() returns trigger
language plpgsql security definer set search_path='' as $$
declare owner_id uuid; actor uuid; parent_author uuid; comment uuid; activity text;
begin
  select author_id into owner_id from public.posts where id=new.post_id and status='published';
  if owner_id is null then return new; end if;
  if tg_table_name='post_comments' then
    actor:=new.author_id; comment:=new.id; activity:='comment';
    if new.parent_id is not null then
      select author_id into parent_author from public.post_comments where id=new.parent_id and post_id=new.post_id;
      if parent_author<>actor then
        insert into public.member_notifications(recipient_id,actor_id,post_id,comment_id,kind)
        values(parent_author,actor,new.post_id,comment,'reply') on conflict do nothing;
      end if;
    end if;
  else
    actor:=new.user_id;
    activity:=case tg_table_name when 'post_likes' then 'like' else 'share' end;
  end if;
  if owner_id<>actor and owner_id is distinct from parent_author then
    insert into public.member_notifications(recipient_id,actor_id,post_id,comment_id,kind)
    values(owner_id,actor,new.post_id,comment,activity) on conflict do nothing;
  end if;
  return new;
end $$;
revoke all on function private.record_member_activity() from public,anon,authenticated;
create trigger post_like_notification after insert on public.post_likes for each row execute function private.record_member_activity();
create trigger post_share_notification after insert on public.post_shares for each row execute function private.record_member_activity();
create trigger post_comment_notification after insert on public.post_comments for each row execute function private.record_member_activity();

create function private.can_view_member(member uuid) returns boolean
language sql stable security definer set search_path='' as $$
  select private.account_verified() and (
    member=auth.uid() or exists(select from public.posts where author_id=member and status='published')
    or exists(select from public.post_comments c join public.posts p on p.id=c.post_id where c.author_id=member and p.status='published')
    or exists(select from public.member_notifications n join public.posts p on p.id=n.post_id where n.recipient_id=auth.uid() and n.actor_id=member and p.status='published')
    or exists(select from public.applications a join public.jobs j on j.id=a.job_id
      where (a.candidate_id=member and j.employer_id=auth.uid()) or (a.candidate_id=auth.uid() and j.employer_id=member))
  )
$$;
revoke all on function private.can_view_member(uuid) from public,anon,authenticated;

create function private.visible_member_cards(ids uuid[])
returns table(id uuid,full_name text,avatar_path text)
language sql stable security definer set search_path='' as $$
  select p.id,p.full_name,case when a.avatar_path ~ ('^'||p.id::text||'/avatar/[A-Za-z0-9_-]+\.(jpg|png|jpeg|webp)$') then a.avatar_path end
  from public.profiles p left join public.account_assets a on a.user_id=p.id
  where p.id=any(ids[1:200]) and private.can_view_member(p.id)
$$;
revoke all on function private.visible_member_cards(uuid[]) from public,anon,authenticated;
grant execute on function private.visible_member_cards(uuid[]) to authenticated;
create function public.member_cards(ids uuid[]) returns table(id uuid,full_name text,avatar_path text)
language sql stable security invoker set search_path='' as $$select * from private.visible_member_cards(ids)$$;
revoke all on function public.member_cards(uuid[]) from public,anon,authenticated;
grant execute on function public.member_cards(uuid[]) to authenticated;

create function private.visible_avatar(object_name text) returns boolean
language sql stable security definer set search_path='' as $$
  select exists(select from public.account_assets a where a.avatar_path=object_name
    and object_name ~ ('^'||a.user_id::text||'/avatar/[A-Za-z0-9_-]+\.(jpg|png|jpeg|webp)$')
    and private.can_view_member(a.user_id))
$$;
create index account_assets_current_avatar on public.account_assets(avatar_path) where avatar_path is not null;
revoke all on function private.visible_avatar(text) from public,anon,authenticated;
grant execute on function private.visible_avatar(text) to authenticated;
create policy current_member_avatar on storage.objects for select to authenticated
using (bucket_id='sera-account-private' and private.visible_avatar(name));

do $$ begin
  if exists(select from pg_publication where pubname='supabase_realtime') then
    alter publication supabase_realtime add table public.member_notifications;
  end if;
end $$;
