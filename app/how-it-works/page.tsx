import { publicMetadata } from "@/lib/seo";
import { InformationPage } from "@/components/information";
export const metadata = publicMetadata("/how-it-works");
export default function Page() {
  return <InformationPage page="how-it-works" />;
}
