import { Suspense } from "react";
import { Discovery } from "@/components/discovery";
import { Loading } from "@/components/ui";
export const metadata = { title: "Discover opportunities" };
export default function Page() {
  return (
    <Suspense fallback={<Loading />}>
      <Discovery />
    </Suspense>
  );
}
