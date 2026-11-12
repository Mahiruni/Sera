-- Prevent unattended collection of identity evidence; dedicated least-privilege reviewers are supported.
create or replace function private.sera_identity_reviewer() returns boolean language sql stable security definer set search_path='' as $$
 select private.account_verified() and exists(select 1 from auth.users where id=auth.uid() and (raw_app_meta_data->>'role' in ('admin','moderator') or raw_app_meta_data->>'identity_reviewer'='true'))
$$;
create function private.sera_identity_has_reviewer(p_user uuid) returns boolean language sql stable security definer set search_path='' as $$
 select p_user is not null and exists(select 1 from auth.users where id<>p_user and email_confirmed_at is not null and (raw_app_meta_data->>'role' in ('admin','moderator') or raw_app_meta_data->>'identity_reviewer'='true'))
$$;
revoke all on function private.sera_identity_has_reviewer(uuid) from public,anon,authenticated;
create or replace function public.sera_identity_status() returns jsonb language plpgsql stable security definer set search_path='' as $$
declare r private.sera_identity_requests; n text;
begin
 if not private.account_verified() then raise exception 'Verified email required';end if;
 select * into r from private.sera_identity_requests where user_id=auth.uid() order by created_at desc limit 1;
 select full_name into n from public.profiles where id=auth.uid();
 if r.id is null then return jsonb_build_object('status','none','profile_name',n,'intake_available',private.sera_identity_has_reviewer(auth.uid()));end if;
 return jsonb_build_object('id',r.id,'intake_available',private.sera_identity_has_reviewer(auth.uid()),'status',case when r.status='approved' and (r.expires_at<=now() or r.verified_name is distinct from n) then 'expired' else r.status end,'profile_name',n,'verified_name',r.verified_name,'document_type',r.document_type,'reason',r.decision_reason,'expires_at',r.expires_at,'evidence_present',exists(select 1 from storage.objects o where o.bucket_id='sera-identity-private' and o.name=r.user_id::text||'/'||r.id::text||'/evidence'),'path',r.user_id::text||'/'||r.id::text||'/evidence');
end$$;
create or replace function public.sera_identity_start(p_document_type text,p_consent boolean) returns jsonb language plpgsql security definer set search_path='' as $$
declare u uuid:=auth.uid(); n text; r private.sera_identity_requests;
begin
 if not private.account_verified() or p_consent is distinct from true or p_document_type is null or p_document_type not in ('national_id','passport','driver_license') then raise exception 'Invalid verification request';end if;
 if not private.sera_identity_has_reviewer(u) then raise exception 'Verification intake is temporarily paused';end if;
 perform pg_advisory_xact_lock(hashtextextended(u::text,1351));
 select full_name into n from public.profiles where id=u;
 if n is null or length(trim(n))<2 then raise exception 'Complete your profile first';end if;
 update private.sera_identity_requests set status='revoked',reviewed_at=now(),decision_reason='Name changed or verification expired' where user_id=u and status='approved' and (expires_at<=now() or verified_name is distinct from n);
 select * into r from private.sera_identity_requests where user_id=u and status in ('draft','pending','approved') for update;
 if r.id is not null then
  if r.status<>'draft' then raise exception 'An active verification already exists';end if;
  if r.document_type<>p_document_type or r.verified_name<>n then raise exception 'Withdraw your current draft before changing its details';end if;
 else
  if (select count(*) from private.sera_identity_requests where user_id=u and created_at>now()-interval '24 hours')>=3 then raise exception 'Daily request limit reached';end if;
  insert into private.sera_identity_requests(user_id,verified_name,document_type,consented_at) values(u,n,p_document_type,now()) returning * into r;
  insert into private.sera_identity_audit(request_id,actor_id,action) values(r.id,u,'draft');
 end if;
 return jsonb_build_object('id',r.id,'path',u::text||'/'||r.id::text||'/evidence');
end$$;
create or replace function public.sera_identity_submit(p_id uuid) returns void language plpgsql security definer set search_path='' as $$
declare r private.sera_identity_requests;
begin
 if not private.account_verified() then raise exception 'Verified email required';end if;
 if not private.sera_identity_has_reviewer(auth.uid()) then raise exception 'Verification intake is temporarily paused';end if;
 select * into r from private.sera_identity_requests where id=p_id and user_id=auth.uid() for update;
 if r.status is distinct from 'draft' then raise exception 'Draft unavailable';end if;
 if not exists(select 1 from public.profiles where id=r.user_id and full_name=r.verified_name) then raise exception 'Profile name changed';end if;
 if not exists(select 1 from storage.objects where bucket_id='sera-identity-private' and name=r.user_id::text||'/'||r.id::text||'/evidence' and owner_id=r.user_id::text) then raise exception 'Evidence required';end if;
 update private.sera_identity_requests set status='pending',submitted_at=now() where id=r.id;
 insert into private.sera_identity_audit(request_id,actor_id,action) values(r.id,auth.uid(),'submit');
end$$;
create or replace function private.sera_identity_evidence_access(p_name text,p_action text) returns boolean language sql stable security definer set search_path='' as $$
 select private.account_verified() and exists(select 1 from private.sera_identity_requests r where p_name=r.user_id::text||'/'||r.id::text||'/evidence' and
 ((p_action='upload' and r.user_id=auth.uid() and r.status='draft' and private.sera_identity_has_reviewer(r.user_id)) or
 (p_action='read' and r.user_id=auth.uid() and r.status in ('draft','pending','rejected','withdrawn','approved','revoked')) or
 (p_action='delete' and r.user_id=auth.uid() and r.status in ('draft','rejected','withdrawn','approved','revoked'))))
$$;
