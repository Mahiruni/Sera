import { requiredProfile } from "@/lib/auth/profile";
import { PageIntro } from "@/components/page-intro";
import { ShieldCheck } from "lucide-react";
import { serverClient } from "@/lib/supabase/server";
import { reviewReport, reviewAppeal } from "@/lib/moderation/actions";
import { pageMetadata } from "@/lib/seo";
export const metadata = pageMetadata("/moderation", "Trust & Safety review", "Restricted moderation console");
type Report = { id: string; post_id: string; reason: string; details: string; created_at: string };
export default async function ModerationPage() {
  await requiredProfile("/moderation");
  const db = await serverClient();
  const { data, error } = await db.rpc("moderator_pending_reports");
  if (error) return <main className="container page"><h1>Access unavailable</h1><p>Moderator review is currently unavailable.</p></main>;
  const reports = (data || []) as Report[];
  const { data: appealRows } = await db.rpc("moderator_pending_appeals");
  const appeals = (appealRows || []) as {id:string;decision_id:string;post_id:string;explanation:string;created_at:string}[];
  return <div className="container page account-foundation">
    <PageIntro icon={ShieldCheck} title="Trust & Safety"
      description="Review reports and appeals. Every decision is explained and recorded." />
    <section className="account-security panel" aria-labelledby="queue-title">
      <h2 id="queue-title">Pending reports ({reports.length})</h2>
      {!reports.length && <p>No pending reports, or your account has no moderator access.</p>}
      {reports.map(report => <article key={report.id} style={{borderBottom:"1px solid #dce7e9",padding:"18px 0"}}>
        <h3>{report.reason}</h3><p>{report.details || "No additional details provided."}</p>
        <p><small>Post ID: {report.post_id} · {new Date(report.created_at).toLocaleDateString("en-GB")}</small></p>
        <form action={reviewReport} className="sera-visibility-form">
          <input type="hidden" name="report_id" value={report.id}/>
          <label htmlFor={"decision-"+report.id}>Decision</label>
          <select name="action" id={"decision-"+report.id} required defaultValue="">
            <option value="" disabled>Select a decision</option>
            <option value="dismiss">Dismiss report</option>
            <option value="quarantine">Quarantine post</option>
            <option value="remove">Remove post from public view</option>
            <option value="restore">Restore quarantined post</option>
          </select>
          <label htmlFor={"reason-"+report.id}>Explanation (minimum 10 characters)</label>
          <textarea id={"reason-"+report.id} name="explanation" minLength={10} maxLength={2000} required rows={3}/>
          <button className="button" type="submit">Record decision</button>
        </form>
      </article>)}
    </section>
    <section className="account-security panel" aria-labelledby="appeal-queue-title">
      <h2 id="appeal-queue-title">Appeals ({appeals.length})</h2>
      {!appeals.length && <p>No pending appeals.</p>}
      {appeals.map(appeal => <article key={appeal.id} style={{borderBottom:"1px solid #dce7e9",padding:"18px 0"}}>
        <h3>Appeal for post {appeal.post_id}</h3><p>{appeal.explanation}</p>
        <form action={reviewAppeal} className="sera-visibility-form">
          <input type="hidden" name="appeal_id" value={appeal.id}/>
          <label htmlFor={"appeal-decision-"+appeal.id}>Appeal outcome</label>
          <select id={"appeal-decision-"+appeal.id} name="decision" required defaultValue="">
            <option value="" disabled>Choose outcome</option><option value="accept">Accept and restore</option><option value="reject">Reject appeal</option>
          </select>
          <label htmlFor={"appeal-explanation-"+appeal.id}>Decision explanation</label>
          <textarea id={"appeal-explanation-"+appeal.id} name="explanation" minLength={10} maxLength={2000} rows={3} required/>
          <button className="button" type="submit">Resolve appeal</button>
        </form>
      </article>)}
    </section>
  </div>;
}
