import {createClient} from "@supabase/supabase-js";
import {serverClient} from "@/lib/supabase/server";
import {validMessageId} from "@/lib/network/message-contract";
import {attachmentPreview,cleanPreviewText,notificationPreviewMode} from "@/lib/network/message-preview-model";
import {shouldSuppressMessage} from "@/lib/network/message-control-model";
export const runtime="nodejs";
const headers={"Cache-Control":"private, no-store"};
const empty=(status:number)=>new Response(null,{status,headers});
export async function GET(request:Request){
 const bearer=request.headers.get("authorization")??"";
 const url=process.env.NEXT_PUBLIC_SUPABASE_URL,key=process.env.NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY;
 if(!url||!key)return empty(503);
 if(bearer&&!/^Bearer [A-Za-z0-9._~-]+$/.test(bearer))return empty(401);
 const params=new URL(request.url).searchParams;
 const owner=params.get("owner"),conversation=params.get("conversation"),id=params.get("message");
 if(!validMessageId(owner)||!validMessageId(conversation)||!validMessageId(id))return empty(400);
 const db=bearer?createClient(url,key,{global:{headers:{Authorization:bearer}},auth:{persistSession:false,autoRefreshToken:false}}):await serverClient();
 const {data:{user}}=await db.auth.getUser(bearer?bearer.slice(7):undefined);
 if(!user?.email_confirmed_at||user.id!==owner)return empty(401);
 const {data:pref,error}=await db.from("sera_message_preferences").select("notification_preview_mode,notification_previews,notifications_enabled,quiet_enabled,quiet_start,quiet_end,quiet_timezone").eq("user_id",owner).maybeSingle();
 const {data:control,error:controlError}=await db.from("sera_conversation_preferences").select("muted_until").eq("user_id",owner).eq("conversation_id",conversation).maybeSingle();
 if(error||controlError||shouldSuppressMessage(pref,control?.muted_until??null,new Date()))return empty(204);
 const {data:thread}=await db.from("professional_conversations").select("member_a,member_b").eq("id",conversation).maybeSingle();
 if(!thread||(thread.member_a!==owner&&thread.member_b!==owner))return empty(404);
 const peer=thread.member_a===owner?thread.member_b:thread.member_a;
 const {data:message}=await db.from("professional_messages").select("body,read_at,unsent_at,created_at").eq("id",id).eq("conversation_id",conversation).eq("sender_id",peer).maybeSingle();
 if(!message||message.unsent_at||message.read_at)return empty(404);
 const {data:privateRead,error:privateError}=await db.from("sera_private_message_reads").select("message_id").eq("message_id",id).eq("recipient_id",owner).maybeSingle();
 if(privateError||privateRead)return empty(204);
 const mode=notificationPreviewMode(pref?.notification_preview_mode,pref?.notification_previews);
 if(mode==="hidden")return Response.json({mode,title:"Sera",body:"You have a new private message.",createdAt:message.created_at},{headers});
 const {data:cards,error:cardError}=await db.rpc("member_cards",{ids:[peer]});
 const card=cards?.[0];
 if(cardError||!card||card.id!==peer)return empty(204);
 const title=cleanPreviewText(card.full_name,80)||"Sera member";
 let body="Sent you a message",bodyKind="message";
 if(mode==="full"){
   body=cleanPreviewText(message.body);
   bodyKind="text";
   if(!body){const {data:files}=await db.from("professional_message_attachments").select("mime_type").eq("message_id",id).limit(5);body=files&&files.length>1?"Sent you multiple attachments":attachmentPreview(files?.[0]?.mime_type);bodyKind="attachment";}
 }
 const path=typeof card.avatar_path==="string"?card.avatar_path:null;
 const avatar=path?(await db.storage.from("sera-account-private").createSignedUrl(path,120)).data?.signedUrl??null:null;
 return Response.json({mode,title,body,bodyKind,senderId:peer,avatar,createdAt:message.created_at},{headers});
}
