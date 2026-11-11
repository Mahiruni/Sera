"use server";
import { revalidatePath } from "next/cache";
import { currentUser, serverClient } from "@/lib/supabase/server";
import { notifyProfessionalDevices } from "@/lib/network/push-delivery";
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
  const { data: membership, error: membershipError } = await db.from("professional_conversations").select("id,member_a,member_b").eq("id", conversation).or("member_a.eq." + user.id + ",member_b.eq." + user.id).maybeSingle();
  if (membershipError || !membership) return;
  // Upload first: a rejected or oversized file must not create an empty message.
  let path: string | null = null;
  const bucket = db.storage.from("professional-message-files");
  if (file) {
    const ext = file.type === "application/pdf" ? "pdf" : file.type.split("/")[1];
    path = user.id + "/" + conversation + "/" + crypto.randomUUID() + "." + ext;
    const uploaded = await bucket.upload(path, file, { contentType: file.type, upsert: false });
    if (uploaded.error) return;
  }
  const { data: message, error } = await db.from("professional_messages").insert({
    conversation_id: conversation, sender_id: user.id, body: body.trim() || "Attachment",
  }).select("id").single();
  if (error || !message) {
    if (path) await bucket.remove([path]);
    return;
  }
  if (file && path) {
    const recorded = await db.from("professional_message_attachments").insert({
      message_id: message.id, owner_id: user.id, storage_path: path,
      file_name: file.name.slice(0, 180) || "attachment", mime_type: file.type, byte_size: file.size,
    });
    if (recorded.error) await bucket.remove([path]);
  }
  // Push is best-effort: a Firebase outage must never undo a persisted message.
  const recipient = membership.member_a === user.id ? membership.member_b : membership.member_a;
  try {
    await notifyProfessionalDevices(recipient, {
      title: "New SERA message",
      body: "You have a new professional message.",
      url: "/network/messages",
      kind: "message",
    });
  } catch (error) {
    console.error("Professional message push delivery failed", error instanceof Error ? error.message : "unknown error");
  }
  revalidatePath("/network/messages");
}
export async function markProfessionalMessagesRead(form: FormData) {
  const user = await currentUser();
  const conversation = form.get("conversation");
  if (!user?.email_confirmed_at || !uuid(conversation)) return;
  const db = await serverClient();
  const { data: membership, error: membershipError } = await db.from("professional_conversations").select("id").eq("id", conversation).or("member_a.eq." + user.id + ",member_b.eq." + user.id).maybeSingle();
  if (membershipError || !membership) return;
  await db.from("professional_messages").update({ read_at: new Date().toISOString() })
    .eq("conversation_id", conversation).neq("sender_id", user.id).is("read_at", null);
  revalidatePath("/network/messages");
}
