-- SERA Phase 4 branded company media, additive migration.
alter table public.sera_organization_details
  add column if not exists logo_path text,
  add column if not exists cover_path text;
alter table public.sera_organization_details
  add constraint sera_logo_path_format check (logo_path is null or logo_path ~ '^[0-9a-f-]{36}/logo/[0-9a-f-]{36}[.]webp$'),
  add constraint sera_cover_path_format check (cover_path is null or cover_path ~ '^[0-9a-f-]{36}/cover/[0-9a-f-]{36}[.]webp$');
grant update(logo_path,cover_path) on public.sera_organization_details to authenticated;
-- Public company logos/covers have no private employee data.
insert into storage.buckets (id,name,public,file_size_limit,allowed_mime_types)
values ('sera-company-brand','sera-company-brand',true,3145728,array['image/webp'])
on conflict (id) do nothing;
create policy "sera_brand_admin_upload" on storage.objects for insert to authenticated
with check (
  bucket_id = 'sera-company-brand'
  and (storage.foldername(name))[2] in ('logo','cover')
  and exists (
    select 1 from public.sera_organization_members m
    where m.organization_id::text = (storage.foldername(name))[1]
      and m.user_id = (select auth.uid()) and m.role in ('owner','admin')
  )
);
create policy "sera_brand_admin_remove" on storage.objects for delete to authenticated
using (
  bucket_id = 'sera-company-brand'
  and exists (
    select 1 from public.sera_organization_members m
    where m.organization_id::text = (storage.foldername(name))[1]
      and m.user_id = (select auth.uid()) and m.role in ('owner','admin')
  )
);
