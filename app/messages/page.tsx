import { pageMetadata } from "@/lib/seo";
import { Suspense } from "react";
import { Loading } from "@/components/ui";
import { Messages } from "@/components/workspace";
export const metadata = pageMetadata(
  "/messages",
  "Local Demo Messages",
  "Browser-local sample conversations, not live chat or support delivery.",
);
export default function Page() {
  return (
    <Suspense fallback={<Loading />}>
      <Messages />
    </Suspense>
  );
}
