import { AuthForm } from "@/components/auth/forms";
import { pageMetadata } from "@/lib/seo";
export const metadata = pageMetadata(
  "/forgot-password",
  "Reset your password",
  "Secure account access for Sera.et.",
);
export default async function Page() {
  return (
    <div className="container page">
      <AuthForm mode="forgot" />
    </div>
  );
}
