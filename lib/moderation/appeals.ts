"use server";
import { serverClient } from "@/lib/supabase/server";
import { revalidatePath } from "next/cache";
export async function submitModerationAppeal(form: FormData) {
  const db = await serverClient();
  const { data: { user } } = await db.auth.getUser();
  if (!user?.email_confirmed_at) throw new Error("Sign in required");
  const decisionId = String(form.get("decision_id") || "");
  const explanation = String(form.get("explanation") || "").trim();
  if (!/^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i.test(decisionId) || explanation.length < 20 || explanation.length > 2000)
    throw new Error("Please provide a valid appeal and at least 20 characters of context.");
  const { error } = await db.from("moderation_appeals").insert({ decision_id: decisionId, explanation });
  if (error) throw new Error("Unable to submit appeal. You may already have appealed this decision.");
  revalidatePath("/settings/safety");
}
