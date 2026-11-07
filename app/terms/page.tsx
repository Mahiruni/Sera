import { pageMetadata } from "@/lib/seo";
import { InformationPage } from "@/components/information";
export const metadata = pageMetadata(
  "/terms",
  "Draft Usage Notice — Demo Limitations",
  "Unfinished usage information for Sera.et’s demonstration, not final legal terms for a live marketplace.",
);
export default function Page() {
  return <InformationPage page="terms" />;
}
