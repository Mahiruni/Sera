import { AuthForm } from "@/components/auth/forms";
import { pageMetadata } from "@/lib/seo";
import { currentUser } from "@/lib/supabase/server";
import { redirect } from "next/navigation";
export const metadata = pageMetadata(
  "/reset-password",
  "Choose a new password",
  "Secure account access for Sera.et.",
);
export default async function Page() {
  if (!(await currentUser())) redirect("/forgot-password");
  return (
    <div className="container page">
      <AuthForm mode="reset" />
    </div>
  );
}
