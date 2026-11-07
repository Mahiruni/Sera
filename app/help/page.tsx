import { pageMetadata } from "@/lib/seo";
import { InformationPage } from "@/components/information";
export const metadata = pageMetadata(
  "/help",
  "Help & Demo Support Information",
  "Read guidance on browser storage, language preferences and demo workspaces. Live support is not connected.",
);
export default function Page() {
  return <InformationPage page="help" />;
}
