import { pageMetadata } from "@/lib/seo";
import { Suspense } from "react";
import { Discovery } from "@/components/discovery";
import { Loading } from "@/components/ui";
export const metadata = pageMetadata(
  "/jobs",
  "Sample Job Discovery — Recruitment Demo",
  "Search sample vacancies in the Sera.et demo. These are not verified live jobs.",
);
export default function Page() {
  return (
    <Suspense fallback={<Loading />}>
      <Discovery />
    </Suspense>
  );
}
