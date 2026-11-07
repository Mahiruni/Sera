import { AuthForm } from "@/components/auth/forms";
import { pageMetadata } from "@/lib/seo";
export const metadata = pageMetadata(
  "/sign-up",
  "Create account",
  "Secure account access for Sera.et.",
);
export default async function Page() {
  return (
    <div className="container page">
      <AuthForm
        mode="signup"
        registrationOpen={process.env.SERA_ACCOUNT_REGISTRATION === "enabled"}
      />
    </div>
  );
}
