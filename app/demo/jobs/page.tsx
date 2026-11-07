import { Suspense } from "react";
import { Discovery } from "@/components/discovery";
import { pageMetadata } from "@/lib/seo";
export const metadata = pageMetadata(
  "/demo/jobs",
  "Sample jobs",
  "Browser-local recruitment demonstration.",
);
export default function Page() {
  return (
    <Suspense>
      <Discovery />
    </Suspense>
  );
}
