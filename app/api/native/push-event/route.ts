import { createClient } from "@supabase/supabase-js";
import { sendIncomingCallPush } from "@/lib/network/call-push";
import { after } from "next/server";
import { notifyProfessionalDevices } from "@/lib/network/push-delivery";
export const runtime = "nodejs";
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
export async function POST(request: Request) {
 const bearer=request.headers.get("authorization")||"";
 const url=process.env.NEXT_PUBLIC_SUPABASE_URL, key=process.env.NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY, service=process.env.SERA_SUPABASE_SERVICE_ROLE_KEY;
 if(!url||!key||!service||!process.env.FIREBASE_SERVICE_ACCOUNT_JSON)return Response.json({error:"Push unavailable"},{status:503});
 if(!/^Bearer [A-Za-z0-9._~-]+$/.test(bearer))return new Response(null,{status:401});
 const db=createClient(url,key,{global:{headers:{Authorization:bearer}},auth:{persistSession:false,autoRefreshToken:false}});
 const {data:{user}}=await db.auth.getUser(bearer.slice(7));
 if(!user?.email_confirmed_at)return new Response(null,{status:401});
 const body=await request.json().catch(()=>null);
 if(!body||typeof body.id!=="string"||!uuid.test(body.id)||!["message","company_message","voice_call"].includes(body.kind))return new Response(null,{status:400});
 if(body.kind==="voice_call"){await sendIncomingCallPush(db,body.id);return new Response(null,{status:202});}
 const company=body.kind==="company_message";
 const {data:message}=await db.from(company?"company_job_messages":"professional_messages").select(company?"id,sender_id,thread_id,created_at":"id,sender_id,conversation_id,created_at").eq("id",body.id).eq("sender_id",user.id).maybeSingle();
 if(!message||Date.now()-new Date(message.created_at).getTime()>10*60*1000)return new Response(null,{status:404});
 const row=message as unknown as Record<string,string>;
 const {data:thread}=await db.from(company?"company_job_threads":"professional_conversations").select(company?"candidate_id,employer_id":"member_a,member_b").eq("id",row[company?"thread_id":"conversation_id"]).maybeSingle();
 if(!thread)return new Response(null,{status:404});
 const t=thread as unknown as Record<string,string>;
 const first=t[company?"candidate_id":"member_a"],second=t[company?"employer_id":"member_b"];
 const recipient=first===user.id?second:second===user.id?first:null;
 if(!recipient)return new Response(null,{status:403});
 const admin=createClient(url,service,{auth:{persistSession:false,autoRefreshToken:false}});
 const claim=await admin.from("sera_native_push_events").insert({event_id:body.id,kind:body.kind});
 if(claim.error)return new Response(null,{status:claim.error.code==="23505"?204:503});
 after(async()=>{try{await notifyProfessionalDevices(recipient,{title:company?"New company message":"New SERA message",body:"You have a new private message.",url:"/network/messages",kind:body.kind});}catch{console.error("Native push delivery failed");}});
 return new Response(null,{status:202});
}
