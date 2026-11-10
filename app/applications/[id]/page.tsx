import { memberCards } from "@/lib/activity/members";
import { MemberAvatar } from "@/components/activity/avatar";
import Link from "next/link";
import { notFound } from "next/navigation";
import { requiredProfile } from "@/lib/auth/profile";
import { serverClient } from "@/lib/supabase/server";
import { type LiveApplication } from "@/lib/recruitment/data";
import { uuid, applicationStages } from "@/lib/recruitment/validation";
import { changeStage } from "@/lib/recruitment/actions";
import { MessageForm } from "@/components/recruitment/forms";
import { WorkspaceNav } from "@/components/workspace/navigation";
import { stageLabel } from "@/lib/workspace/model";
import { pageMetadata } from "@/lib/seo";
export const metadata = pageMetadata(
  "/applications",
  "Application conversation",
  "Private application details and messages.",
);
export default async function Page({
  params,
  searchParams,
}: {
  params: Promise<{ id: string }>;
  searchParams: Promise<{ error?: string; updated?: string }>;
}) {
  const { user } = await requiredProfile();
  const { id } = await params;
  if (!uuid(id)) notFound();
  const c = await serverClient();
  const r = await c
    .from("applications")
    .select("*,jobs(id,title,company,employer_id,status)")
    .eq("id", id)
    .maybeSingle();
  if (r.error) throw Error("Application could not be loaded. Please retry.");
  if (!r.data) notFound();
  const a = r.data as unknown as LiveApplication;
  const employer = a.jobs?.employer_id === user.id;
  const messages = await c
    .from("application_messages")
    .select("id,sender_id,body,created_at")
    .eq("application_id", id)
    .order("created_at", { ascending: true })
    .limit(200);
  let cvUrl: string | null = null;
  if (a.cv_path && a.stage !== "withdrawn") {
    const s = await c.storage
      .from("sera-account-private")
      .createSignedUrl(a.cv_path, 300, { download: a.cv_name || true });
    cvUrl = s.data?.signedUrl ?? null;
  }
  const people = await memberCards([
    a.candidate_id,
    ...(a.jobs ? [a.jobs.employer_id] : []),
  ]);
  const p = await searchParams;
  return (
    <div className="container page live-market workspace-detail">
      <WorkspaceNav employer={employer} />
      <Link
        className="inline-link"
        href={employer ? "/employer" : "/applications"}
      >
        ← Back to applications
      </Link>
      <div className="live-heading">
        <div>
          <p className="eyebrow">
            {a.jobs?.company} / {stageLabel(a.stage)}
          </p>
          <h1>{a.jobs?.title || "Application"}</h1>
          <p className="subtext">
            <MemberAvatar
              name={a.candidate_name}
              photo={people.get(a.candidate_id)?.photo}
              size={28}
            />{" "}
            Introduction from {a.candidate_name}
          </p>
        </div>
        {a.jobs && (
          <Link className="button secondary" href={"/jobs/" + a.job_id}>
            View job
          </Link>
        )}
      </div>
      {p.error && (
        <p className="auth-feedback" role="alert">
          This stage change could not be saved.
        </p>
      )}
      {p.updated && (
        <p className="auth-notice" role="status">
          Stage updated.
        </p>
      )}
      <div className="live-detail-grid">
        <section className="panel live-copy">
          <h2>Shared introduction</h2>
          <p>{a.cover_note}</p>
          <h3>Headline</h3>
          <p>{a.candidate_headline || "Not provided"}</p>
          <h3>Skills</h3>
          <p>{a.skills || "Not provided"}</p>
          <h3>Experience</h3>
          <p>{a.experience || "Not provided"}</p>
          <h3>Education</h3>
          <p style={{ whiteSpace: "pre-line" }}>
            {a.education || "Not provided"}
          </p>
          <h3>CV</h3>
          {a.cv_path ? (
            cvUrl ? (
              <p>
                <a
                  className="button secondary"
                  href={cvUrl}
                  rel="noopener noreferrer"
                >
                  Download {a.cv_name || "CV"}
                </a>
              </p>
            ) : (
              <p>CV unavailable.</p>
            )
          ) : (
            <p>Not attached</p>
          )}
          <p className="live-disclosure">
            This is the profile snapshot shared at application time.
          </p>
          {a.stage !== "withdrawn" && (
            <form action={changeStage} className="live-form">
              <input name="application_id" type="hidden" value={id} />
              {employer ? (
                <label className="auth-label">
                  Application stage
                  <select
                    name="stage"
                    defaultValue={
                      applicationStages.includes(
                        a.stage as (typeof applicationStages)[number],
                      )
                        ? a.stage
                        : "screened"
                    }
                  >
                    {applicationStages.map((s) => (
                      <option key={s} value={s}>
                        {stageLabel(s)}
                      </option>
                    ))}
                  </select>
                </label>
              ) : (
                <>
                  <input name="stage" type="hidden" value="withdrawn" />
                  <p className="auth-help">
                    Withdrawing ends this conversation and cannot be undone.
                  </p>
                </>
              )}
              <button className="button secondary">
                {employer ? "Update stage" : "Withdraw application"}
              </button>
            </form>
          )}
        </section>
        <section className="panel live-copy">
          <h2>Conversation</h2>
          <p className="auth-help">
            Visible only to the applicant and job poster. Messages are saved in
            Sera; no external chat or email delivery is implied. Up to 200
            messages shown.
          </p>
          <div className="live-thread">
            {messages.error ? (
              <p role="alert">Messages could not be loaded. Please refresh.</p>
            ) : !messages.data?.length ? (
              <p className="subtext">Start a thoughtful conversation.</p>
            ) : (
              messages.data.map((m) => (
                <article
                  className={
                    "live-message " + (m.sender_id === user.id ? "mine" : "")
                  }
                  key={m.id}
                >
                  <span>
                    <MemberAvatar
                      name={people.get(m.sender_id)?.name || "Member"}
                      photo={people.get(m.sender_id)?.photo}
                      size={24}
                    />{" "}
                    {m.sender_id === user.id
                      ? "You"
                      : employer
                        ? a.candidate_name
                        : "Job poster"}{" "}
                    ·{" "}
                    {new Date(m.created_at).toLocaleString("en-GB", {
                      dateStyle: "medium",
                      timeStyle: "short",
                    })}
                  </span>
                  <p>{m.body}</p>
                </article>
              ))
            )}
          </div>
          {a.stage === "withdrawn" ? (
            <p className="auth-notice">
              This application was withdrawn. Messages are read-only.
            </p>
          ) : (
            <MessageForm applicationId={id} />
          )}
        </section>
      </div>
    </div>
  );
}
