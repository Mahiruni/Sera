import { pageMetadata } from "@/lib/seo";
import { Dashboard } from "@/components/workspace";
export const metadata = pageMetadata(
  "/employer",
  "Employer Demo Dashboard",
  "Explore sample employer workflows. Live employer accounts are not launched.",
);
export default function Page() {
  return <Dashboard employer />;
}
