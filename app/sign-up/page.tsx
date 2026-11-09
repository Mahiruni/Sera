import { PremiumAuth } from "@/components/auth/premium";
import { pageMetadata } from "@/lib/seo";
export const metadata = pageMetadata(
  "/sign-up",
  "Create account",
  "Secure account access for Sera.et.",
);
export default async function Page() {
  return (
    <PremiumAuth
      mode="signup"
      registrationOpen={process.env.SERA_ACCOUNT_REGISTRATION === "enabled"}
    />
  );
}
