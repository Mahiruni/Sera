import { requiredProfile } from "@/lib/auth/profile";
import { workspaceData } from "@/lib/workspace/data";
import { pageNumber } from "@/lib/workspace/model";
import { WorkspaceOverview } from "@/components/workspace/overview";
import { pageMetadata } from "@/lib/seo";
export const metadata = pageMetadata(
  "/employer",
  "Employer workspace",
  "Manage your own postings, introductions and hiring conversations.",
);
export default async function Page({
  searchParams,
}: {
  searchParams: Promise<{
    jobsPage?: string;
    appsPage?: string;
    status?: string;
    error?: string;
  }>;
}) {
  const { user, profile } = await requiredProfile();
  if (!profile)
    return (
      <div className="container page">
        <h1>Your workspace is temporarily unavailable.</h1>
        <p>Please refresh to try again.</p>
      </div>
    );
  const p = await searchParams,
    jobsPage = pageNumber(p.jobsPage),
    appsPage = pageNumber(p.appsPage),
    status = p.status === "open" || p.status === "closed" ? p.status : "all";
  const data = await workspaceData(user.id, "employer", {
    jobsPage,
    appsPage,
    status,
  });
  return (
    <WorkspaceOverview
      profile={profile}
      userId={user.id}
      data={data}
      mode="employer"
      jobsPage={jobsPage}
      appsPage={appsPage}
      status={status}
      statusError={!!p.error}
    />
  );
}
