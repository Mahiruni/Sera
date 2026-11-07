import { publicMetadata } from "@/lib/seo";
import { InformationPage } from "@/components/information";
export const metadata = publicMetadata("/about");
export default function Page() {
  return <InformationPage page="about" />;
}
