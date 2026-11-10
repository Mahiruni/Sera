"use server";
import { revalidatePath } from "next/cache";
import { currentUser, serverClient } from "@/lib/supabase/server";

const validId = (value: unknown): value is string =>
  typeof value === "string" && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value);
const statuses = new Set(["reviewing", "resolved", "dismissed"]);
export async function reviewProfessionalReport(form: FormData) {
  const user = await currentUser();
  if (!user?.email_confirmed_at || !["admin", "moderator"].includes(String(user.app_metadata?.role ?? ""))) return;
  const id = form.get("id");
  const status = form.get("status");
  const note = form.get("note");
  if (!validId(id) || typeof status !== "string" || !statuses.has(status) ||
      typeof note !== "string" || note.length > 2000) return;
  const db = await serverClient();
  const { error } = await db.from("professional_reports").update({
    status, reviewed_by: user.id,
    reviewed_at: new Date().toISOString(),
  }).eq("id", id);
  if (!error) {
    await db.from("professional_report_reviews").upsert({
      report_id: id, moderator_id: user.id, note: note.trim(), updated_at: new Date().toISOString(),
    }, { onConflict: "report_id" });
    revalidatePath("/admin/moderation");
  }
}
