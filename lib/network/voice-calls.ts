"use server";
import { currentUser, serverClient } from "@/lib/supabase/server";
import { validMessageId } from "@/lib/network/message-contract";
export type CallState="ringing"|"accepted"|"declined"|"cancelled"|"ended"|"missed"|"failed";
export type CallSession={id:string;caller:string;callee:string;state:CallState;offer:string|null;answer:string|null;created_at:string;accepted_at:string|null;ended_at:string|null};
export type CallConfig={enabled:boolean;iceServers?:RTCIceServer[]};
async function dbFor(conversation:string){
  if(!validMessageId(conversation))throw new Error("Invalid conversation.");
  const user=await currentUser();if(!user?.email_confirmed_at)throw new Error("Sign in to call.");
  return serverClient();
}
export async function getCallConfig(conversation:string):Promise<CallConfig>{
  try{const db=await dbFor(conversation);const {data,error}=await db.rpc("sera_call_config",{p_conversation:conversation});return !error&&data?.enabled===true?data:{enabled:false};}catch{return {enabled:false};}
}
export async function getCallStatus(conversation:string,id:string|null):Promise<CallSession|null>{
  const db=await dbFor(conversation);if(id&&!validMessageId(id))throw new Error("Invalid call.");
  const {data,error}=await db.rpc("sera_call_status",{p_conversation:conversation,p_id:id});if(error)throw new Error("Call status unavailable.");return data;
}
export async function startCall(conversation:string,id:string,offer:string):Promise<CallSession>{
  const db=await dbFor(conversation);if(!validMessageId(id)||typeof offer!=="string"||offer.length>65536)throw new Error("Invalid call.");
  const {data,error}=await db.rpc("sera_call_start",{p_conversation:conversation,p_id:id,p_offer:offer});if(error||!data)throw new Error("Cannot start call. The person may be busy or unavailable.");return data;
}
export async function changeCall(conversation:string,id:string,action:"accept"|"decline"|"end"|"fail",answer?:string):Promise<CallSession>{
  const db=await dbFor(conversation);if(!validMessageId(id)||!["accept","decline","end","fail"].includes(action)||(answer!=null&&(typeof answer!=="string"||answer.length>65536)))throw new Error("Invalid call.");
  const {data,error}=await db.rpc("sera_call_transition",{p_conversation:conversation,p_id:id,p_action:action,p_answer:answer??null});if(error||!data)throw new Error("Call could not be updated.");return data;
}
export async function getCallHistory(conversation:string):Promise<CallSession[]>{
  const db=await dbFor(conversation);const {data,error}=await db.rpc("sera_call_history",{p_conversation:conversation});return !error&&Array.isArray(data)?data:[];
}
