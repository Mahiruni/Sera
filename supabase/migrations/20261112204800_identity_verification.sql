-- Identity verification: private evidence, explicit review, revocable name-bound badge.
create table private.sera_identity_requests(
 id uuid primary key default gen_random_uuid(), user_id uuid not null references auth.users(id) on delete cascade,
 verified_name text not null, document_type text not null check(document_type in ('national_id','passport','driver_license')),
 status text not null default 'draft' check(status in ('draft','pending','approved','rejected','withdrawn','revoked')),
 created_at timestamptz not null default now(), submitted_at timestamptz, reviewed_at timestamptz,
 reviewer_id uuid references auth.users(id) on delete set null, decision_reason text, expires_at timestamptz
);
alter table private.sera_identity_requests enable row level security;
revoke all on private.sera_identity_requests from public,anon,authenticated;
create unique index sera_identity_active_request on private.sera_identity_requests(user_id) where status in ('draft','pending','approved');
create table private.sera_identity_audit(id bigint generated always as identity primary key,request_id uuid not null references private.sera_identity_requests(id) on delete cascade,actor_id uuid not null,action text not null,created_at timestamptz not null default now());
alter table private.sera_identity_audit enable row level security;
revoke all on private.sera_identity_audit from public,anon,authenticated;
create function private.sera_identity_reviewer() returns boolean language sql stable security definer set search_path='' as $$
 select private.account_verified() and exists(select 1 from auth.users where id=auth.uid() and raw_app_meta_data->>'role' in ('admin','moderator'))
$$;
revoke all on function private.sera_identity_reviewer() from public,anon,authenticated;
create function private.sera_identity_evidence_access(p_name text,p_action text) returns boolean language sql stable security definer set search_path='' as $$
 select private.account_verified() and exists(select 1 from private.sera_identity_requests r where p_name=r.user_id::text||'/'||r.id::text||'/evidence' and
 ((p_action='upload' and r.user_id=auth.uid() and r.status='draft') or
 (p_action='read' and ((r.user_id=auth.uid() and r.status in ('draft','pending','rejected','withdrawn','approved','revoked')) or (private.sera_identity_reviewer() and r.status='pending' and r.user_id<>auth.uid()))) or
 (p_action='delete' and r.user_id=auth.uid() and r.status in ('draft','rejected','withdrawn','approved','revoked'))))
$$;
revoke all on function private.sera_identity_evidence_access(text,text) from public,anon;
grant execute on function private.sera_identity_evidence_access(text,text) to authenticated;
insert into storage.buckets(id,name,public,file_size_limit,allowed_mime_types) values('sera-identity-private','sera-identity-private',false,4194304,array['image/jpeg','image/png','application/pdf']);
create policy "Identity evidence upload" on storage.objects for insert to authenticated with check(bucket_id='sera-identity-private' and private.sera_identity_evidence_access(name,'upload'));
create policy "Identity evidence read" on storage.objects for select to authenticated using(bucket_id='sera-identity-private' and private.sera_identity_evidence_access(name,'read'));
create policy "Identity evidence delete" on storage.objects for delete to authenticated using(bucket_id='sera-identity-private' and private.sera_identity_evidence_access(name,'delete'));
create function public.sera_identity_status() returns jsonb language plpgsql stable security definer set search_path='' as $$
declare r private.sera_identity_requests; n text;
begin
 if not private.account_verified() then raise exception 'Verified email required';end if;
 select * into r from private.sera_identity_requests where user_id=auth.uid() order by created_at desc limit 1;
 select full_name into n from public.profiles where id=auth.uid();
 if r.id is null then return jsonb_build_object('status','none','profile_name',n);end if;
 return jsonb_build_object('id',r.id,'status',case when r.status='approved' and (r.expires_at<=now() or r.verified_name is distinct from n) then 'expired' else r.status end,'profile_name',n,'verified_name',r.verified_name,'document_type',r.document_type,'reason',r.decision_reason,'expires_at',r.expires_at,'path',r.user_id::text||'/'||r.id::text||'/evidence');
end$$;
create function public.sera_identity_start(p_document_type text) returns jsonb language plpgsql security definer set search_path='' as $$
declare u uuid:=auth.uid(); n text; r private.sera_identity_requests;
begin
 if not private.account_verified() or p_document_type not in ('national_id','passport','driver_license') then raise exception 'Invalid verification request';end if;
 perform pg_advisory_xact_lock(hashtextextended(u::text,1351));
 select full_name into n from public.profiles where id=u;
 if n is null or length(trim(n))<2 then raise exception 'Complete your profile first';end if;
 update private.sera_identity_requests set status='revoked',decision_reason='Name changed or verification expired' where user_id=u and status='approved' and (expires_at<=now() or verified_name is distinct from n);
 select * into r from private.sera_identity_requests where user_id=u and status in ('draft','pending','approved') for update;
 if r.id is not null then
  if r.status<>'draft' then raise exception 'An active verification already exists';end if;
  if r.document_type<>p_document_type or r.verified_name<>n then raise exception 'Withdraw your current draft before changing its details';end if;
 else
  if (select count(*) from private.sera_identity_requests where user_id=u and created_at>now()-interval '24 hours')>=3 then raise exception 'Daily request limit reached';end if;
  insert into private.sera_identity_requests(user_id,verified_name,document_type) values(u,n,p_document_type) returning * into r;
  insert into private.sera_identity_audit(request_id,actor_id,action) values(r.id,u,'draft');
 end if;
 return jsonb_build_object('id',r.id,'path',u::text||'/'||r.id::text||'/evidence');
