"use server";
import { currentUser, serverClient } from "@/lib/supabase/server";
import { readProfile } from "@/lib/auth/validation";
import { revalidatePath } from "next/cache";
export async function saveAccount(data: FormData) {
  const user = await currentUser();
  if (!user?.email_confirmed_at) return { error: "Please sign in again." };
  const base = readProfile(data);
  const headline = String(data.get("headline") || "").trim();
  const summary = String(data.get("summary") || "").trim();
  const education = String(data.get("education") || "").trim();
  if (
    !base ||
    headline.length > 120 ||
    summary.length > 2000 ||
    education.length > 2000
  )
    return { error: "Check the required fields and character limits." };
  const client = await serverClient();
  const { error } = await client
    .from("profiles")
    .update({
      ...base,
      headline,
      summary,
      education,
      onboarded_at: new Date().toISOString(),
    })
    .eq("id", user.id)
    .select("id")
    .single();
  if (error)
    return { error: "Your profile could not be saved. Please try again." };
  const auth = await client.auth.updateUser({
    data: { full_name: base.full_name, locale: base.locale },
  });
  revalidatePath("/settings");
  return {
    success: true,
    warning: auth.error
      ? "Profile saved. Save once more to update your email language."
      : undefined,
  };
}
