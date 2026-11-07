import { Suspense } from "react";
import { Discovery } from "@/components/discovery";
import { Loading } from "@/components/ui";
export const metadata = { title: "Skill matches" };
export default function Page() {
  return (
    <Suspense fallback={<Loading />}>
      <Discovery matches />
    </Suspense>
  );
}
