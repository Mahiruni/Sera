-- Private, deduplicated job-match alerts. Delivery is explicit until notification preferences are ready.
alter table public.professional_notifications add column job_id uuid references public.jobs(id) on delete cascade;
alter table public.professional_notifications add column match_reasons text[] not null default '{}';
alter table public.professional_notifications drop constraint professional_notifications_kind_check;
alter table public.professional_notifications add constraint professional_notifications_kind_check check(kind in ('like','comment','share','follow','connection_request','connection_accepted','repost','reply','job_match'));
alter table public.professional_notifications add constraint professional_notifications_job_target check ((kind='job_match')=(job_id is not null) and (kind<>'job_match' or post_id is null));
create unique index professional_job_match_once on public.professional_notifications(recipient_id,job_id) where kind='job_match';
create policy "Job matches respect professional blocks" on public.professional_notifications as restrictive for select to authenticated using(kind<>'job_match' or private.sera_social_visible_to_me(actor_id));

-- Literal matching: punctuation such as C++ is not interpreted as a regular expression.
create function private.sera_job_match_terms(raw text) returns text[] language sql immutable set search_path='' as $$
 select coalesce(array_agg(t order by t),'{}'::text[]) from (
 select distinct lower(btrim(s)) as t from regexp_split_to_table(coalesce(raw,''), E'[,;\\n]+') s
 where char_length(btrim(s)) between 2 and 80 order by t limit 20
 ) x;
$$;
create function private.sera_job_term_matches(haystack text,needle text) returns boolean language sql immutable set search_path='' as $$
 select case when needle is null or needle='' then false else exists(
 select 1 from (select string_to_array(lower(coalesce(haystack,'')),lower(needle)) as parts) p
 cross join lateral generate_subscripts(p.parts,1) i
 where i<cardinality(p.parts) and right(p.parts[i],1)!~'[[:alnum:]]' and left(p.parts[i+1],1)!~'[[:alnum:]]'
 ) end;
$$;
revoke all on function private.sera_job_match_terms(text),private.sera_job_term_matches(text,text) from public,anon,authenticated;

create function public.sera_refresh_job_alerts() returns integer language plpgsql security definer set search_path='' as $$
declare u uuid:=auth.uid(); allowed integer; inserted integer; my_skills text[]; my_roles text[];
begin
 if u is null or not private.account_verified() then raise exception 'Verified account required';end if;
 perform pg_advisory_xact_lock(hashtextextended(u::text,29));
 select private.sera_job_match_terms(skills),private.sera_job_match_terms(preferred_roles) into my_skills,my_roles from public.profiles where id=u;
 if coalesce(cardinality(my_skills),0)+coalesce(cardinality(my_roles),0)=0 then return 0;end if;
 select greatest(0,3-count(*))::integer into allowed from public.professional_notifications where recipient_id=u and kind='job_match' and created_at>now()-interval '1 day';
 if allowed=0 then return 0;end if;
 with candidates as (
 select j.* from public.jobs j
 where j.status='open' and j.created_at>=now()-interval '7 days' and j.employer_id<>u
 and private.sera_social_visible(u,j.employer_id)
 and not exists(select 1 from public.applications a where a.job_id=j.id and a.candidate_id=u)
 and not exists(select 1 from public.professional_notifications n where n.recipient_id=u and n.kind='job_match' and n.job_id=j.id)
 order by j.created_at desc,j.id limit 100
 ), scored as (
 select j.id,j.employer_id,j.created_at,
 array(select s from unnest(my_skills) s where private.sera_job_term_matches(concat_ws(' ',j.title,j.description,j.requirements),s)) as skills,
 array(select s from unnest(my_roles) s where private.sera_job_term_matches(j.title,s)) as roles
 from candidates j
 )
 insert into public.professional_notifications(recipient_id,actor_id,kind,job_id,match_reasons)
 select u,s.employer_id,'job_match',s.id,
 array(select distinct t from unnest(s.roles||s.skills) t order by t limit 4)
 from scored s where cardinality(s.skills)+cardinality(s.roles)>0
 order by cardinality(s.roles)*6+cardinality(s.skills)*3 desc,s.created_at desc,s.id limit allowed
 on conflict do nothing;
 get diagnostics inserted=row_count;
 return inserted;
end $$;
revoke all on function public.sera_refresh_job_alerts() from public,anon;
grant execute on function public.sera_refresh_job_alerts() to authenticated;
