import { PremiumAuth } from "@/components/auth/premium";
import { pageMetadata } from "@/lib/seo";
export const metadata = pageMetadata(
  "/sign-in",
  "Sign in",
  "Secure account access for Sera.et.",
);
export default async function Page({
  searchParams,
}: {
  searchParams: Promise<{ password?: string; signed?: string; next?: string }>;
}) {
  const status = await searchParams;
  return (
    <PremiumAuth
      mode="signin"
      destination={status.next}
      notice={
        status.password === "updated"
          ? "password-updated"
          : status.signed === "out"
            ? "signed-out"
            : undefined
      }
    />
  );
}
