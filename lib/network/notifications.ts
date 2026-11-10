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
