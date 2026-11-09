import Link from "next/link";
import { notFound } from "next/navigation";
import { pageMetadata } from "@/lib/seo";
import { JobDetail } from "@/components/discovery";
import { currentUser, serverClient } from "@/lib/supabase/server";
import { liveJob, salary } from "@/lib/recruitment/data";
import { uuid } from "@/lib/recruitment/validation";
import { ApplyForm } from "@/components/recruitment/forms";
import { MarketNav } from "@/components/recruitment/ui";
import { toggleSavedJob } from "@/lib/recruitment/actions";
export const metadata = pageMetadata(
  "/jobs",
  "Job details",
  "Review employer-provided role information and apply privately.",
);
export default async function Page({
  params,
  searchParams,
}: {
  params: Promise<{ id: string }>;
  searchParams: Promise<{ error?: string; saved?: string }>;
}) {
  const { id } = await params;
  if (/^\d+$/.test(id)) return <JobDetail id={Number(id)} />;
  if (!uuid(id)) notFound();
  const job = await liveJob(id);
  if (!job) notFound();
  const user = await currentUser();
  const c = await serverClient();
  const existing = user
    ? await c
        .from("applications")
        .select("id")
        .eq("job_id", id)
        .eq("candidate_id", user.id)
        .maybeSingle()
    : null;
  const saved = user
    ? await c
        .from("saved_jobs")
        .select("job_id")
        .eq("job_id", id)
        .eq("user_id", user.id)
        .maybeSingle()
    : null;
  const profile = user?.email_confirmed_at
    ? (
        await c
          .from("profiles")
          .select("full_name,headline,skills,experience,education")
          .eq("id", user.id)
          .single()
      ).data
    : null;
  const p = await searchParams;
  return (
    <div className="container page live-market">
      <MarketNav />
      <Link className="inline-link" href="/jobs">
        ← All jobs
      </Link>
      <div className="live-heading">
        <div>
          <p className="eyebrow">
            {job.company} / {job.status}
          </p>
          <h1>{job.title}</h1>
          <p className="subtext">
            {job.location} · {job.work_type} · {salary(job)}
          </p>
        </div>
        {user && (
          <form action={toggleSavedJob}>
            <input type="hidden" name="job_id" value={id} />
            <input
              type="hidden"
              name="remove"
              value={saved?.data ? "yes" : "no"}
            />
            <button className="button secondary">
              {saved?.data ? "Remove saved job" : "Save job"}
            </button>
          </form>
        )}
      </div>
      {p.error && (
        <p className="auth-feedback" role="alert">
          Could not save this job. Please try again.
        </p>
      )}
      {p.saved && (
        <p className="auth-notice" role="status">
          Job saved to your account.
        </p>
      )}
      <p className="live-disclosure">
        Employer provided · not independently verified. Review the opportunity
        carefully and never pay to apply.
      </p>
      <section className="panel live-copy">
        <h2>About the role</h2>
        <p>{job.description}</p>
        <h2>What you bring</h2>
        <p>{job.requirements}</p>
      </section>
      {job.employer_id === user?.id ? (
        <Link className="button" href="/employer">
          Manage your posting
        </Link>
      ) : existing?.data ? (
        <Link className="button" href={"/applications/" + existing.data.id}>
          View your application
        </Link>
      ) : job.status !== "open" ? (
        <p className="auth-notice">This job is closed.</p>
      ) : user?.email_confirmed_at ? (
        <ApplyForm
          jobId={id}
          jobTitle={job.title}
          company={job.company}
          snapshot={{
            name: profile?.full_name ?? "",
            headline: profile?.headline ?? "",
            skills: profile?.skills ?? "",
            experience: profile?.experience ?? "",
            education: profile?.education ?? "",
          }}
        />
      ) : (
        <div className="panel live-empty">
          <h2>Ready to introduce yourself?</h2>
          <p>Create an account and verify your email to apply.</p>
          <Link className="button" href="/sign-in">
            Sign in to apply
          </Link>
        </div>
      )}
    </div>
  );
}
