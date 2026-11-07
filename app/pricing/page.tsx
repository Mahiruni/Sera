import { pageMetadata } from "@/lib/seo";
import { Pricing } from "@/components/workspace";
export const metadata = pageMetadata(
  "/pricing",
  "Featured Listing Concept — Checkout Unavailable",
  "The featured-listing concept is demonstration information. Payment checkout is not connected.",
);
export default function Page() {
  return <Pricing />;
}
