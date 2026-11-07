import { pageMetadata } from "@/lib/seo";
import { Suspense } from "react";
import { Discovery } from "@/components/discovery";
import { Loading } from "@/components/ui";
export const metadata = pageMetadata(
  "/saved",
  "Saved Sample Roles — Your Browser",
  "Your locally bookmarked demo roles. This browser-only page is not a public job listing.",
);
export default function Page() {
  return (
    <Suspense fallback={<Loading />}>
      <Discovery savedOnly />
    </Suspense>
  );
}
