import "server-only";
import type { SupabaseClient } from "@supabase/supabase-js";
import { notifyProfessionalDevices } from "@/lib/network/push-delivery";
import { validMessageId } from "@/lib/network/message-contract";
/** Claim once using the caller JWT. Never trust a client-supplied recipient or expiry. */
export async function sendIncomingCallPush(db:SupabaseClient,id:string){
  if(!validMessageId(id))return;
  const {data,error}=await db.rpc("sera_call_claim_push",{p_id:id});
  if(error||!data||!validMessageId(data.recipient)||!validMessageId(data.conversation_id)||!validMessageId(data.id)||typeof data.expires_at!=="string"||Date.parse(data.expires_at)<=Date.now())return;
  await notifyProfessionalDevices(data.recipient,{title:"Incoming SERA call",body:"You have an incoming voice call. Open SERA to answer.",url:"/network/messages?thread="+data.conversation_id,kind:"voice_call",callId:data.id,recipientId:data.recipient,expiresAt:data.expires_at});
}

/** Silent native dismissal after accept, decline or hangup. Database owns recipients and eligibility. */
export async function dismissIncomingCallPush(db:SupabaseClient,id:string){
  if(!validMessageId(id))return;
  const {data,error}=await db.rpc("sera_call_claim_dismissal",{p_id:id});
  if(error||!data||!Array.isArray(data.recipients)||typeof data.expires_at!=="string")return;
  const recipients=(data.recipients as unknown[]).filter((x):x is string=>typeof x==="string"&&validMessageId(x));
  await Promise.all(recipients.map(recipient=>notifyProfessionalDevices(recipient,{title:"SERA.et",body:"Call invitation closed",url:"/network/messages",kind:"voice_call_dismiss",callId:id,recipientId:recipient,expiresAt:data.expires_at})));
}
