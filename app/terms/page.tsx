import { pageMetadata } from "@/lib/seo";
import { InformationPage } from "@/components/information";
export const metadata = pageMetadata(
  "/terms",
  "Account terms",
  "Terms for Sera accounts and the separate recruitment demonstration.",
);
export default function Page() {
  return <InformationPage page="terms" />;
}
