import {createClient} from "@supabase/supabase-js";
import {after} from "next/server";
import {notifyMessageOnce} from "@/lib/network/push-delivery";
import {validMessageId} from "@/lib/network/message-contract";
import {parseMessageAction} from "@/lib/network/message-action-model";
export const runtime="nodejs";

export async function POST(request:Request){
 const bearer=request.headers.get("authorization")??"";
 const url=process.env.NEXT_PUBLIC_SUPABASE_URL,key=process.env.NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY;
 if(!url||!key)return new Response(null,{status:503});
 if(!/^Bearer [A-Za-z0-9._~-]+$/.test(bearer))return new Response(null,{status:401});
 const input=parseMessageAction(await request.json().catch(()=>null));
 if(!input)return new Response(null,{status:400});
 const db=createClient(url,key,{global:{headers:{Authorization:bearer}},auth:{persistSession:false,autoRefreshToken:false}});
 const {data:{user}}=await db.auth.getUser(bearer.slice(7));
 if(!user?.email_confirmed_at||user.id!==input.owner)return new Response(null,{status:401});
 const {data:thread}=await db.from("professional_conversations").select("member_a,member_b").eq("id",input.conversation).maybeSingle();
 if(!thread||(thread.member_a!==user.id&&thread.member_b!==user.id))return new Response(null,{status:403});
 const peer=thread.member_a===user.id?thread.member_b:thread.member_a;
 const {data:target}=await db.from("professional_messages").select("id,sender_id,unsent_at").eq("id",input.message).eq("conversation_id",input.conversation).eq("sender_id",peer).maybeSingle();
 if(!target||target.unsent_at)return new Response(null,{status:404});
 if(input.action==="read"){
   const {error}=await db.rpc("sera_message_receipt",{p_conversation:input.conversation,p_ids:[input.message],p_read:true});
   return new Response(null,{status:error?503:204});
 }
 const {data,error}=await db.rpc("sera_send_message_v2",{p_conversation:input.conversation,p_client_id:input.clientId,p_body:input.body,p_reply_to:input.message});
 if(error||!validMessageId(data))return new Response(null,{status:503});
 after(async()=>{try{await notifyMessageOnce(peer,input.conversation,data);}catch{console.error("Notification reply push failed");}});
 return Response.json({id:data},{status:200});
}
