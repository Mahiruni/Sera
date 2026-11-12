"use server";
import { revalidatePath } from "next/cache";
import { currentUser, serverClient } from "@/lib/supabase/server";
export async function markNotificationRead(form:FormData) {
 const user=await currentUser();
 const id=form.get("id");
 if(!user?.email_confirmed_at || typeof id!=="string" || !/^[0-9a-f-]{36}$/i.test(id)) return;
 const db=await serverClient();
 const {error}=await db.from("professional_notifications").update({read_at:new Date().toISOString()}).eq("id",id).eq("recipient_id",user.id);
 if(!error) revalidatePath("/notifications");
}

export async function markAllNotificationsRead(){
 const user=await currentUser();
 if(!user?.email_confirmed_at) return;
 const db=await serverClient();
 const {error}=await db.from("professional_notifications")
  .update({read_at:new Date().toISOString()})
  .eq("recipient_id",user.id).is("read_at",null);
 if(!error) revalidatePath("/notifications");
}

/** Shared unread count for the global bell and the canonical inbox. */
export async function professionalUnreadCount(): Promise<number> {
 const user = await currentUser();
 if (!user?.email_confirmed_at) return 0;
 const db = await serverClient();
 const { count, error } = await db.from("professional_notifications")
  .select("id", { count: "exact", head: true })
  .eq("recipient_id", user.id)
  .is("read_at", null);
 if (error) throw new Error("Notifications are temporarily unavailable.");
 return count ?? 0;
}

export async function professionalInboxUnreadCount():Promise<number>{const user=await currentUser();if(!user?.email_confirmed_at)return 0;const db=await serverClient();const {data,error}=await db.rpc("sera_unread_activity");if(error)throw new Error("Inbox unavailable");return Number(data?.inbox||0);}

/** Explicitly refresh the member's own job matches; database enforces relevance and caps. */
export async function refreshJobAlerts(){
 const user=await currentUser();if(!user?.email_confirmed_at)return;
 const db=await serverClient();const {error}=await db.rpc("sera_refresh_job_alerts");
 if(error)throw new Error("Job alerts are temporarily unavailable. Please try again.");
 revalidatePath("/notifications");
}

export async function setAutomaticJobAlerts(form:FormData){
 const user=await currentUser();if(!user?.email_confirmed_at)return;
 const value=form.get("enabled");if(value!=="true"&&value!=="false")return;
 const db=await serverClient();const {error}=await db.from("sera_job_alert_preferences").upsert({user_id:user.id,enabled:value==="true",updated_at:new Date().toISOString()},{onConflict:"user_id"});
 if(error)throw new Error("Unable to save career alert preferences.");
 revalidatePath("/notifications");
}
