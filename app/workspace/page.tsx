import { pageMetadata } from "@/lib/seo";
import { Dashboard } from "@/components/workspace";
export const metadata = pageMetadata(
  "/workspace",
  "Your Demo Workspace",
  "Browser-only workspace for sample candidate and employer journeys.",
);
export default function Page() {
  return <Dashboard />;
}
