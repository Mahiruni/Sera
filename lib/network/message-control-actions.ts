"use server";
import {revalidatePath} from "next/cache";
import {currentUser,serverClient} from "@/lib/supabase/server";
import {validMessageId} from "@/lib/network/message-contract";
import {parseClock} from "@/lib/network/message-control-model";
export async function saveMessagingPreferences(form:FormData){
 const user=await currentUser();if(!user?.email_confirmed_at)return;
 const start=parseClock(form.get("start")),end=parseClock(form.get("end")),zone=form.get("zone");
 if(start===null||end===null||start===end||typeof zone!=="string"||zone.length>80)throw new Error("Choose valid quiet hours and timezone.");
 try{new Intl.DateTimeFormat("en",{timeZone:zone}).format();}catch{throw new Error("Invalid timezone");}
 const db=await serverClient();const {error}=await db.rpc("sera_save_message_preferences",{p_enabled:form.get("enabled")==="on",p_quiet:form.get("quiet")==="on",p_start:start,p_end:end,p_timezone:zone});
 if(error)throw new Error("Could not save messaging preferences.");
 revalidatePath("/settings/messaging");
}
export async function setConversationControl(conversation:string,action:"pin"|"archive"|"unread"|"mute",value:boolean,minutes=0){
 const user=await currentUser();if(!user?.email_confirmed_at||!validMessageId(conversation)||!["pin","archive","unread","mute"].includes(action)||typeof value!=="boolean"||![0,60,480,1440,-1].includes(minutes))return {ok:false,error:"Invalid conversation action."};
 const db=await serverClient();const {error}=await db.rpc("sera_set_conversation_preference",{p_conversation:conversation,p_action:action,p_value:value,p_mute_minutes:minutes});
 if(error)return {ok:false,error:"Could not save conversation preference."};
 revalidatePath("/network/messages");return {ok:true,error:""};
}

export async function saveTypingVisibility(form:FormData){
 const user=await currentUser();if(!user?.email_confirmed_at)return;
 const db=await serverClient();const {error}=await db.rpc("sera_set_typing_visibility",{p_visible:form.get("typing")==="on"});
 if(error)throw new Error("Could not save typing privacy.");
 revalidatePath("/settings/messaging");
}

export async function saveMessagingActivity(form:FormData){
 const user=await currentUser();if(!user?.email_confirmed_at)return;
 const db=await serverClient();const {error}=await db.rpc("sera_presence_heartbeat",{p_visibility:form.get("activity")==="on"?"connections":"hidden"});
 if(error)throw new Error("Could not save activity privacy.");
 revalidatePath("/settings/messaging");
}

export async function saveReadReceiptVisibility(form:FormData){
 const user=await currentUser();if(!user?.email_confirmed_at)return;
 const db=await serverClient();const {error}=await db.rpc("sera_set_read_receipt_visibility",{p_visible:form.get("receipts")==="on"});
 if(error)throw new Error("Could not save read receipt privacy.");
 revalidatePath("/settings/messaging");
}

export async function saveNotificationPreviews(form:FormData){
 const user=await currentUser();if(!user?.email_confirmed_at)return;
 const mode=form.get("previewMode");if(mode!=="full"&&mode!=="sender"&&mode!=="hidden")throw new Error("Choose a notification privacy mode.");
 const db=await serverClient();const {error}=await db.rpc("sera_set_notification_preview_mode",{p_mode:mode});
 if(error)throw new Error("Could not save notification previews.");
 revalidatePath("/settings/messaging");
}

/** Chat safety operations use existing RLS and the authenticated member identity. */
export async function conversationSafety(conversation:string,action:"block"|"report",reason="spam",details=""){
 const user=await currentUser();if(!user?.email_confirmed_at||!validMessageId(conversation)||!["block","report"].includes(action))return {ok:false,error:"Invalid safety request."};
 if(!["spam","harassment","impersonation","inappropriate","other"].includes(reason)||typeof details!=="string"||details.length>1000)return {ok:false,error:"Invalid report."};
 const db=await serverClient();
 const {data:thread,error:accessError}=await db.from("professional_conversations").select("member_a,member_b").eq("id",conversation).or("member_a.eq."+user.id+",member_b.eq."+user.id).maybeSingle();
 if(accessError||!thread)return {ok:false,error:"Conversation is unavailable."};
 const target=thread.member_a===user.id?thread.member_b:thread.member_a;
 const {error}=action==="block"?await db.from("professional_blocks").upsert({blocker_id:user.id,blocked_id:target},{onConflict:"blocker_id,blocked_id",ignoreDuplicates:true}):await db.from("professional_reports").insert({reporter_id:user.id,reported_id:target,reason,details:details.trim()});
 if(error)return {ok:false,error:"Could not complete the safety request."};
 revalidatePath("/network/messages");revalidatePath("/connections");return {ok:true,error:""};
}
