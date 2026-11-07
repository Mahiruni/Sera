import Link from "next/link";
import { requiredProfile } from "@/lib/auth/profile";
import { serverClient } from "@/lib/supabase/server";
import { type LiveJob, type LiveApplication } from "@/lib/recruitment/data";
import { MarketNav, Empty } from "@/components/recruitment/ui";
import { changeJobStatus } from "@/lib/recruitment/actions";
import { pageMetadata } from "@/lib/seo";
export const metadata = pageMetadata(
  "/employer",
  "Employer workspace",
  "Manage your posted jobs and incoming applications.",
);
export default async function Page({
  searchParams,
}: {
  searchParams: Promise<{ error?: string }>;
}) {
  const { user } = await requiredProfile();
  const c = await serverClient();
  const { data, error } = await c
    .from("jobs")
    .select("*")
    .eq("employer_id", user.id)
    .order("created_at", { ascending: false })
    .limit(100);
  const jobs = (data || []) as LiveJob[];
  const incoming = jobs.length
    ? await c
        .from("applications")
        .select("*,jobs(id,title,company,employer_id,status)")
        .in(
          "job_id",
          jobs.map((j) => j.id),
        )
        .order("created_at", { ascending: false })
        .limit(100)
    : { data: [], error: null };
  const apps = (incoming.data || []) as unknown as LiveApplication[];
  const p = await searchParams;
  return (
    <div className="container page live-market">
      <div className="live-heading">
        <div>
          <p className="eyebrow">SERA / EMPLOYER</p>
          <h1>A clearer view of your hiring.</h1>
          <p className="subtext">
            Manage your own postings, review introductions and keep
            conversations together.
          </p>
        </div>
        <Link className="button" href="/employer/post">
          Post a job
        </Link>
      </div>
      <MarketNav />
      {p.error && (
        <p className="auth-feedback" role="alert">
          Job status could not be updated.
        </p>
      )}
      <h2 className="live-section-title">Your jobs</h2>
      {error ? (
        <Empty title="Your jobs could not be loaded">
          <p>Please refresh.</p>
        </Empty>
      ) : jobs.length ? (
        <div className="live-list">
          {jobs.map((j) => (
            <article className="panel live-row" key={j.id}>
              <div>
                <span className="live-badge">{j.status}</span>
                <h3>
                  <Link href={"/jobs/" + j.id}>{j.title}</Link>
                </h3>
                <p>
                  {j.company} · {j.location}
                </p>
              </div>
              <form action={changeJobStatus}>
                <input name="job_id" type="hidden" value={j.id} />
                <input
                  name="status"
                  type="hidden"
                  value={j.status === "open" ? "closed" : "open"}
                />
                <button className="button secondary">
                  {j.status === "open" ? "Close posting" : "Reopen posting"}
                </button>
              </form>
            </article>
          ))}
        </div>
      ) : (
        <Empty title="Your first posting starts here">
          <p>
            Publish a real role with clear responsibilities and requirements.
          </p>
        </Empty>
      )}
      <h2 className="live-section-title">Incoming applications</h2>
      {incoming.error ? (
        <Empty title="Applications could not be loaded">
          <p>Please refresh.</p>
        </Empty>
      ) : apps.length ? (
        <div className="live-list">
          {apps.map((a) => (
            <Link
              key={a.id}
              className="panel live-row"
              href={"/applications/" + a.id}
            >
              <div>
                <p>{a.jobs?.title || "Job"}</p>
                <h3>{a.candidate_name}</h3>
              </div>
              <span className="live-badge">{a.stage} →</span>
            </Link>
          ))}
        </div>
      ) : (
        <Empty title="No applications yet">
          <p>Candidate introductions to your jobs will appear here.</p>
        </Empty>
      )}
      <p className="live-disclosure">
        These tools do not confer verified employer status. Do not request
        payments, identity documents or financial information. Lists show up to
        100 recent records.
      </p>
    </div>
  );
}
