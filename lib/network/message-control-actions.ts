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
