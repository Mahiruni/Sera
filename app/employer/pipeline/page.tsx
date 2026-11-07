import { pageMetadata } from "@/lib/seo";
import { Applications } from "@/components/workspace";
export const metadata = pageMetadata(
  "/employer/pipeline",
  "Demo Hiring Pipeline",
  "Review local demo applications and stages without making real hiring decisions.",
);
export default function Page() {
  return <Applications pipeline />;
}
