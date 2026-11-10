"use server";
import { currentUser, serverClient } from "@/lib/supabase/server";
import { revalidatePath } from "next/cache";

export async function saveProfileVisibility(form: FormData) {
  const user = await currentUser();
  if (!user?.email_confirmed_at) return;
  const visibility = form.get("visibility");
  if (visibility !== "public" && visibility !== "members" && visibility !== "connections") return;
  const db = await serverClient();
  const { error } = await db.from("professional_profile_visibility").upsert({
    user_id: user.id,
    visibility,
    updated_at: new Date().toISOString(),
  }, { onConflict: "user_id" });
  if (error) throw new Error("Could not update profile visibility.");
  if (visibility !== "public") {
    const { error: unpublishError } = await db.from("professional_public_portfolios").update({ published: false }).eq("user_id", user.id);
    if (unpublishError) throw new Error("Visibility saved, but portfolio unpublishing failed. Please retry.");
  }
  revalidatePath("/settings");
}
