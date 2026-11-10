"use server";
import { currentUser, serverClient } from "@/lib/supabase/server";
import { revalidatePath } from "next/cache";

export async function updatePublicPortfolio(form: FormData) {
  const user = await currentUser();
  if (!user?.email_confirmed_at) throw new Error("Sign in to manage your portfolio.");
  const publish = form.get("intent") === "publish";
  const db = await serverClient();
  if (!publish) {
    const { error } = await db.from("professional_public_portfolios").update({ published: false }).eq("user_id", user.id);
    if (error) throw new Error("Could not unpublish portfolio.");
  } else {
    const [{ data: preference, error: preferenceError }, { data: profile, error: profileError }] = await Promise.all([
      db.from("professional_profile_visibility").select("visibility").eq("user_id",user.id).maybeSingle(),
      db.from("profiles").select("full_name,headline,summary,location,skills,experience,education").eq("id",user.id).single()
    ]);
    if (preferenceError || preference?.visibility !== "public") throw new Error("Choose Public internet as your preferred audience first.");
    if (profileError || !profile?.full_name?.trim()) throw new Error("Complete your professional name before publishing.");
    const { error } = await db.from("professional_public_portfolios").upsert({
      user_id:user.id,full_name:profile.full_name,headline:profile.headline,summary:profile.summary,
      location:profile.location,skills:profile.skills,experience:profile.experience,education:profile.education,
      published:true,updated_at:new Date().toISOString()
    },{onConflict:"user_id"});
    if (error) throw new Error("Could not publish portfolio.");
  }
  revalidatePath("/settings");
  revalidatePath("/portfolio/" + user.id);
}
