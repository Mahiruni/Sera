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
