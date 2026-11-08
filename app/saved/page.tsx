import Link from "next/link";
import { requiredProfile } from "@/lib/auth/profile";
import { serverClient } from "@/lib/supabase/server";
import { type LiveJob } from "@/lib/recruitment/data";
import { LiveJobCard, Empty } from "@/components/recruitment/ui";
import { WorkspaceNav } from "@/components/workspace/navigation";
import { toggleSavedJob } from "@/lib/recruitment/actions";
import { pageMetadata } from "@/lib/seo";
export const metadata = pageMetadata(
  "/saved",
  "Saved jobs",
  "Your private saved roles, synchronized with your account.",
);
export default async function Page() {
  const { user } = await requiredProfile();
  const c = await serverClient();
  const { data, error } = await c
    .from("saved_jobs")
    .select("job_id,jobs(*)")
    .eq("user_id", user.id)
    .order("created_at", { ascending: false })
    .limit(100);
  const rows = (data || []) as unknown as {
    job_id: string;
    jobs: LiveJob | null;
  }[];
  return (
    <div className="container page live-market workspace-detail">
      <p className="eyebrow">SERA / SAVED FOR LATER</p>
      <h1>Keep the possibilities close.</h1>
      <WorkspaceNav />
      {error ? (
        <Empty title="Saved jobs could not be loaded">
          <p>Please refresh.</p>
        </Empty>
      ) : rows.length ? (
        <div className="live-job-grid">
          {rows.map((r) => (
            <div key={r.job_id}>
              {r.jobs ? (
                <LiveJobCard job={r.jobs} />
              ) : (
                <Empty title="This posting is no longer public">
                  <p>It may have closed.</p>
                </Empty>
              )}
              <form action={toggleSavedJob} className="live-remove">
                <input name="job_id" type="hidden" value={r.job_id} />
                <input name="remove" type="hidden" value="yes" />
                <button className="button secondary">Remove</button>
              </form>
            </div>
          ))}
        </div>
      ) : (
        <Empty title="A little room for possibilities">
          <p>
            Save roles from their detail page and find them here on any device.
          </p>
          <Link className="inline-link" href="/jobs">
            Explore jobs →
          </Link>
        </Empty>
      )}
    </div>
  );
}
