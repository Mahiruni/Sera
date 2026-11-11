import Link from "next/link";
import { PageIntro } from "@/components/page-intro";
import { FileText } from "lucide-react";
import { requiredProfile } from "@/lib/auth/profile";
import { serverClient } from "@/lib/supabase/server";
import { type LiveApplication } from "@/lib/recruitment/data";
import { Empty } from "@/components/recruitment/ui";
import { WorkspaceNav } from "@/components/workspace/navigation";
import { stageLabel, activityDate } from "@/lib/workspace/model";
import { pageMetadata } from "@/lib/seo";
export const metadata = pageMetadata(
  "/applications",
  "My applications",
  "Track your private applications and conversations.",
);
export default async function Page({
  searchParams,
}: {
  searchParams: Promise<{ stage?: string; page?: string }>;
}) {
  const params = await searchParams;
  const stages = ["applied", "screened", "interview", "offer", "rejected", "withdrawn"] as const;
  const stage = stages.find((value) => value === params.stage) || "all";
  const page = Math.min(1000, Math.max(1, Number.parseInt(params.page || "1", 10) || 1));
  const pageSize = 20;
  const { user } = await requiredProfile();
  const c = await serverClient();
  let query = c
    .from("applications")
    .select("*,jobs(id,title,company,employer_id,status)", { count: "exact" })
    .eq("candidate_id", user.id);
  if (stage !== "all") query = query.eq("stage", stage);
  const { data, error, count } = await query
    .order("created_at", { ascending: false })
    .order("id", { ascending: true })
    .range((page - 1) * pageSize, page * pageSize - 1);
  const apps = (data || []) as unknown as LiveApplication[];
  const journey = ["applied", "screened", "interview", "offer"] as const;
  const stageIndex = (value: string) => journey.findIndex((step) => step === value);
  return (
    <div className="container page live-market workspace-detail">
      <PageIntro icon={FileText} title="Every introduction, in one place."
        description="Follow your applications from first hello to your next opportunity." />
      <WorkspaceNav />
      <form className="panel live-search" method="get">
        <label>
          Application status
          <select name="stage" defaultValue={stage}>
            <option value="all">All applications</option>
            <option value="applied">Submitted</option>
            <option value="screened">In review</option>
            <option value="interview">Interview</option>
            <option value="offer">Offer</option>
            <option value="rejected">Not selected</option>
            <option value="withdrawn">Withdrawn</option>
          </select>
        </label>
        <button className="button">Filter applications</button>
      </form>
      {!error && (
        <p className="live-disclosure" role="status">
          {count === 0 ? "No matching applications" : `${(count ?? 0).toLocaleString()} applications · Page ${page} of ${Math.max(1, Math.ceil((count ?? 0) / pageSize))}`}
        </p>
      )}
      {error ? (
        <Empty title="Applications could not be loaded">
          <p>Please refresh.</p>
        </Empty>
      ) : apps.length ? (
        <div className="live-list">
          {apps.map((a) => (
            <Link
              className="panel live-row"
              href={"/applications/" + a.id}
              key={a.id}
            >
              <div>
                <p>{a.jobs?.company}</p>
                <h2>{a.jobs?.title || "Job unavailable"}</h2>
                <p>Applied {activityDate(a.created_at)}</p>
                {stageIndex(a.stage) >= 0 ? (
                  <div aria-label={`Current application stage: ${stageLabel(a.stage)}`} style={{ marginTop: 12 }}>
                    <div style={{ display: "flex", gap: 5 }} aria-hidden="true">
                      {journey.map((step, index) => (
                        <span key={step} style={{ height: 5, flex: 1, borderRadius: 999, background: index <= stageIndex(a.stage) ? "#16B9D6" : "#DCE7EC" }} />
                      ))}
                    </div>
                    <p className="auth-help" style={{ marginTop: 6 }}>Current stage: {stageLabel(a.stage)}</p>
                  </div>
                ) : (
                  <p className="auth-help">{a.stage === "withdrawn" ? "Application withdrawn" : "Application closed — not selected"}</p>
                )}
              </div>
              <span className={`ws-status ${a.stage}`}>
                {stageLabel(a.stage)} →
              </span>
            </Link>
          ))}
        </div>
      ) : (
        <Empty title="Your next introduction is waiting">
          <p>Explore open roles and send a thoughtful application.</p>
          <Link className="inline-link" href="/jobs">
            Find jobs →
          </Link>
        </Empty>
      )}
      {!error && (count ?? 0) > pageSize && (
        <nav className="live-pagination" aria-label="Application pages">
          {page > 1 && (
            <Link href={`/applications?${new URLSearchParams({ stage, page: String(page - 1) })}`}>
              ← Previous
            </Link>
          )}
          {page * pageSize < (count ?? 0) && (
            <Link href={`/applications?${new URLSearchParams({ stage, page: String(page + 1) })}`}>
              Next →
            </Link>
          )}
        </nav>
      )}
    </div>
  );
}
