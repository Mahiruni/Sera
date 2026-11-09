import { redirect } from "next/navigation";
import { requiredProfile } from "@/lib/auth/profile";
import { workspaceData } from "@/lib/workspace/data";
import { WorkspaceOverview } from "@/components/workspace/overview";
import { linkedInBadge } from "@/lib/auth/linkedin-badge";
import { pageMetadata } from "@/lib/seo";
export const metadata = pageMetadata(
  "/dashboard",
  "Your career workspace",
  "Your applications, saved roles, conversations and profile in one private workspace.",
);
export default async function Page({
  searchParams,
}: {
  searchParams: Promise<{ view?: string }>;
}) {
  const { user, profile } = await requiredProfile();
  if (!profile)
    return (
      <div className="container page">
        <h1>Your workspace is temporarily unavailable.</h1>
        <p>Please refresh to try again.</p>
      </div>
    );
  if (!profile.onboarded_at) redirect("/onboarding");
  const params = await searchParams;
  if (profile.journey === "employer" && params.view !== "candidate")
    redirect("/employer");
  const data = await workspaceData(user.id, "candidate");
  const linkedin = await linkedInBadge(user.id);
  return (
    <WorkspaceOverview
      profile={profile}
      userId={user.id}
      data={data}
      mode="candidate"
      linkedin={linkedin}
    />
  );
}
