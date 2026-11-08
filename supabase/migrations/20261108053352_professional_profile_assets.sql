alter table public.profiles add column headline text not null default '' check (char_length(headline)<=120), add column summary text not null default '' check (char_length(summary)<=2000), add column education text not null default '' check (char_length(education)<=2000);
grant update(headline,summary,education) on public.profiles to authenticated;
create table public.account_assets (
 user_id uuid primary key references auth.users(id) on delete cascade,
 avatar_path text, cv_path text, branded_path text, cv_name text,
 cv_uploaded_at timestamptz, cv_status text check(cv_status in ('ready','failed')),
 version uuid not null default gen_random_uuid(),
 check(avatar_path is null or avatar_path like user_id::text||'/avatar/%'),
 check(cv_path is null or cv_path like user_id::text||'/cv/%'),
 check(branded_path is null or branded_path like user_id::text||'/cv/%'),
 check(char_length(cv_name)<=180)
);
alter table public.account_assets enable row level security;
revoke all on public.account_assets from anon,authenticated;
grant select,insert,update,delete on public.account_assets to authenticated;
create policy account_assets_owner on public.account_assets for all to authenticated using ((select auth.uid())=user_id) with check ((select auth.uid())=user_id and (select private.account_verified()));
insert into storage.buckets(id,name,public,file_size_limit,allowed_mime_types) values('sera-account-private','sera-account-private',false,8388608,array['image/jpeg','image/png','image/webp','application/pdf']);
create policy account_files_read on storage.objects for select to authenticated using(bucket_id='sera-account-private' and (storage.foldername(name))[1]=(select auth.uid())::text);
create policy account_files_insert on storage.objects for insert to authenticated with check(bucket_id='sera-account-private' and (storage.foldername(name))[1]=(select auth.uid())::text and (select private.account_verified()));
create policy account_files_update on storage.objects for update to authenticated using(bucket_id='sera-account-private' and (storage.foldername(name))[1]=(select auth.uid())::text) with check(bucket_id='sera-account-private' and (storage.foldername(name))[1]=(select auth.uid())::text and (select private.account_verified()));
create policy account_files_delete on storage.objects for delete to authenticated using(bucket_id='sera-account-private' and (storage.foldername(name))[1]=(select auth.uid())::text);
