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

select public.sera_edit_message(message,'Edited searchable phrase') from privacy_fixture;
select public.sera_set_message_star(message,true) from privacy_fixture;
select public.sera_set_message_pin(thread,message,true) from privacy_fixture;
do $$ begin
 if not exists(select 1 from public.professional_messages where id=(select message from privacy_fixture) and edited_at is not null) then raise exception 'Edit missing'; end if;
 if not exists(select 1 from public.sera_message_library((select thread from privacy_fixture),'search','searchable') ) then raise exception 'Search failed'; end if;
 if not exists(select 1 from public.sera_message_library((select thread from privacy_fixture),'star')) then raise exception 'Star missing';end if;
end $$;
reset role;
select set_config('request.jwt.claims',json_build_object('sub',b)::text,true) from privacy_fixture;
set local role authenticated;
do $$ begin
 if exists(select 1 from public.sera_message_library((select thread from privacy_fixture),'star')) then raise exception 'Private star exposed';end if;
 if not exists(select 1 from public.sera_message_library((select thread from privacy_fixture),'pin')) then raise exception 'Shared pin missing';end if;
 begin perform public.sera_edit_message((select message from privacy_fixture),'forbidden');raise exception 'Unauthorized edit succeeded';exception when others then if sqlerrm='Unauthorized edit succeeded' then raise;end if;end;
end $$;
reset role;
select set_config('request.jwt.claims',json_build_object('sub',c)::text,true) from privacy_fixture;
set local role authenticated;
do $$ begin
 if exists(select 1 from public.sera_message_library((select thread from privacy_fixture),'search','searchable')) then raise exception 'Outsider search leaked';end if;
end $$;
reset role;
select set_config('request.jwt.claims',json_build_object('sub',a)::text,true) from privacy_fixture;
set local role authenticated;
update public.professional_messages set unsent_at=now() where id=(select message from privacy_fixture);
with i as (insert into public.professional_messages(conversation_id,sender_id,body) select thread,a,'Replacement pin' from privacy_fixture returning id) update privacy_fixture set second=i.id from i;
select public.sera_set_message_pin(thread,second,true) from privacy_fixture;
do $$ begin
 if exists(select 1 from public.sera_message_library((select thread from privacy_fixture),'star')) then raise exception 'Unsent star visible';end if;
 if not exists(select 1 from public.sera_message_library((select thread from privacy_fixture),'pin') where id=(select second from privacy_fixture)) then raise exception 'Replacement pin failed';end if;
end $$;
reset role;
select set_config('request.jwt.claims',json_build_object('sub',a)::text,true) from privacy_fixture;
set local role authenticated;
with i as (insert into public.professional_messages(conversation_id,sender_id,body) select thread,a,'Library advanced test' from privacy_fixture returning id) update privacy_fixture set message=i.id from i;
select public.sera_set_message_pin(thread,message,true) from privacy_fixture;
select public.sera_set_message_star(message,true) from privacy_fixture;
do $$ begin
 if (select count(*) from public.sera_message_library_v2((select thread from privacy_fixture),'pin'))<>2 then raise exception 'Multiple pins missing';end if;
 if not exists(select 1 from public.sera_message_library_v2(null,'star') where id=(select message from privacy_fixture)) then raise exception 'Global star missing';end if;
 if not exists(select 1 from public.sera_message_library_v2((select thread from privacy_fixture),'search','advanced','you',now()-interval '1 day',now()+interval '1 day')) then raise exception 'Search filters failed';end if;
 if exists(select 1 from public.sera_message_library_v2((select thread from privacy_fixture),'search','advanced','contact')) then raise exception 'Sender filter failed';end if;
 if not exists(select 1 from public.sera_message_context((select thread from privacy_fixture),(select message from privacy_fixture)) where id=(select second from privacy_fixture)) then raise exception 'Context missing';end if;
end $$;
reset role;
select set_config('request.jwt.claims',json_build_object('sub',b)::text,true) from privacy_fixture;
set local role authenticated;
do $$ begin
 if exists(select 1 from public.sera_message_library_v2(null,'star')) then raise exception 'Global star leaked';end if;
 if (select count(*) from public.sera_message_library_v2((select thread from privacy_fixture),'pin'))<>2 then raise exception 'Shared pins missing';end if;
end $$;
reset role;
select set_config('request.jwt.claims',json_build_object('sub',c)::text,true) from privacy_fixture;
set local role authenticated;
do $$ begin
 if exists(select 1 from public.sera_message_context((select thread from privacy_fixture),(select message from privacy_fixture))) then raise exception 'Outsider history leak';end if;
end $$;
reset role;
select 'PASS: message library, multiple pins, global stars, filters and history authorization' as result;
rollback;
