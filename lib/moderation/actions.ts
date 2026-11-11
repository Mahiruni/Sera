"use server";
import { serverClient } from "@/lib/supabase/server";
import { revalidatePath } from "next/cache";
export async function reviewReport(form: FormData) {
  const db = await serverClient();
  const { data: { user } } = await db.auth.getUser();
  if (!user?.email_confirmed_at) throw new Error("Sign in required");
  const id = String(form.get("report_id") || "");
  const action = String(form.get("action") || "");
  const explanation = String(form.get("explanation") || "").trim();
  if (!/^[0-9a-f-]{36}$/i.test(id) || !["dismiss","quarantine","remove","restore"].includes(action) || explanation.length < 10 || explanation.length > 2000)
    throw new Error("Invalid review details");
  const { error } = await db.rpc("moderator_review_report", { p_report_id: id, p_action: action, p_explanation: explanation });
  if (error) throw new Error("Unable to apply review. Check your moderator access and report status.");
  revalidatePath("/moderation");
  revalidatePath("/feed");
}

export async function reviewAppeal(form: FormData) {
  const db = await serverClient();
  const { data: { user } } = await db.auth.getUser();
  if (!user?.email_confirmed_at) throw new Error("Sign in required");
  const id = String(form.get("appeal_id") || "");
  const choice = String(form.get("decision") || "");
  const explanation = String(form.get("explanation") || "").trim();
  if (!/^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i.test(id) || !["accept","reject"].includes(choice) || explanation.length < 10 || explanation.length > 2000)
    throw new Error("Invalid appeal review");
  const { error } = await db.rpc("moderator_review_appeal", { p_appeal_id: id, p_accept: choice === "accept", p_explanation: explanation });
  if (error) throw new Error("Could not review appeal.");
  revalidatePath("/moderation");
  revalidatePath("/feed");
}
