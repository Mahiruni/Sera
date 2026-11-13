import {MessageWorkspace} from "@/components/network/sera-message-workspace";
import {requiredProfile} from "@/lib/auth/profile";
import {serverClient} from "@/lib/supabase/server";
import {memberCards} from "@/lib/activity/members";
import {loadMessagePage} from "@/lib/network/message-view";
import {validMessageId,type ThreadPreview} from "@/lib/network/message-contract";
export const metadata={title:"Professional messages | Sera",robots:{index:false,follow:false}};
type InboxRow={id:string;member_a:string;member_b:string;last_message_at:string|null;last_message_id:string|null;last_body:string|null;last_sender_id:string|null;last_created_at:string|null;last_delivered_at:string|null;unread_count:number};
export default async function ProfessionalInbox({searchParams}:{searchParams:Promise<{thread?:string;q?:string}>}){
  const {user}=await requiredProfile("/network/messages");const db=await serverClient(),params=await searchParams;
  const search=typeof params.q==="string"?params.q.trim().slice(0,120):"";
  const {data,error}=await db.rpc("sera_inbox_v2",{p_limit:200});
  const rows=(data??[]) as unknown as InboxRow[];
  if(validMessageId(params.thread)&&!rows.some(r=>r.id===params.thread)){
    const {data:extra}=await db.from("professional_conversations").select("id,member_a,member_b,last_message_at").eq("id",params.thread).maybeSingle();
    if(extra)rows.unshift({...extra,last_message_id:null,last_body:null,last_sender_id:null,last_created_at:null,last_delivered_at:null,unread_count:0});
  }
  const {data:accepted}=await db.from("professional_connection_requests").select("requester_id,recipient_id").eq("status","accepted").or("requester_id.eq."+user.id+",recipient_id.eq."+user.id).limit(100);
  const {data:controls}=await db.from("sera_conversation_preferences").select("conversation_id,pinned,archived,marked_unread,muted_until").eq("user_id",user.id);
  const byThread=new Map((controls??[]).map(c=>[c.conversation_id,c]));
  const peerIds=rows.map(r=>r.member_a===user.id?r.member_b:r.member_a);
  const existing=new Set(peerIds);
  const candidateIds=[...new Set((accepted??[]).map(r=>r.requester_id===user.id?r.recipient_id:r.requester_id))].filter(id=>!existing.has(id));
  const people=await memberCards([...peerIds,...candidateIds]);
  const threads:ThreadPreview[]=rows.map(r=>{const peerId=r.member_a===user.id?r.member_b:r.member_a,person=people.get(peerId);return {id:r.id,peerId,name:person?.name??"SERA member",photo:person?.photo,lastBody:r.last_body,lastAt:r.last_created_at??r.last_message_at,unread:Number(r.unread_count)||0,lastMessageId:r.last_message_id,lastSenderId:r.last_sender_id,lastDeliveredAt:r.last_delivered_at,pinned:byThread.get(r.id)?.pinned??false,archived:byThread.get(r.id)?.archived??false,markedUnread:byThread.get(r.id)?.marked_unread??false,mutedUntil:byThread.get(r.id)?.muted_until??null};});
  const selected=validMessageId(params.thread)?threads.find(t=>t.id===params.thread)??null:null;
  const page=selected?await loadMessagePage(db,selected.id,search):{messages:[],hasMore:false,error:""};
  return <div className="sera-network network-app sera-inbox sera-chat-page">{error&&<p role="alert">Inbox could not be loaded. Refresh to retry.</p>}<MessageWorkspace userId={user.id} threads={threads} selected={selected} messages={page.messages} hasMore={page.hasMore} search={search} loadError={page.error} candidates={candidateIds.map(id=>({id,name:people.get(id)?.name??"SERA member",photo:people.get(id)?.photo}))}/></div>;
}
