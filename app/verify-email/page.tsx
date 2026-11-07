import { AuthForm } from "@/components/auth/forms";
import { pageMetadata } from "@/lib/seo";
export const metadata = pageMetadata(
  "/verify-email",
  "Verify your email",
  "Secure account access for Sera.et.",
);
export default async function Page({
  searchParams,
}: {
  searchParams: Promise<{ error?: string }>;
}) {
  const { error } = await searchParams;
  return (
    <div className="container page">
      {error && (
        <p className="auth-feedback" role="alert">
          This link is invalid or expired. Request another verification email
          below, or use password recovery if you were resetting a password.
        </p>
      )}
      <AuthForm mode="verify" />
    </div>
  );
}
