import Link from "next/link";
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
  return <main className="container page account-foundation">
    <div className="account-security panel">
      <Link className="inline-link" href="/settings">← Account settings</Link>
      <h1>Account safety</h1>
      <p>Reports are private. Reporting helps our team review possible guideline violations; a report does not automatically remove a post.</p>
    </div>
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
  </main>;
}
