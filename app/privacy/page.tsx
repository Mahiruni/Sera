import { pageMetadata } from "@/lib/seo";
import { InformationPage } from "@/components/information";
export const metadata = pageMetadata(
  "/privacy",
  "Privacy Information — Draft Demo Notice",
  "Draft information about browser-local storage and current demo limitations. Not a final live-service privacy policy.",
);
export default function Page() {
  return <InformationPage page="privacy" />;
}
