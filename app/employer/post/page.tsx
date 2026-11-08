import { requiredProfile } from "@/lib/auth/profile";
import { JobPostForm } from "@/components/recruitment/forms";
import { WorkspaceNav } from "@/components/workspace/navigation";
import { pageMetadata } from "@/lib/seo";
export const metadata = pageMetadata(
  "/employer/post",
  "Post a job",
  "Publish clear employer-provided job information.",
);
export default async function Page() {
  const { profile } = await requiredProfile();
  return (
    <div className="container page live-market workspace-detail">
      <p className="eyebrow">SERA / NEW OPPORTUNITY</p>
      <h1>Make room for someone great.</h1>
      <p className="subtext">
        Publish a role you are authorized to represent. Everything below will be
        visible publicly.
      </p>
      <WorkspaceNav employer />
      <JobPostForm company={profile?.company_name || ""} />
    </div>
  );
}
