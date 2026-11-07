import "server-only";
import { redirect } from "next/navigation";
import { currentUser, serverClient } from "../supabase/server";
import type { Profile } from "@/components/auth/forms";
export async function requiredProfile() {
  const user = await currentUser();
  if (!user || !user.email_confirmed_at) redirect("/sign-in");
  const client = await serverClient();
  const { data, error } = await client
    .from("profiles")
    .select(
      "id,full_name,locale,journey,location,phone,skills,experience,preferred_roles,company_name,company_role,onboarded_at",
    )
    .eq("id", user.id)
    .single();
  return { user, profile: error ? null : (data as Profile) };
}
