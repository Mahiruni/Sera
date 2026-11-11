import { requiredProfile } from "@/lib/auth/profile";
import { serverClient } from "@/lib/supabase/server";
import { reviewReport } from "@/lib/moderation/actions";
import { pageMetadata } from "@/lib/seo";
export const metadata = pageMetadata("/moderation", "Trust & Safety review", "Restricted moderation console");
type Report = { id: string; post_id: string; reason: string; details: string; created_at: string };
export default async function ModerationPage() {
  await requiredProfile("/moderation");
  const db = await serverClient();
  const { data, error } = await db.rpc("moderator_pending_reports");
  if (error) return <main className="container page"><h1>Access unavailable</h1><p>Moderator review is currently unavailable.</p></main>;
  const reports = (data || []) as Report[];
  return <main className="container page account-foundation">
    <header className="account-security panel"><h1>SERA Trust & Safety</h1><p>Restricted review queue. Every decision requires a written explanation and is recorded for audit.</p></header>
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
  </main>;
}
