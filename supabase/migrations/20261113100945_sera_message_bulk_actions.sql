create function public.sera_bulk_star_messages(p_conversation uuid,p_ids uuid[]) returns integer language plpgsql security invoker set search_path='' as $$
declare n integer; begin
 if (select auth.uid()) is null or cardinality(p_ids) not between 1 and 50 or cardinality(p_ids) is null then raise exception 'Invalid selection';end if;
 if (select count(*) from public.professional_messages where conversation_id=p_conversation and id=any(p_ids) and unsent_at is null)<>cardinality(p_ids) then raise exception 'Selection unavailable';end if;
 insert into public.sera_starred_messages(user_id,message_id) select (select auth.uid()),id from public.professional_messages where conversation_id=p_conversation and id=any(p_ids) and unsent_at is null on conflict do nothing;
 get diagnostics n=row_count;return n;
end; $$;
create function public.sera_bulk_forward_text(p_source uuid,p_destination uuid,p_ids uuid[],p_clients uuid[]) returns uuid[] language plpgsql security invoker set search_path='' as $$
declare i integer; mid uuid; result uuid[]:=array[]::uuid[];begin
 if (select auth.uid()) is null or cardinality(p_ids) is null or cardinality(p_ids) not between 1 and 50 or cardinality(p_clients) is distinct from cardinality(p_ids) then raise exception 'Invalid selection';end if;
 if (select count(*) from public.professional_messages m where m.conversation_id=p_source and m.id=any(p_ids) and m.unsent_at is null and not exists(select 1 from public.professional_message_attachments a where a.message_id=m.id))<>cardinality(p_ids) then raise exception 'Only available text messages can be forwarded together';end if;
 if not exists(select 1 from public.professional_conversations where id=p_destination) then raise exception 'Destination unavailable';end if;
 for i in 1..cardinality(p_ids) loop
  mid=public.sera_send_message_v2(p_destination,p_clients[i],'',null,p_ids[i]);
  result=array_append(result,mid);
 end loop;return result;
end; $$;
revoke all on function public.sera_bulk_star_messages(uuid,uuid[]),public.sera_bulk_forward_text(uuid,uuid,uuid[],uuid[]) from public,anon;
grant execute on function public.sera_bulk_star_messages(uuid,uuid[]),public.sera_bulk_forward_text(uuid,uuid,uuid[],uuid[]) to authenticated;