import { pageMetadata } from "@/lib/seo";
import { PostJob } from "@/components/workspace";
export const metadata = pageMetadata(
  "/employer/post",
  "Create a Sample Role — Employer Demo",
  "Create a browser-local sample vacancy. This does not publish a live job.",
);
export default function Page() {
  return <PostJob />;
}
