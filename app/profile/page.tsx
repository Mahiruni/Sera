import { pageMetadata } from "@/lib/seo";
import { ProfilePage } from "@/components/workspace";
export const metadata = pageMetadata(
  "/profile",
  "Local Demo Profile",
  "Edit fictional profile information in the Sera.et browser demonstration.",
);
export default function Page() {
  return <ProfilePage />;
}
