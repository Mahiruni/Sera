"use server";
import { after } from "next/server";
import { revalidatePath } from "next/cache";
import { currentUser,serverClient } from "@/lib/supabase/server";
import { notifyMessageOnce } from "@/lib/network/push-delivery";
import { validMessageId,validAttachment,messageFilePath,hasFileSignature,MESSAGE_LIMIT,REACTIONS,isChatMime,type SendInput,type AttachmentMeta } from "@/lib/network/message-contract";
import { loadMessagePage,refreshMessageViews } from "@/lib/network/message-view";
const BUCKET="professional-message-files";
type Db=Awaited<ReturnType<typeof serverClient>>;
type Result={ok:true;id:string;warning?:string}|{ok:false;error:string};
async function context(conversation:string){
  const user=await currentUser();
  if(!user?.email_confirmed_at||!validMessageId(conversation))return null;
  const db=await serverClient();
  const {data}=await db.from("professional_conversations").select("id,member_a,member_b").eq("id",conversation).maybeSingle();
  if(!data || (data.member_a!==user.id && data.member_b!==user.id))return null;
  return {user,db,recipient:data.member_a===user.id?data.member_b:data.member_a};
}
async function existing(db:Db,conversation:string,sender:string,clientId:string) {
  const {data}=await db.from("professional_messages").select("id").eq("conversation_id",conversation).eq("sender_id",sender).eq("client_message_id",clientId).maybeSingle();
  return typeof data?.id==="string"?data.id:null;
}
async function uploaded(db:Db,path:string){
  const parts=path.split("/"); const name=parts.pop()!;
  const {data}=await db.storage.from(BUCKET).list(parts.join("/"),{search:name,limit:10});
  return Boolean(data?.some(f=>f.name===name));
}
function schedulePush(recipient:string,conversation:string,message:string){
  after(async()=>{try{await notifyMessageOnce(recipient,conversation,message);}catch{console.error("Message push failed");}});
}
export async function prepareMessageUpload(conversation:string,clientId:string,file:AttachmentMeta):Promise<{ok:true;url:string|null;alreadySent?:string}|{ok:false;error:string}>{
  if(!validMessageId(clientId)||!validAttachment(file))return {ok:false,error:"Choose a supported file of 5 MB or less."};
  const ctx=await context(conversation);if(!ctx)return {ok:false,error:"This conversation is unavailable."};
  const id=await existing(ctx.db,conversation,ctx.user.id,clientId);
  if(id)return {ok:true,url:null,alreadySent:id};
  const path=messageFilePath(ctx.user.id,conversation,clientId,file.mime);
  if(await uploaded(ctx.db,path))return {ok:true,url:null};
  const {data,error}=await ctx.db.storage.from(BUCKET).createSignedUploadUrl(path,{upsert:false});
  if(error||!data?.signedUrl)return {ok:false,error:"Could not prepare the private upload. Please retry."};
  return {ok:true,url:data.signedUrl};
}
export async function sendMessageV2(input:SendInput):Promise<Result>{
  if(!input || !validMessageId(input.clientId)||typeof input.body!=="string"||input.body.trim().length>MESSAGE_LIMIT||
    (input.replyTo!=null&&!validMessageId(input.replyTo))||(input.attachment!=null&&!validAttachment(input.attachment))||
    (!input.body.trim()&&!input.attachment))return {ok:false,error:"Write a message or choose a valid attachment."};
  const ctx=await context(input.conversation);if(!ctx)return {ok:false,error:"This conversation is unavailable."};
  const prior=await existing(ctx.db,input.conversation,ctx.user.id,input.clientId);if(prior)return {ok:true,id:prior};
  const file=input.attachment??null;
  const path=file?messageFilePath(ctx.user.id,input.conversation,input.clientId,file.mime):null;
  if(file&&path){
    const {data,error}=await ctx.db.storage.from(BUCKET).download(path);
    if(error||!data)return {ok:false,error:"The file has not arrived yet. Retry the upload."};
    if(data.size!==file.size||!hasFileSignature(new Uint8Array(await data.slice(0,32).arrayBuffer()),file.mime)){
      await ctx.db.storage.from(BUCKET).remove([path]);
      return {ok:false,error:"The attachment does not match its declared format."};
    }
  }
  const {data,error}=await ctx.db.rpc("sera_send_message_v2",{
    p_conversation:input.conversation,p_client_id:input.clientId,p_body:input.body.trim(),p_reply_to:input.replyTo??null,
    p_forward_source:null,p_path:path,p_name:file?.name??null,p_mime:file?.mime??null,p_size:file?.size??null,p_duration_ms:file?.durationMs??null,
  });
  if(error||!validMessageId(data))return {ok:false,error:"Message could not be sent. Check the connection and retry."};
  schedulePush(ctx.recipient,input.conversation,data);revalidatePath("/network/messages");return {ok:true,id:data};
}
export async function setMessageReaction(message:string,emoji:string|null){
  const user=await currentUser();
  if(!user?.email_confirmed_at||!validMessageId(message)||(emoji!==null&&!(REACTIONS as readonly string[]).includes(emoji)))return {ok:false,error:"Invalid reaction."};
  const db=await serverClient();const {error}=await db.rpc("sera_set_message_reaction",{p_message:message,p_emoji:emoji});
  if(error)return {ok:false,error:"Could not update reaction."};
  revalidatePath("/network/messages");return {ok:true,error:""};
}
export async function unsendMessage(message:string){
  const user=await currentUser();if(!user?.email_confirmed_at||!validMessageId(message))return {ok:false,error:"Invalid message."};
  const db=await serverClient();const {data,error}=await db.rpc("sera_unsend_message",{p_message:message});
  if(error)return {ok:false,error:"Only the sender can unsend this message."};
  const paths=Array.isArray(data)?data.filter((p):p is string=>typeof p==="string"&&p.startsWith(user.id+"/")):[];
  let warning="";
  if(paths.length){const removed=await db.storage.from(BUCKET).remove(paths);if(removed.error)warning="Message removed. Previously issued file links expire shortly.";}
  revalidatePath("/network/messages");return {ok:true,error:"",warning};
}
export async function forwardMessage(source:string,destination:string,clientId:string):Promise<Result>{
  if(!validMessageId(source)||!validMessageId(clientId))return {ok:false,error:"Invalid message."};
  const ctx=await context(destination);if(!ctx)return {ok:false,error:"Destination conversation unavailable."};
  const prior=await existing(ctx.db,destination,ctx.user.id,clientId);if(prior)return {ok:true,id:prior};
  const {data:original}=await ctx.db.from("professional_messages").select("id,unsent_at").eq("id",source).maybeSingle();
  if(!original||original.unsent_at)return {ok:false,error:"This message is no longer available."};
  const {data:files,error:filesError}=await ctx.db.from("professional_message_attachments").select("storage_path,file_name,mime_type,byte_size,duration_ms").eq("message_id",source);
  if(filesError)return {ok:false,error:"Could not read the source attachment."};
  if((files??[]).length>1)return {ok:false,error:"Forward one attachment at a time."};
  const f=files?.[0];
  let file:AttachmentMeta|null=null,path:string|null=null;
  if(f){
    if(!isChatMime(f.mime_type))return {ok:false,error:"Unsupported attachment."};
    file={name:f.file_name,mime:f.mime_type,size:f.byte_size,durationMs:f.duration_ms};
    if(!validAttachment(file))return {ok:false,error:"Invalid attachment."};
    path=messageFilePath(ctx.user.id,destination,clientId,file.mime);
    if(!await uploaded(ctx.db,path)){
      const {data,error}=await ctx.db.storage.from(BUCKET).download(f.storage_path);
      if(error||!data||data.size!==file.size)return {ok:false,error:"Could not copy the private attachment."};
      const transfer=await ctx.db.storage.from(BUCKET).upload(path,data,{contentType:file.mime,upsert:false});
      if(transfer.error&&!await uploaded(ctx.db,path))return {ok:false,error:"Could not forward the attachment. Retry."};
    }
  }
  const {data,error}=await ctx.db.rpc("sera_send_message_v2",{
    p_conversation:destination,p_client_id:clientId,p_body:"",p_reply_to:null,p_forward_source:source,
    p_path:path,p_name:file?.name??null,p_mime:file?.mime??null,p_size:file?.size??null,p_duration_ms:file?.durationMs??null,
  });
  if(error||!validMessageId(data))return {ok:false,error:"Could not forward this message."};
  schedulePush(ctx.recipient,destination,data);revalidatePath("/network/messages");return {ok:true,id:data};
}
export async function recordMessageReceipts(conversation:string,ids:string[],read:boolean){
  if(!Array.isArray(ids)||ids.length===0||ids.length>100||!ids.every(validMessageId)||typeof read!=="boolean")return false;
  const ctx=await context(conversation);if(!ctx)return false;
  const {error}=await ctx.db.rpc("sera_message_receipt",{p_conversation:conversation,p_ids:[...new Set(ids)],p_read:read});
  return !error;
}
export async function loadEarlierMessages(conversation:string,search:string,before:{id:string;createdAt:string}){
  const ctx=await context(conversation);
  if(!ctx||!before||!validMessageId(before.id)||typeof before.createdAt!=="string"||!/^[0-9T:+.Z-]{10,40}$/.test(before.createdAt)||!Number.isFinite(Date.parse(before.createdAt)))return {messages:[],hasMore:false,error:"History unavailable."};
  return loadMessagePage(ctx.db,conversation,typeof search==="string"?search.trim().slice(0,120):"",before);
}
export async function refreshOlderMessages(conversation:string,ids:string[]){
  const ctx=await context(conversation);
  if(!ctx||!Array.isArray(ids)||ids.length>600||!ids.every(validMessageId))return null;
  try{return await refreshMessageViews(ctx.db,conversation,ids);}catch{return null;}
}

