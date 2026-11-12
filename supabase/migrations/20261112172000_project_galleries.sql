insert into storage.buckets(id,name,public,file_size_limit,allowed_mime_types)
values('sera-portfolio-images','sera-portfolio-images',false,5242880,array['image/jpeg','image/png','image/webp']) on conflict(id) do nothing;
create function public.sera_gallery_allowed(p_path text,p_write boolean default false) returns boolean
language sql stable security definer set search_path='' as $$
 select auth.uid() is not null and exists(
 select 1 from public.sera_portfolio_items i
 join public.professional_public_portfolios p on p.user_id=i.user_id
 where i.user_id::text=split_part(p_path,'/',1) and i.id::text=split_part(p_path,'/',2)
 and (i.user_id=auth.uid() or (not p_write and i.shared and p.published and not exists(
 select 1 from public.professional_blocks b where (b.blocker_id=auth.uid() and b.blocked_id=i.user_id) or (b.blocker_id=i.user_id and b.blocked_id=auth.uid()))))
 ) or (auth.uid() is not null and exists(select 1 from public.sera_portfolio_items i where i.user_id=auth.uid() and i.user_id::text=split_part(p_path,'/',1) and i.id::text=split_part(p_path,'/',2)));
$$;
revoke all on function public.sera_gallery_allowed(text,boolean) from public,anon;
grant execute on function public.sera_gallery_allowed(text,boolean) to authenticated;
create policy sera_gallery_read on storage.objects for select to authenticated using(bucket_id='sera-portfolio-images' and public.sera_gallery_allowed(name,false));
create policy sera_gallery_insert on storage.objects for insert to authenticated with check(bucket_id='sera-portfolio-images' and name ~ '^[0-9a-f-]{36}/[0-9a-f-]{36}/[0-9a-f-]{36}[.](jpg|png|webp)$' and public.sera_gallery_allowed(name,true));
create policy sera_gallery_delete on storage.objects for delete to authenticated using(bucket_id='sera-portfolio-images' and split_part(name,'/',1)=auth.uid()::text);
create function public.sera_list_gallery(p_item uuid) returns table(path text)
language sql stable security definer set search_path='' as $$
 select o.name from storage.objects o where o.bucket_id='sera-portfolio-images' and split_part(o.name,'/',2)=p_item::text and public.sera_gallery_allowed(o.name,false) order by o.created_at,o.name limit 30;
$$;
revoke all on function public.sera_list_gallery(uuid) from public,anon;
grant execute on function public.sera_list_gallery(uuid) to authenticated;
