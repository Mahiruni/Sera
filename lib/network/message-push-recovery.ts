import "server-only";
import {createClient} from "@supabase/supabase-js";
import {sendProfessionalPush} from "@/lib/network/firebase-push";
import {messagePushPayload} from "@/lib/network/message-push-model";
import {shouldSuppressMessage} from "@/lib/network/message-control-model";

type Job={event_id:string;kind:"message"|"company_message";recipient_id:string;conversation_id:string;device_id:string;lease_id:string;expires_at:string};
function adminClient(){
 const url=process.env.NEXT_PUBLIC_SUPABASE_URL,key=process.env.SERA_SUPABASE_SERVICE_ROLE_KEY;
 if(!url||!key||!process.env.FIREBASE_SERVICE_ACCOUNT_JSON)throw new Error("Push recovery unavailable");
 return createClient(url,key,{auth:{persistSession:false,autoRefreshToken:false}});
}
type Db=ReturnType<typeof adminClient>;
async function processJob(db:Db,j:Job):Promise<void>{
 let outcome:"accepted"|"skipped"|"retry"="retry";
 try{
  const [device,prefs,control]=await Promise.all([
   db.from("professional_push_devices").select("token,platform,enabled,user_id").eq("id",j.device_id).maybeSingle(),
   db.from("sera_message_preferences").select("notifications_enabled,quiet_enabled,quiet_start,quiet_end,quiet_timezone").eq("user_id",j.recipient_id).maybeSingle(),
   j.kind==="message"?db.from("sera_conversation_preferences").select("muted_until").eq("user_id",j.recipient_id).eq("conversation_id",j.conversation_id).maybeSingle():Promise.resolve({data:null,error:null})
  ]);
  if(device.error||prefs.error||control.error)throw new Error("Notification policy lookup failed");
  const d=device.data;
  if(!d||!d.enabled||d.user_id!==j.recipient_id||shouldSuppressMessage(prefs.data,control.data?.muted_until??null,new Date())||Date.parse(j.expires_at)<=Date.now())outcome="skipped";
  else{
   // Revalidate membership and message state before every retry.
   const company=j.kind==="company_message";
   const [msg,thread]=await Promise.all([
    db.from(company?"company_job_messages":"professional_messages").select(company?"sender_id,thread_id":"sender_id,conversation_id,read_at,unsent_at").eq("id",j.event_id).maybeSingle(),
    db.from(company?"company_job_threads":"professional_conversations").select(company?"candidate_id,employer_id":"member_a,member_b").eq("id",j.conversation_id).maybeSingle()
   ]);
   if(msg.error||thread.error)throw new Error("Message lookup failed");
   const m=msg.data as unknown as Record<string,string|null>|null;
   const t=thread.data as unknown as Record<string,string>|null;
   const first=t?.[company?"candidate_id":"member_a"],second=t?.[company?"employer_id":"member_b"];
   const peer=first===j.recipient_id?second:second===j.recipient_id?first:null;
   if(!m||!peer||m.sender_id!==peer||m[company?"thread_id":"conversation_id"]!==j.conversation_id||(!company&&(m.read_at||m.unsent_at)))outcome="skipped";
   else{
    const privateRead=company?{data:null,error:null}:await db.from("sera_private_message_reads").select("message_id").eq("message_id",j.event_id).eq("recipient_id",j.recipient_id).maybeSingle();
    if(privateRead.error)throw new Error("Private read lookup failed");
    if(privateRead.data){outcome="skipped";}else{
    const block=await db.from("professional_blocks").select("blocker_id").or("and(blocker_id.eq."+peer+",blocked_id.eq."+j.recipient_id+"),and(blocker_id.eq."+j.recipient_id+",blocked_id.eq."+peer+")").limit(1);
    if(block.error)throw new Error("Block lookup failed");
    if(block.data?.length)outcome="skipped";
    else{
     const payload=messagePushPayload(j.recipient_id,j.conversation_id,j.event_id,j.kind);
     if(!payload)outcome="skipped";
     else{
      const result=await sendProfessionalPush([d.token],{...payload,expiresAt:j.expires_at},d.platform==="android");
      if(result.delivered===1)outcome="accepted";
      else if(result.invalidTokens.includes(d.token)){
       // Guard against a replacement token or account change during delivery.
       const update=await db.from("professional_push_devices").update({enabled:false,updated_at:new Date().toISOString()}).eq("id",j.device_id).eq("user_id",j.recipient_id).eq("token",d.token);
       if(update.error)throw new Error("Invalid token cleanup failed");
       outcome="skipped";
      }
     }
    }
   }
    }
  }
 }catch{ /* Leave the job eligible for bounded retry. Never log private content or tokens. */ }
 const {error}=await db.rpc("sera_finish_message_push",{p_event:j.event_id,p_kind:j.kind,p_device:j.device_id,p_lease:j.lease_id,p_result:outcome});
 if(error)throw new Error("Notification job finalization failed");
}
export async function drainMessagePushJobs(event?:string){
 const db=adminClient();
 const {data,error}=await db.rpc("sera_claim_message_push_jobs",{p_limit:8,p_event:event??null});
 if(error)throw new Error("Notification recovery claim failed");
 const jobs=(data??[]) as Job[];
 const results=await Promise.allSettled(jobs.map(j=>processJob(db,j)));
 return {claimed:jobs.length,failed:results.filter(r=>r.status==="rejected").length};
}
export async function enqueueAndDispatchMessage(recipient:string,conversation:string,message:string,kind:"message"|"company_message"){
 if(!messagePushPayload(recipient,conversation,message,kind))return;
 const db=adminClient();
 const {error}=await db.rpc("sera_enqueue_message_push",{p_event:message,p_kind:kind,p_recipient:recipient,p_conversation:conversation});
 if(error)throw new Error("Notification enqueue failed");
 await drainMessagePushJobs(message);
}