/** Commit all uploaded photos as one ordered message after checking their actual signatures. */
export async function sendPhotoAlbum(input:{conversation:string;clientId:string;body:string;replyTo:string|null;photos:{id:string;meta:AttachmentMeta}[]}):Promise<Result>{
  if(!input||!validMessageId(input.clientId)||typeof input.body!=="string"||input.body.trim().length>MESSAGE_LIMIT||(input.replyTo!==null&&!validMessageId(input.replyTo))||!Array.isArray(input.photos)||input.photos.length<2||input.photos.length>4)return {ok:false,error:"Choose two to four photos."};
  if(!input.photos.every(p=>p&&validMessageId(p.id)&&validAttachment(p.meta)&&p.meta.mime.startsWith("image/")&&p.meta.durationMs==null)||new Set(input.photos.map(p=>p.id)).size!==input.photos.length)return {ok:false,error:"Invalid photo selection."};
  const ctx=await context(input.conversation);if(!ctx)return {ok:false,error:"This conversation is unavailable."};
  const prior=await existing(ctx.db,input.conversation,ctx.user.id,input.clientId);if(prior)return {ok:true,id:prior};
  const files=[];
  for(const {id,meta} of input.photos){
    const path=messageFilePath(ctx.user.id,input.conversation,id,meta.mime);
    const {data,error}=await ctx.db.storage.from(BUCKET).download(path);
    if(error||!data||data.size!==meta.size||!hasFileSignature(new Uint8Array(await data.slice(0,32).arrayBuffer()),meta.mime))return {ok:false,error:"One photo is unavailable or invalid. Retry the album."};
    files.push({path,name:meta.name,mime:meta.mime,size:meta.size});
  }
  const {data,error}=await ctx.db.rpc("sera_send_photo_album",{p_conversation:input.conversation,p_client_id:input.clientId,p_body:input.body.trim(),p_reply_to:input.replyTo,p_files:files});
  if(error||!validMessageId(data))return {ok:false,error:"Album could not be sent. Retry."};
  schedulePush(ctx.recipient,input.conversation,data);revalidatePath("/network/messages");return {ok:true,id:data};
}
