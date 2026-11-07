import { Suspense } from "react";
import { Loading } from "@/components/ui";
import { Account } from "@/components/workspace";
export const metadata = { title: "Account" };
export default function Page() {
  return (
    <Suspense fallback={<Loading />}>
      <Account />
    </Suspense>
  );
}
