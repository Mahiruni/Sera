import Link from "next/link";
import { PageIntro } from "@/components/page-intro";
import { ShieldCheck, ArrowLeft } from "lucide-react";
import { submitModerationAppeal } from "@/lib/moderation/appeals";
import { requiredProfile } from "@/lib/auth/profile";
import { serverClient } from "@/lib/supabase/server";
import { pageMetadata } from "@/lib/seo";

export const metadata = pageMetadata("/settings/safety", "Account safety", "Review your private community safety reports.");

const labels: Record<string,string> = {
 harassment: "Harassment", hate: "Hate speech", sexual: "Sexual content",
 fraud: "Fraud or scam", spam: "Spam", violence: "Violence or threats",
 impersonation: "Impersonation", privacy: "Privacy violation", other: "Other",
};

export default async function SafetyPage() {
  const { user } = await requiredProfile();
  const db = await serverClient();
  const { data: reports, error } = await db.from("moderation_reports")
    .select("id,post_id,reason,status,created_at")
    .eq("reporter_id", user.id)
    .order("created_at", { ascending: false })
    .limit(50);
  const { data: decisions, error: decisionsError } = await db.rpc("my_moderation_decisions");
  const { data: appeals } = await db.from("moderation_appeals").select("decision_id,status").eq("appellant_id",user.id);
  const appealMap = new Map((appeals || []).map(a => [a.decision_id,a.status]));
  return <div className="container page account-foundation">
    <PageIntro icon={ShieldCheck} title="Account safety"
      description="Review your private reports, post decisions and appeals.">
      <Link className="button secondary" href="/settings"><ArrowLeft size={16} /> Your account</Link>
    </PageIntro>
    <section className="account-security panel" aria-labelledby="safety-decisions-title">
      <h2 id="safety-decisions-title">Decisions on your posts</h2>
      {decisionsError ? <p role="alert">Unable to load moderation decisions.</p> : !decisions?.length ? <p>No moderation decisions on your posts.</p> : decisions.map((d: { id: string; action: string; explanation: string | null; policy: string; created_at: string }) => <article key={d.id} style={{padding:"16px 0",borderBottom:"1px solid #dce7e9"}}>
        <h3>{d.action === "quarantine" ? "Post temporarily hidden" : d.action === "remove" ? "Post removed from public view" : d.action === "restore" ? "Post restored" : "Post reviewed"}</h3>
        <p>{d.explanation}</p><small>Policy: {d.policy} · {new Date(d.created_at).toLocaleDateString("en-GB")}</small>
        {(d.action === "quarantine" || d.action === "remove") && (appealMap.has(d.id) ? <p>Appeal: {appealMap.get(d.id)}</p> : <form action={submitModerationAppeal} className="sera-visibility-form">
          <input type="hidden" name="decision_id" value={d.id}/>
          <label htmlFor={"appeal-"+d.id}>Request another review</label>
          <textarea id={"appeal-"+d.id} name="explanation" minLength={20} maxLength={2000} rows={3} required placeholder="Explain why this decision should be reconsidered."/>
          <button className="button" type="submit">Submit appeal</button>
        </form>)}
      </article>)}
    </section>
    <section className="account-security panel" aria-labelledby="safety-reports-title">
      <h2 id="safety-reports-title">Your reports</h2>
      {error ? <p role="alert">Unable to load reports right now.</p> :
      !reports?.length ? <p>You haven't submitted any reports.</p> :
      <ul style={{ listStyle: "none", padding: 0, margin: 0 }}>
        {reports.map(report => <li key={report.id} style={{ padding: "16px 0", borderBottom: "1px solid #dce7e9" }}>
          <strong>{labels[report.reason] || "Community report"}</strong>
          <p style={{ margin: "4px 0" }}>Status: {report.status === "pending" ? "Awaiting review" : report.status === "reviewing" ? "Under review" : report.status === "resolved" ? "Resolved" : "Closed without action"}</p>
          <small>{new Date(report.created_at).toLocaleDateString("en-GB", { dateStyle: "medium" })}</small>
        </li>)}
      </ul>}
    </section>
  </div>;
}
