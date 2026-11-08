import { redirect } from "next/navigation";
import { requiredProfile } from "@/lib/auth/profile";
export default async function Page() {
  const { profile } = await requiredProfile();
  redirect(
    profile?.journey === "employer"
      ? "/employer#conversations"
      : "/dashboard?view=candidate#conversations",
  );
}
