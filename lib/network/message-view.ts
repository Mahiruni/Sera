import "server-only";
import { serverClient } from "@/lib/supabase/server";
import { isChatMime, type MessageView, type ChatFile } from "@/lib/network/message-contract";
type Db=Awaited<ReturnType<typeof serverClient>>;
type Row={id:string;sender_id:string;body:string;created_at:string;read_at:string|null;delivered_at:string|null;client_message_id:string|null;reply_to_id:string|null;is_forwarded:boolean;unsent_at:string|null};
const fields="id,sender_id,body,created_at,read_at,delivered_at,client_message_id,reply_to_id,is_forwarded,unsent_at";
export async function messageViews(db:Db,conversation:string,rows:Row[]):Promise<MessageView[]> {
  if(!rows.length)return [];
  const ids=rows.map(m=>m.id);
  const replyIds=[...new Set(rows.flatMap(m=>m.reply_to_id?[m.reply_to_id]:[]))];
  const [attachments,reactions,replies]=await Promise.all([
    db.from("professional_message_attachments").select("id,message_id,file_name,mime_type,byte_size,duration_ms").in("message_id",ids),
    db.from("professional_message_reactions").select("message_id,member_id,emoji").eq("conversation_id",conversation).in("message_id",ids).not("emoji","is",null),
    replyIds.length?db.from("professional_messages").select("id,sender_id,body,unsent_at").eq("conversation_id",conversation).in("id",replyIds):Promise.resolve({data:[]}),
  ]);
  const fileMap=new Map<string,ChatFile[]>();
  for(const a of attachments.data??[]){
    if(!isChatMime(a.mime_type))continue;
    const list=fileMap.get(a.message_id)??[];
    list.push({id:a.id,name:a.file_name,mime:a.mime_type,size:a.byte_size,durationMs:a.duration_ms,url:"/api/network/messages/attachment?id="+encodeURIComponent(a.id)});
    fileMap.set(a.message_id,list);
  }
  const reactionMap=new Map<string,{memberId:string;emoji:string}[]>();
  for(const r of reactions.data??[]){
    if(!r.emoji)continue;
    const list=reactionMap.get(r.message_id)??[];list.push({memberId:r.member_id,emoji:r.emoji});reactionMap.set(r.message_id,list);
  }
  const replyMap=new Map<string,{id:string;senderId:string;body:string;unsent:boolean}>();
  for(const r of replies.data??[])replyMap.set(r.id,{id:r.id,senderId:r.sender_id,body:r.unsent_at?"Message unsent":r.body,unsent:Boolean(r.unsent_at)});
  return rows.map(m=>({
    id:m.id,senderId:m.sender_id,body:m.unsent_at?"Message unsent":m.body,createdAt:m.created_at,
    readAt:m.read_at,deliveredAt:m.delivered_at,clientId:m.client_message_id,forwarded:m.is_forwarded,unsent:Boolean(m.unsent_at),
    reply:m.reply_to_id?replyMap.get(m.reply_to_id)??{id:m.reply_to_id,senderId:"",body:"Message unavailable",unsent:true}:null,
    files:m.unsent_at?[]:fileMap.get(m.id)??[],reactions:m.unsent_at?[]:reactionMap.get(m.id)??[],
  }));
}
export async function loadMessagePage(db:Db,conversation:string,search="",before?:{id:string;createdAt:string}) {
  let q=db.from("professional_messages").select(fields).eq("conversation_id",conversation);
  if(search)q=q.ilike("body","%"+search.replace(/[\\%_]/g,"\\$&")+"%");
  if(before)q=q.or("created_at.lt."+before.createdAt+",and(created_at.eq."+before.createdAt+",id.lt."+before.id+")");
  const {data,error}=await q.order("created_at",{ascending:false}).order("id",{ascending:false}).limit(61);
  if(error)return {messages:[] as MessageView[],hasMore:false,error:"Could not load messages. Try again."};
  const rows=(data??[]) as unknown as Row[];
  return {messages:await messageViews(db,conversation,rows.slice(0,60).reverse()),hasMore:rows.length>60,error:""};
}
export async function refreshMessageViews(db:Db,conversation:string,ids:string[]) {
  const rows:Row[]=[];
  for(let i=0;i<ids.length;i+=100){
    const {data,error}=await db.from("professional_messages").select(fields).eq("conversation_id",conversation).in("id",ids.slice(i,i+100));
    if(error)throw new Error("Could not refresh message history");
    rows.push(...(data??[]) as unknown as Row[]);
  }
  return messageViews(db,conversation,rows);
}
