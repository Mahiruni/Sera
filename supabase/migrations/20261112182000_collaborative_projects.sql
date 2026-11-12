create table public.sera_space_projects(id uuid primary key default gen_random_uuid(),space_id uuid not null references public.sera_collaboration_spaces(id) on delete cascade,title text not null check(char_length(trim(title)) between 3 and 100),description text not null default '' check(char_length(description)<=1000),created_at timestamptz not null default now());
create table public.sera_project_tasks(id uuid primary key default gen_random_uuid(),project_id uuid not null references public.sera_space_projects(id) on delete cascade,title text not null check(char_length(trim(title)) between 1 and 150),assignee_id uuid references auth.users(id) on delete set null,status text not null default 'todo' check(status in ('todo','doing','done')),due_date date,created_at timestamptz not null default now());
create index sera_project_tasks_project on public.sera_project_tasks(project_id,created_at);
alter table public.sera_space_projects enable row level security;
alter table public.sera_project_tasks enable row level security;
revoke all on public.sera_space_projects,public.sera_project_tasks from public,anon,authenticated;
create function public.sera_space_projects_list(p_space uuid) returns table(id uuid,title text,description text,total bigint,completed bigint) language sql stable security definer set search_path='' as $$
select p.id,p.title,p.description,(select count(*) from public.sera_project_tasks t where t.project_id=p.id),(select count(*) from public.sera_project_tasks t where t.project_id=p.id and t.status='done') from public.sera_space_projects p where p.space_id=p_space and exists(select 1 from public.sera_collaboration_members m where m.space_id=p_space and m.member_id=auth.uid() and m.status='active') order by p.created_at desc limit 50;
$$;
create function public.sera_create_space_project(p_space uuid,p_title text,p_description text) returns uuid language plpgsql security definer set search_path='' as $$
declare v uuid;
begin
 if not exists(select 1 from public.sera_collaboration_spaces where id=p_space and owner_id=auth.uid() for update) then raise exception 'Owner access required'; end if;
 if (select count(*) from public.sera_space_projects where space_id=p_space)>=50 then raise exception 'Project limit reached';end if;
 insert into public.sera_space_projects(space_id,title,description) values(p_space,trim(p_title),trim(coalesce(p_description,''))) returning id into v;return v;
end;$$;
create function public.sera_project_tasks_list(p_project uuid) returns table(id uuid,title text,status text,assignee_id uuid,assignee_name text,due_date date) language sql stable security definer set search_path='' as $$
select t.id,t.title,t.status,t.assignee_id,case when m.member_id is not null then coalesce(p.full_name,'SERA member') else null end,t.due_date from public.sera_project_tasks t join public.sera_space_projects j on j.id=t.project_id left join public.sera_collaboration_members m on m.space_id=j.space_id and m.member_id=t.assignee_id and m.status='active' left join public.profiles p on p.id=m.member_id where t.project_id=p_project and exists(select 1 from public.sera_collaboration_members x where x.space_id=j.space_id and x.member_id=auth.uid() and x.status='active') order by case t.status when 'doing' then 0 when 'todo' then 1 else 2 end,t.created_at limit 200;
$$;
create function public.sera_create_project_task(p_project uuid,p_title text,p_assignee uuid default null,p_due date default null) returns uuid language plpgsql security definer set search_path='' as $$
declare s uuid; v uuid;
begin
 select j.space_id into s from public.sera_space_projects j join public.sera_collaboration_spaces c on c.id=j.space_id where j.id=p_project and c.owner_id=auth.uid() for update of j;
 if s is null then raise exception 'Owner access required';end if;
 if p_assignee is not null and not exists(select 1 from public.sera_collaboration_members where space_id=s and member_id=p_assignee and status='active') then raise exception 'Active member required';end if;
 if (select count(*) from public.sera_project_tasks where project_id=p_project)>=200 then raise exception 'Task limit reached';end if;
 insert into public.sera_project_tasks(project_id,title,assignee_id,due_date) values(p_project,trim(p_title),p_assignee,p_due) returning id into v;return v;
end;$$;
create function public.sera_update_task_status(p_task uuid,p_status text) returns void language plpgsql security definer set search_path='' as $$
begin
 update public.sera_project_tasks t set status=p_status from public.sera_space_projects j,public.sera_collaboration_spaces c where t.id=p_task and j.id=t.project_id and c.id=j.space_id and (c.owner_id=auth.uid() or t.assignee_id=auth.uid()) and exists(select 1 from public.sera_collaboration_members m where m.space_id=c.id and m.member_id=auth.uid() and m.status='active');
 if not found then raise exception 'Task unavailable';end if;
end;$$;
revoke all on function public.sera_space_projects_list(uuid),public.sera_create_space_project(uuid,text,text),public.sera_project_tasks_list(uuid),public.sera_create_project_task(uuid,text,uuid,date),public.sera_update_task_status(uuid,text) from public,anon;
grant execute on function public.sera_space_projects_list(uuid),public.sera_create_space_project(uuid,text,text),public.sera_project_tasks_list(uuid),public.sera_create_project_task(uuid,text,uuid,date),public.sera_update_task_status(uuid,text) to authenticated;
