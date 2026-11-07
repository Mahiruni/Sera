import { pageMetadata } from "@/lib/seo";
import { Suspense } from "react";
import { Loading } from "@/components/ui";
import { Account } from "@/components/workspace";
export const metadata = pageMetadata(
  "/account",
  "Demo Workspace — Candidate & Employer",
  "Choose a browser-only demonstration role. Real account access is available separately at /sign-in.",
);
export default function Page() {
  return (
    <Suspense fallback={<Loading />}>
      <Account />
    </Suspense>
  );
}
