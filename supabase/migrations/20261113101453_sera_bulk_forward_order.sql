-- Assign the actual server instant to each insert, including sequential bulk sends.
create or replace function public.sera_guard_edit_insert() returns trigger language plpgsql security invoker set search_path='' as $$
begin
 if new.edited_at is not null then raise exception 'Edit timestamp is server managed'; end if;
 if (select auth.uid()) is not null then new.created_at=clock_timestamp(); end if;
 return new;
end; $$;
create or replace function public.sera_bulk_forward_text(p_source uuid,p_destination uuid,p_ids uuid[],p_clients uuid[]) returns uuid[] language plpgsql security invoker set search_path='' as $$
declare i integer; mid uuid; result uuid[]:=array[]::uuid[];begin
 if (select auth.uid()) is null or cardinality(p_ids) is null or cardinality(p_ids) not between 1 and 50 or cardinality(p_clients) is distinct from cardinality(p_ids)
 or (select count(distinct x) from unnest(p_clients) x)<>cardinality(p_ids) then raise exception 'Invalid selection';end if;
 if (select count(*) from public.professional_messages m where m.conversation_id=p_source and m.id=any(p_ids) and m.unsent_at is null and not exists(select 1 from public.professional_message_attachments a where a.message_id=m.id))<>cardinality(p_ids) then raise exception 'Only available text messages can be forwarded together';end if;
 if not exists(select 1 from public.professional_conversations where id=p_destination) then raise exception 'Destination unavailable';end if;
 for i in 1..cardinality(p_ids) loop
  mid=public.sera_send_message_v2(p_destination,p_clients[i],'',null,p_ids[i]);
  result=array_append(result,mid);
 end loop;return result;
end; $$;