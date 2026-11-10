"use server";
import { revalidatePath } from "next/cache";
import { currentUser, serverClient } from "@/lib/supabase/server";
const uuid = (v: unknown): v is string => typeof v === "string" && /^[a-f0-9]{8}-[a-f0-9]{4}-[1-8][a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$/i.test(v);
export async function startConversation(form: FormData) {
  const user = await currentUser();
  const other = form.get("recipient");
  if (!user?.email_confirmed_at || !uuid(other) || other === user.id) return;
  const db = await serverClient();
  const [member_a, member_b] = [user.id, other].sort();
  const { error } = await db.from("professional_conversations").insert({ member_a, member_b });
  // A duplicate conversation is an expected, harmless outcome. Never require UPDATE privileges.
  if (error?.code === "23505") { revalidatePath("/network/messages"); return; }
  if (error) return;
  revalidatePath("/network/messages");
}
export async function sendProfessionalMessage(form: FormData) {
  const user = await currentUser();
  const conversation = form.get("conversation");
  const body = form.get("body");
  if (!user?.email_confirmed_at || !uuid(conversation) || typeof body !== "string" ||
      !body.trim() || body.trim().length > 4000) return;
  const db = await serverClient();
  const { error } = await db.from("professional_messages").insert({
    conversation_id: conversation, sender_id: user.id, body: body.trim(),
  });
  if (!error) revalidatePath("/network/messages");
}
export async function markProfessionalMessagesRead(form: FormData) {
  const user = await currentUser();
  const conversation = form.get("conversation");
  if (!user?.email_confirmed_at || !uuid(conversation)) return;
  const db = await serverClient();
  await db.from("professional_messages").update({ read_at: new Date().toISOString() })
    .eq("conversation_id", conversation).neq("sender_id", user.id).is("read_at", null);
  revalidatePath("/network/messages");
}
