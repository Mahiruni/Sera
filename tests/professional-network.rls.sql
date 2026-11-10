begin;
create temporary table network_fixture(a uuid,b uuid,c uuid,thread uuid,message uuid);
insert into network_fixture(a,b,c) values(gen_random_uuid(),gen_random_uuid(),gen_random_uuid());
grant select,update on network_fixture to authenticated;
insert into auth.users(id,email_confirmed_at) select unnest(array[a,b,c]),now() from network_fixture;
select set_config('request.jwt.claims',json_build_object('sub',a)::text,true) from network_fixture;
set local role authenticated;
insert into public.professional_follows(follower_id,followed_id) select a,b from network_fixture;
insert into public.professional_connection_requests(requester_id,recipient_id) select a,b from network_fixture;
do $$ begin
  if (select count(*) from public.professional_connection_requests) <> 1 then raise exception 'Requester cannot view own request';end if;
  begin
    insert into public.professional_connection_requests(requester_id,recipient_id) select b,c from network_fixture;
    raise exception 'Forged requester accepted';
  exception when check_violation or insufficient_privilege then null;
  end;
end $$;
reset role;
select set_config('request.jwt.claims',json_build_object('sub',c)::text,true) from network_fixture;
set local role authenticated;
do $$ begin
 if exists(select from public.professional_connection_requests) then raise exception 'Outsider can view private requests'; end if;
 if exists(select from public.professional_follows where follower_id=(select a from network_fixture)) = false then raise exception 'Public follow not visible'; end if;
end $$;
reset role;
select set_config('request.jwt.claims',json_build_object('sub',b)::text,true) from network_fixture;
set local role authenticated;
update public.professional_connection_requests set status='accepted' where requester_id=(select a from network_fixture);
do $$ begin
 if (select count(*) from public.professional_connection_requests where status='accepted') <> 1 then raise exception 'Acceptance failed'; end if;
end $$;
reset role;
select set_config('request.jwt.claims',json_build_object('sub',a)::text,true) from network_fixture;
set local role authenticated;
with inserted as (
 insert into public.professional_conversations(member_a,member_b)
 select least(a,b),greatest(a,b) from network_fixture returning id
) update network_fixture set thread=inserted.id from inserted;
with inserted as (
 insert into public.professional_messages(conversation_id,sender_id,body)
 select thread,a,'Hello' from network_fixture returning id
) update network_fixture set message=inserted.id from inserted;
do $$ begin
 if (select count(*) from public.professional_messages) <> 1 then raise exception 'Sender cannot read own message';end if;
end $$;
reset role;
select set_config('request.jwt.claims',json_build_object('sub',c)::text,true) from network_fixture;
set local role authenticated;
do $$ begin
 if exists(select from public.professional_conversations) or exists(select from public.professional_messages) then
   raise exception 'Outsider read private conversation';
 end if;
 begin
  insert into public.professional_messages(conversation_id,sender_id,body) select thread,c,'Intrusion' from network_fixture;
  raise exception 'Outsider sent a message';
 exception when check_violation or insufficient_privilege then null;
 end;
end $$;
reset role;
select set_config('request.jwt.claims',json_build_object('sub',b)::text,true) from network_fixture;
set local role authenticated;
update public.professional_messages set read_at=now() where id=(select message from network_fixture);
do $$ begin
 if (select count(*) from public.professional_messages where read_at is not null) <> 1 then raise exception 'Recipient cannot mark read'; end if;
end $$;
reset role;
select set_config('request.jwt.claims',json_build_object('sub',a)::text,true) from network_fixture;
set local role authenticated;
insert into public.professional_message_attachments(message_id,owner_id,storage_path,file_name,mime_type,byte_size)
select message,a,a::text || '/' || thread::text || '/test.pdf','test.pdf','application/pdf',1024 from network_fixture;
do $$ begin
 if (select count(*) from public.professional_message_attachments) <> 1 then
  raise exception 'Sender cannot see own attachment';
 end if;
end $$;
reset role;
select set_config('request.jwt.claims',json_build_object('sub',c)::text,true) from network_fixture;
set local role authenticated;
do $$ begin
 if exists(select from public.professional_message_attachments) then
  raise exception 'Outsider can view private attachments';
 end if;
end $$;
reset role;
select set_config('request.jwt.claims',json_build_object('sub',a)::text,true) from network_fixture;
set local role authenticated;
insert into public.professional_blocks(blocker_id,blocked_id) select a,b from network_fixture;
do $$ begin
 if exists(select from public.professional_messages) then raise exception 'Block did not hide conversation messages'; end if;
 if exists(select from public.professional_message_attachments) then raise exception 'Block did not hide attachments'; end if;
 if exists(select from public.professional_conversations) then raise exception 'Block did not hide conversation metadata'; end if;
end $$;
reset role;
select set_config('request.jwt.claims',json_build_object('sub',b)::text,true) from network_fixture;
set local role authenticated;
do $$ begin
 if exists(select from public.professional_messages) then raise exception 'Blocked recipient can still read messages'; end if;
 if exists(select from public.professional_message_attachments) then raise exception 'Blocked recipient can still read attachments'; end if;
 if exists(select from public.professional_conversations) then raise exception 'Blocked recipient can still read thread metadata'; end if;
 begin
  insert into public.professional_messages(conversation_id,sender_id,body) select thread,b,'Blocked send' from network_fixture;
  raise exception 'Blocked message sent';
 exception when check_violation or insufficient_privilege then null;
 end;
end $$;
reset role;
select set_config('request.jwt.claims',json_build_object('sub',a)::text,true) from network_fixture;
set local role authenticated;
insert into public.professional_reports(reporter_id,reported_id,reason,details)
select a,b,'spam','Repeated unsolicited messages' from network_fixture;
do $$ begin
 if (select count(*) from public.professional_reports) <> 1 then
   raise exception 'Reporter cannot see own report';
 end if;
end $$;
reset role;
select set_config('request.jwt.claims',json_build_object('sub',b)::text,true) from network_fixture;
set local role authenticated;
do $$ begin
 if exists(select from public.professional_reports) then
   raise exception 'Reported member can view confidential report';
 end if;
 begin
  insert into public.professional_reports(reporter_id,reported_id,reason)
  select a,c,'spam' from network_fixture;
  raise exception 'Forged reporter accepted';
 exception when check_violation or insufficient_privilege then null;
 end;
end $$;
reset role;
set local role anon;
do $$ begin
 begin perform * from public.professional_messages;raise exception 'Anonymous read allowed';exception when insufficient_privilege then null;end;
end $$;
reset role;
rollback;
select 'PASS: professional follows, invitations, message isolation and read receipts' as result;
