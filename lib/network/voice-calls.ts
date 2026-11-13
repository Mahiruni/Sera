"use server";
import { callRelayConfig } from "@/lib/network/call-relay";
import { after } from "next/server";
import { sendIncomingCallPush, dismissIncomingCallPush } from "@/lib/network/call-push";
import { memberCard } from "@/lib/activity/members";
import { currentUser, serverClient } from "@/lib/supabase/server";
import { validMessageId } from "@/lib/network/message-contract";
export type CallState="ringing"|"accepted"|"declined"|"cancelled"|"ended"|"missed"|"failed";
export type CallSession={id:string;caller:string;callee:string;state:CallState;offer:string|null;answer:string|null;created_at:string;expires_at?:string;accepted_at:string|null;ended_at:string|null;caller_restart_ready?:boolean;callee_restart_ready?:boolean;restart_requested?:boolean;restart_generation?:number;restart_offer?:string|null;restart_answer?:string|null};
export type CallHistoryEntry=Pick<CallSession,"id"|"caller"|"callee"|"state"|"created_at"|"accepted_at"|"ended_at">&{connected_seconds?:number|null};
export type CallConfig={enabled:boolean;iceServers?:RTCIceServer[]};
async function dbFor(conversation:string){
  if(!validMessageId(conversation))throw new Error("Invalid conversation.");
  const user=await currentUser();if(!user?.email_confirmed_at)throw new Error("Sign in to call.");
  return serverClient();
}
export async function getCallConfig(conversation:string,issue=false):Promise<CallConfig>{
  try{const db=await dbFor(conversation);return await callRelayConfig(db,conversation,issue===true);}catch{return {enabled:false};}
}
export async function getCallStatus(conversation:string,id:string|null):Promise<CallSession|null>{
  const db=await dbFor(conversation);if(id&&!validMessageId(id))throw new Error("Invalid call.");
  const {data,error}=await db.rpc("sera_call_status",{p_conversation:conversation,p_id:id});if(error)throw new Error("Call status unavailable.");return data;
}
export async function startCall(conversation:string,id:string,offer:string):Promise<CallSession>{
  const db=await dbFor(conversation);if(!validMessageId(id)||typeof offer!=="string"||offer.length>65536)throw new Error("Invalid call.");
  const {data,error}=await db.rpc("sera_call_start",{p_conversation:conversation,p_id:id,p_offer:offer});if(error||!data)throw new Error("Cannot start call. The person may be busy or unavailable.");
  after(async()=>{try{await sendIncomingCallPush(db,id);}catch{console.error("Call push failed");}});return data;
}
export async function changeCall(conversation:string,id:string,action:"accept"|"decline"|"end"|"fail",answer?:string):Promise<CallSession>{
  const db=await dbFor(conversation);if(!validMessageId(id)||!["accept","decline","end","fail"].includes(action)||(answer!=null&&(typeof answer!=="string"||answer.length>65536)))throw new Error("Invalid call.");
  const {data,error}=await db.rpc("sera_call_transition",{p_conversation:conversation,p_id:id,p_action:action,p_answer:answer??null});if(error||!data)throw new Error("Call could not be updated.");
  after(async()=>{try{await dismissIncomingCallPush(db,id);}catch{console.error("Call dismissal push failed");}});return data;
}
export async function getCallHistory(conversation:string):Promise<CallHistoryEntry[]>{
  const db=await dbFor(conversation);const {data,error}=await db.rpc("sera_call_history",{p_conversation:conversation});return !error&&Array.isArray(data)?data:[];
}

export async function confirmCallMedia(conversation:string,id:string):Promise<boolean>{
 const db=await dbFor(conversation);if(!validMessageId(id))return false;
 const {data,error}=await db.rpc("sera_call_confirm_media",{p_conversation:conversation,p_id:id});return !error&&data===true;
}

export async function restartCallMedia(conversation:string,id:string,action:"ready"|"request"|"offer"|"answer",generation=0,sdp?:string):Promise<CallSession|null>{
 const db=await dbFor(conversation);
 if(!validMessageId(id)||!["ready","request","offer","answer"].includes(action)||!Number.isInteger(generation)||generation<0||generation>3||(sdp!=null&&(typeof sdp!=="string"||sdp.length>65536)))return null;
 const {data,error}=await db.rpc("sera_call_restart",{p_conversation:conversation,p_id:id,p_action:action,p_generation:generation,p_sdp:sdp??null});
 if(error)throw Error("Call recovery signaling unavailable.");return data;
}

export type PendingIncomingCall={id:string;conversation:string;name:string;photo:string|null;expiresAt:string};
export async function getPendingIncomingCall():Promise<PendingIncomingCall|null>{
 const user=await currentUser();if(!user?.email_confirmed_at)return null;
 const db=await serverClient();
 const {data,error}=await db.rpc("sera_call_pending_incoming");
 if(error||!data||!validMessageId(data.id)||!validMessageId(data.conversation_id)||!validMessageId(data.caller)||!Number.isFinite(Date.parse(data.expires_at))||Date.parse(data.expires_at)<=Date.now())return null;
 const card=await memberCard(data.caller);
 return {id:data.id,conversation:data.conversation_id,name:card?.name||"Sera member",photo:card?.photo??null,expiresAt:data.expires_at};
}
