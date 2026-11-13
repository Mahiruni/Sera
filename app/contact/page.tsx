import { pageMetadata } from "@/lib/seo";
import { InformationPage } from "@/components/information";
export const metadata = pageMetadata(
  "/contact",
  "Contact Status — Support Not Yet Connected",
  "Sera has not configured verified support contact details or a live contact form.",
);
export default function Page() {
  return <InformationPage page="contact" />;
}
