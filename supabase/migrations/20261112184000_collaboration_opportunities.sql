create table public.sera_collaboration_opportunities(id uuid primary key default gen_random_uuid(),owner_id uuid not null references auth.users(id) on delete cascade,title text not null check(char_length(trim(title)) between 3 and 120),description text not null check(char_length(trim(description)) between 3 and 2000),skills text not null check(char_length(trim(skills)) between 2 and 500),closed boolean not null default false,created_at timestamptz not null default now());
create table public.sera_collaboration_interests(opportunity_id uuid not null references public.sera_collaboration_opportunities(id) on delete cascade,member_id uuid not null references auth.users(id) on delete cascade,created_at timestamptz not null default now(),primary key(opportunity_id,member_id));
alter table public.sera_collaboration_opportunities enable row level security;
alter table public.sera_collaboration_interests enable row level security;
revoke all on public.sera_collaboration_opportunities,public.sera_collaboration_interests from public,anon,authenticated;
create function public.sera_opportunities_list(p_mine boolean default false) returns table(id uuid,owner_id uuid,full_name text,title text,description text,skills text,matched_skills text,interested boolean,closed boolean,interest_count bigint) language sql stable security definer set search_path='' as $$
with my_skills as (select distinct lower(trim(t)) skill from public.profiles p cross join lateral regexp_split_to_table(coalesce(p.skills,''),'[,;|\n]+') t where p.id=auth.uid() and char_length(trim(t)) between 2 and 80)
select o.id,o.owner_id,coalesce(p.full_name,'SERA member'),o.title,o.description,o.skills,coalesce(m.matches,''),exists(select 1 from public.sera_collaboration_interests i where i.opportunity_id=o.id and i.member_id=auth.uid()),o.closed,(select count(*) from public.sera_collaboration_interests i where i.opportunity_id=o.id)
from public.sera_collaboration_opportunities o left join public.profiles p on p.id=o.owner_id
left join lateral(select string_agg(s.skill,', ' order by s.skill) matches,count(*) n from my_skills s where exists(select 1 from regexp_split_to_table(o.skills,'[,;|\n]+') t where lower(trim(t))=s.skill)) m on true
where auth.uid() is not null and (case when p_mine then o.owner_id=auth.uid() or exists(select 1 from public.sera_collaboration_interests i where i.opportunity_id=o.id and i.member_id=auth.uid()) else not o.closed end)
and not exists(select 1 from public.professional_blocks b where (b.blocker_id=auth.uid() and b.blocked_id=o.owner_id) or (b.blocker_id=o.owner_id and b.blocked_id=auth.uid()))
order by m.n desc,o.created_at desc,o.id limit 100;
$$;
create function public.sera_create_opportunity(p_title text,p_description text,p_skills text) returns uuid language plpgsql security definer set search_path='' as $$
declare u uuid:=auth.uid();v uuid;
begin
if u is null or not exists(select 1 from auth.users where id=u and email_confirmed_at is not null) then raise exception 'Verified account required';end if;
perform pg_advisory_xact_lock(hashtextextended(u::text,8));
if (select count(*) from public.sera_collaboration_opportunities where owner_id=u and not closed)>=20 then raise exception 'Opportunity limit reached';end if;
insert into public.sera_collaboration_opportunities(owner_id,title,description,skills) values(u,trim(p_title),trim(p_description),trim(p_skills)) returning id into v;return v;
end;$$;
create function public.sera_opportunity_interest(p_id uuid,p_interested boolean) returns void language plpgsql security definer set search_path='' as $$
declare u uuid:=auth.uid();o public.sera_collaboration_opportunities%rowtype;
begin
if u is null or not exists(select 1 from auth.users where id=u and email_confirmed_at is not null) then raise exception 'Verified account required';end if;
if not p_interested then delete from public.sera_collaboration_interests where opportunity_id=p_id and member_id=u;return;end if;
select * into o from public.sera_collaboration_opportunities where id=p_id for update;
if not found or o.closed or o.owner_id=u or exists(select 1 from public.professional_blocks b where (b.blocker_id=u and b.blocked_id=o.owner_id) or (b.blocker_id=o.owner_id and b.blocked_id=u)) then raise exception 'Opportunity unavailable';end if;
insert into public.sera_collaboration_interests(opportunity_id,member_id) values(p_id,u) on conflict do nothing;
end;$$;
create function public.sera_close_opportunity(p_id uuid) returns void language plpgsql security definer set search_path='' as $$
begin update public.sera_collaboration_opportunities set closed=true where id=p_id and owner_id=auth.uid();if not found then raise exception 'Owner access required';end if;end;$$;
create function public.sera_opportunity_applicants(p_id uuid) returns table(member_id uuid,full_name text) language sql stable security definer set search_path='' as $$
select i.member_id,coalesce(p.full_name,'SERA member') from public.sera_collaboration_interests i join public.sera_collaboration_opportunities o on o.id=i.opportunity_id left join public.profiles p on p.id=i.member_id where i.opportunity_id=p_id and o.owner_id=auth.uid() and not exists(select 1 from public.professional_blocks b where (b.blocker_id=auth.uid() and b.blocked_id=i.member_id) or (b.blocker_id=i.member_id and b.blocked_id=auth.uid())) order by i.created_at desc limit 100;
$$;
revoke all on function public.sera_opportunities_list(boolean),public.sera_create_opportunity(text,text,text),public.sera_opportunity_interest(uuid,boolean),public.sera_close_opportunity(uuid),public.sera_opportunity_applicants(uuid) from public,anon;
grant execute on function public.sera_opportunities_list(boolean),public.sera_create_opportunity(text,text,text),public.sera_opportunity_interest(uuid,boolean),public.sera_close_opportunity(uuid),public.sera_opportunity_applicants(uuid) to authenticated;
