begin;
create temporary table privacy_fixture(a uuid,b uuid,c uuid,thread uuid,message uuid,second uuid);
insert into privacy_fixture(a,b,c) values(gen_random_uuid(),gen_random_uuid(),gen_random_uuid());
grant select,update on privacy_fixture to authenticated;
insert into auth.users(id,email_confirmed_at) select unnest(array[a,b,c]),now() from privacy_fixture;
insert into public.professional_push_devices(user_id,token,platform) select b,'test-privacy-fixture-device-token','android' from privacy_fixture;

select set_config('request.jwt.claims',json_build_object('sub',a)::text,true) from privacy_fixture;
set local role authenticated;
insert into public.professional_connection_requests(requester_id,recipient_id) select a,b from privacy_fixture;
reset role;
select set_config('request.jwt.claims',json_build_object('sub',b)::text,true) from privacy_fixture;
set local role authenticated;
update public.professional_connection_requests set status='accepted' where requester_id=(select a from privacy_fixture);
reset role;
select set_config('request.jwt.claims',json_build_object('sub',a)::text,true) from privacy_fixture;
set local role authenticated;
with i as (insert into public.professional_conversations(member_a,member_b) select least(a,b),greatest(a,b) from privacy_fixture returning id) update privacy_fixture set thread=i.id from i;
with i as (insert into public.professional_messages(conversation_id,sender_id,body) select thread,a,'Private read' from privacy_fixture returning id) update privacy_fixture set message=i.id from i;

with i as (insert into public.professional_messages(conversation_id,sender_id,body) select thread,a,'Second text' from privacy_fixture returning id) update privacy_fixture set second=i.id from i;
select public.sera_bulk_star_messages(thread,array[message,second]) from privacy_fixture;
do $$ begin
 if (select count(*) from public.sera_starred_messages where user_id=(select a from privacy_fixture))<>2 then raise exception 'Bulk stars missing';end if;
end $$;
reset role;
create temp table bulk_client_ids(first_id uuid,second_id uuid);
insert into bulk_client_ids values(gen_random_uuid(),gen_random_uuid());
grant select on bulk_client_ids to authenticated;
set local role authenticated;
select public.sera_bulk_forward_text(thread,thread,array[message,second],array[first_id,second_id]) from privacy_fixture cross join bulk_client_ids;
select public.sera_bulk_forward_text(thread,thread,array[message,second],array[first_id,second_id]) from privacy_fixture cross join bulk_client_ids;
do $$ begin
 if (select count(*) from public.professional_messages where client_message_id in(select first_id from bulk_client_ids union select second_id from bulk_client_ids))<>2 then raise exception 'Bulk forwarding duplicated';end if;
end $$;
reset role;
create temporary table schedule_fixture(id uuid,cancel_id uuid);
insert into schedule_fixture values(gen_random_uuid(),gen_random_uuid());
grant select on schedule_fixture to authenticated;
set local role authenticated;
select public.sera_save_scheduled_message(s.id,f.thread,'Scheduled test',now()+interval '5 minutes') from schedule_fixture s cross join privacy_fixture f;
select public.sera_save_scheduled_message(s.id,f.thread,'Edited schedule',now()+interval '6 minutes') from schedule_fixture s cross join privacy_fixture f;
select public.sera_save_scheduled_message(s.cancel_id,f.thread,'Cancelled test',now()+interval '5 minutes') from schedule_fixture s cross join privacy_fixture f;
select public.sera_cancel_scheduled_message(cancel_id) from schedule_fixture;
reset role;
select set_config('request.jwt.claims',json_build_object('sub',b)::text,true) from privacy_fixture;
set local role authenticated;
do $$ begin
 if exists(select 1 from public.sera_scheduled_messages where id=(select id from schedule_fixture)) then raise exception 'Schedule leaked';end if;
 if public.sera_cancel_scheduled_message((select id from schedule_fixture)) then raise exception 'Peer cancelled';end if;
 if exists(select 1 from public.sera_starred_messages) then raise exception 'Private bulk stars leaked';end if;
end $$;
reset role;
update public.sera_scheduled_messages set send_at=now()-interval '1 minute' where id in(select id from schedule_fixture union select cancel_id from schedule_fixture);
select private.sera_deliver_scheduled_messages();
select private.sera_deliver_scheduled_messages();
do $$ begin
 if (select count(*) from public.professional_messages where client_message_id=(select id from schedule_fixture) and body='Edited schedule')<>1 then raise exception 'Schedule duplicate or missing';end if;
 if exists(select 1 from public.professional_messages where client_message_id=(select cancel_id from schedule_fixture)) then raise exception 'Cancelled schedule delivered';end if;
 if not exists(select 1 from public.sera_scheduled_messages where id=(select id from schedule_fixture) and state='sent' and message_id is not null) then raise exception 'Schedule not marked sent';end if;
end $$;
reset role;
select 'PASS: scheduled message authorization, editing, cancellation, delivery and bulk actions' as result;
rollback;
