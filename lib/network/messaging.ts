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
  const attachment = form.get("attachment");
  const file = attachment instanceof File && attachment.size > 0 ? attachment : null;
  const allowed = new Set(["image/jpeg","image/png","image/webp","application/pdf"]);
  if (file && (file.size > 5 * 1024 * 1024 || !allowed.has(file.type))) return;
  if (!user?.email_confirmed_at || !uuid(conversation) || typeof body !== "string" ||
      (!body.trim() && !file) || body.trim().length > 4000) return;
  const db = await serverClient();
  const { data: message, error } = await db.from("professional_messages").insert({
    conversation_id: conversation, sender_id: user.id, body: body.trim() || "Attachment",
  }).select("id").single();
  if (error || !message) return;
  if (file) {
    const ext = file.type === "application/pdf" ? "pdf" : file.type.split("/")[1];
    const path = user.id + "/" + conversation + "/" + crypto.randomUUID() + "." + ext;
    const bucket = db.storage.from("professional-message-files");
    const upload = await bucket.upload(path, file, { contentType: file.type, upsert: false });
    if (!upload.error) {
      const recorded = await db.from("professional_message_attachments").insert({
        message_id: message.id, owner_id: user.id, storage_path: path,
        file_name: file.name.slice(0, 180) || "attachment", mime_type: file.type, byte_size: file.size,
      });
      if (recorded.error) await bucket.remove([path]);
    }
  }
  revalidatePath("/network/messages");
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
