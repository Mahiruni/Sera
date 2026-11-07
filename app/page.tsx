import { PublicLanding } from "@/components/public-site";
import { publicMetadata } from "@/lib/seo";
export const metadata = publicMetadata("/");
export default function Page() {
  return <PublicLanding />;
}
