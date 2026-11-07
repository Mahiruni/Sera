import { pageMetadata } from "@/lib/seo";
import { Suspense } from "react";
import { Discovery } from "@/components/discovery";
import { Loading } from "@/components/ui";
export const metadata = pageMetadata(
  "/matches",
  "Keyword Matches — Demo Workspace",
  "Explainable keyword-overlap results from a local demo profile, not an AI hiring assessment.",
);
export default function Page() {
  return (
    <Suspense fallback={<Loading />}>
      <Discovery matches />
    </Suspense>
  );
}