end$$;
create function public.sera_identity_submit(p_id uuid) returns void language plpgsql security definer set search_path='' as $$
declare r private.sera_identity_requests;
begin
 if not private.account_verified() then raise exception 'Verified email required';end if;
 select * into r from private.sera_identity_requests where id=p_id and user_id=auth.uid() for update;
 if r.status is distinct from 'draft' then raise exception 'Draft unavailable';end if;
 if not exists(select 1 from public.profiles where id=r.user_id and full_name=r.verified_name) then raise exception 'Profile name changed';end if;
 if not exists(select 1 from storage.objects where bucket_id='sera-identity-private' and name=r.user_id::text||'/'||r.id::text||'/evidence' and owner_id=r.user_id::text) then raise exception 'Evidence required';end if;
 update private.sera_identity_requests set status='pending',submitted_at=now() where id=r.id;
 insert into private.sera_identity_audit(request_id,actor_id,action) values(r.id,auth.uid(),'submit');
end$$;
create function public.sera_identity_withdraw(p_id uuid) returns void language plpgsql security definer set search_path='' as $$
begin
 if not private.account_verified() then raise exception 'Verified email required';end if;
 update private.sera_identity_requests set status='withdrawn' where id=p_id and user_id=auth.uid() and status in ('draft','pending');
 if not found then raise exception 'Request unavailable';end if;
 insert into private.sera_identity_audit(request_id,actor_id,action) values(p_id,auth.uid(),'withdraw');
end$$;
create function public.sera_identity_queue() returns table(id uuid,user_id uuid,verified_name text,document_type text,status text,submitted_at timestamptz,path text) language plpgsql stable security definer set search_path='' as $$
begin
 if not private.sera_identity_reviewer() then raise exception 'Reviewer access required';end if;
 return query select r.id,r.user_id,r.verified_name,r.document_type,r.status,r.submitted_at,r.user_id::text||'/'||r.id::text||'/evidence' from private.sera_identity_requests r where r.status in ('pending','approved') and r.user_id<>auth.uid() order by (r.status='pending') desc,r.submitted_at desc limit 100;
end$$;
create function public.sera_identity_review(p_id uuid,p_decision text,p_reason text) returns void language plpgsql security definer set search_path='' as $$
declare r private.sera_identity_requests;
begin
 if not private.sera_identity_reviewer() or p_decision not in ('approved','rejected','revoked') or length(trim(coalesce(p_reason,''))) not between 10 and 1000 then raise exception 'Invalid review';end if;
 select * into r from private.sera_identity_requests where id=p_id for update;
 if r.id is null or r.user_id=auth.uid() or not ((r.status='pending' and p_decision in ('approved','rejected')) or (r.status='approved' and p_decision='revoked')) then raise exception 'Invalid review transition';end if;
 if p_decision='approved' and (not exists(select 1 from public.profiles where id=r.user_id and full_name=r.verified_name) or not exists(select 1 from storage.objects where bucket_id='sera-identity-private' and name=r.user_id::text||'/'||r.id::text||'/evidence')) then raise exception 'Evidence or identity changed';end if;
 update private.sera_identity_requests set status=p_decision,reviewed_at=now(),reviewer_id=auth.uid(),decision_reason=trim(p_reason),expires_at=case when p_decision='approved' then now()+interval '1 year' else null end where id=r.id;
 insert into private.sera_identity_audit(request_id,actor_id,action) values(r.id,auth.uid(),p_decision);
end$$;
create function public.sera_identity_badges(p_ids uuid[]) returns table(id uuid) language sql stable security definer set search_path='' as $$
 select r.user_id from private.sera_identity_requests r join public.profiles p on p.id=r.user_id
 where private.account_verified() and r.user_id=any(p_ids[1:200]) and r.status='approved' and r.expires_at>now() and r.verified_name=p.full_name and private.can_view_member(r.user_id)
$$;
revoke all on function public.sera_identity_status(),public.sera_identity_start(text),public.sera_identity_submit(uuid),public.sera_identity_withdraw(uuid),public.sera_identity_queue(),public.sera_identity_review(uuid,text,text),public.sera_identity_badges(uuid[]) from public,anon;
grant execute on function public.sera_identity_status(),public.sera_identity_start(text),public.sera_identity_submit(uuid),public.sera_identity_withdraw(uuid),public.sera_identity_queue(),public.sera_identity_review(uuid,text,text),public.sera_identity_badges(uuid[]) to authenticated;
