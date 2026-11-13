import {createClient} from "@supabase/supabase-js";
import {validMessageId} from "@/lib/network/message-contract";
import {attachmentPreview,cleanPreviewText} from "@/lib/network/message-preview-model";
export const runtime="nodejs";
const headers={"Cache-Control":"private, no-store"};
const empty=(status:number)=>new Response(null,{status,headers});
export async function GET(request:Request){
 const bearer=request.headers.get("authorization")??"";
 const url=process.env.NEXT_PUBLIC_SUPABASE_URL,key=process.env.NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY;
 if(!url||!key)return empty(503);
 if(!/^Bearer [A-Za-z0-9._~-]+$/.test(bearer))return empty(401);
 const params=new URL(request.url).searchParams;
 const owner=params.get("owner"),conversation=params.get("conversation"),id=params.get("message");
 if(!validMessageId(owner)||!validMessageId(conversation)||!validMessageId(id))return empty(400);
 const db=createClient(url,key,{global:{headers:{Authorization:bearer}},auth:{persistSession:false,autoRefreshToken:false}});
 const {data:{user}}=await db.auth.getUser(bearer.slice(7));
 if(!user?.email_confirmed_at||user.id!==owner)return empty(401);
 const {data:pref,error}=await db.from("sera_message_preferences").select("notification_previews,notifications_enabled").eq("user_id",owner).maybeSingle();
 if(error||!pref?.notification_previews||!pref.notifications_enabled)return empty(204);
 const {data:thread}=await db.from("professional_conversations").select("member_a,member_b").eq("id",conversation).maybeSingle();
 if(!thread||(thread.member_a!==owner&&thread.member_b!==owner))return empty(404);
 const peer=thread.member_a===owner?thread.member_b:thread.member_a;
 const {data:message}=await db.from("professional_messages").select("body,read_at,unsent_at").eq("id",id).eq("conversation_id",conversation).eq("sender_id",peer).maybeSingle();
 if(!message||message.unsent_at||message.read_at)return empty(404);
 const {data:privateRead,error:privateError}=await db.from("sera_private_message_reads").select("message_id").eq("message_id",id).maybeSingle();
 if(privateError||privateRead)return empty(204);
 const {data:cards}=await db.rpc("member_cards",{ids:[peer]});
 const title=cleanPreviewText(cards?.[0]?.full_name,80)||"SERA message";
 let body=cleanPreviewText(message.body);
 if(!body){const {data:files}=await db.from("professional_message_attachments").select("mime_type").eq("message_id",id).limit(1);body=attachmentPreview(files?.[0]?.mime_type);}
 return Response.json({title,body},{headers});
}
