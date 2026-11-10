import { currentUser } from "@/lib/supabase/server";
import { redirect } from "next/navigation";
import { PublicLanding } from "@/components/public-site";
import { publicMetadata } from "@/lib/seo";
export const metadata = publicMetadata("/");
export default async function Page() {
  const user = await currentUser();
  if (user?.email_confirmed_at) redirect("/feed");
  return <PublicLanding />;
}
