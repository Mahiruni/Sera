import "server-only";
import { safeDestination } from "./validation";
import { redirect } from "next/navigation";
import { currentUser, serverClient } from "../supabase/server";
import type { Profile } from "@/components/auth/forms";
export async function requiredProfile(destination?: string) {
  const user = await currentUser();
  if (!user || !user.email_confirmed_at)
    redirect(
      destination
        ? "/sign-in?next=" + encodeURIComponent(safeDestination(destination))
        : "/sign-in",
    );
  const client = await serverClient();
  const { data, error } = await client
    .from("profiles")
    .select(
      "id,full_name,locale,journey,location,phone,skills,experience,preferred_roles,company_name,company_role,onboarded_at,headline,summary,education",
    )
    .eq("id", user.id)
    .single();
  return { user, profile: error ? null : (data as Profile) };
}
