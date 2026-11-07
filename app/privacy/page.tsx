import { pageMetadata } from "@/lib/seo";
import { InformationPage } from "@/components/information";
export const metadata = pageMetadata(
  "/privacy",
  "Privacy notice",
  "How Sera handles account, profile and browser-storage information.",
);
export default function Page() {
  return <InformationPage page="privacy" />;
}
