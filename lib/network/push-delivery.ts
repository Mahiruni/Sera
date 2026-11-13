import "server-only";
import { createClient } from "@supabase/supabase-js";
import { enqueueAndDispatchMessage } from "@/lib/network/message-push-recovery";
import { sendProfessionalPush } from "@/lib/network/firebase-push";

type Notification = { title: string; body: string; url: string; kind: string; callId?:string; recipientId?:string; expiresAt?:string; messageId?:string; conversationId?:string };
/** Server-only delivery; the service role is never exposed to browsers or authenticated users. */
export async function notifyProfessionalDevices(recipientId: string, notification: Notification) {
  const url = process.env.NEXT_PUBLIC_SUPABASE_URL;
  const key = process.env.SERA_SUPABASE_SERVICE_ROLE_KEY;
  if (!url || !key || !process.env.FIREBASE_SERVICE_ACCOUNT_JSON) return;
  const admin = createClient(url, key, { auth: { persistSession: false, autoRefreshToken: false } });
  const { data, error } = await admin.from("professional_push_devices")
    .select("token,platform").eq("user_id", recipientId).eq("enabled", true).limit(100);
  if (error || !data?.length) return;
  const android = data.filter(row => row.platform === "android").map(row => row.token as string);
  const web = data.filter(row => notification.kind !== "voice_call_dismiss" && row.platform !== "android").map(row => row.token as string);
  const results = await Promise.all([android.length ? sendProfessionalPush(android,notification,true) : null, web.length ? sendProfessionalPush(web,notification) : null]);
  const invalidTokens = results.flatMap(result => result?.invalidTokens ?? []);
  if (invalidTokens.length) {
    await admin.from("professional_push_devices").update({ enabled: false, updated_at: new Date().toISOString() })
      .eq("user_id", recipientId).in("token", invalidTokens);
  }
}

/** Shared native/web dispatch; queue claim and device jobs are committed atomically. */
export async function notifyMessageOnce(recipient:string, conversation:string, message:string, kind:"message"|"company_message"="message") {
  await enqueueAndDispatchMessage(recipient,conversation,message,kind);
}
