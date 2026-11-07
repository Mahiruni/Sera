import { Suspense } from "react";
import { Loading } from "@/components/ui";
import { Messages } from "@/components/workspace";
export const metadata = { title: "Messages" };
export default function Page() {
  return (
    <Suspense fallback={<Loading />}>
      <Messages />
    </Suspense>
  );
}
