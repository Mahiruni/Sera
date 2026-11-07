import { pageMetadata } from "@/lib/seo";
import { Applications } from "@/components/workspace";
export const metadata = pageMetadata(
  "/applications",
  "Local Demo Applications",
  "Track local sample applications. Nothing is delivered to a real employer.",
);
export default function Page() {
  return <Applications />;
}
