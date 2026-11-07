import { AuthForm } from "@/components/auth/forms";
import { pageMetadata } from "@/lib/seo";
export const metadata = pageMetadata(
  "/sign-in",
  "Sign in",
  "Secure account access for Sera.et.",
);
export default async function Page({
  searchParams,
}: {
  searchParams: Promise<{ password?: string; signed?: string }>;
}) {
  const status = await searchParams;
  return (
    <div className="container page">
      {status.password === "updated" && (
        <p className="auth-notice" role="status">
          Your password was updated. Sign in with the new password.
        </p>
      )}
      {status.signed === "out" && (
        <p className="auth-notice" role="status">
          You have signed out.
        </p>
      )}
      <AuthForm mode="signin" />
    </div>
  );
}
