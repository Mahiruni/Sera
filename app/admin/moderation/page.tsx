import { notFound } from "next/navigation";
import { requiredProfile } from "@/lib/auth/profile";
import { serverClient } from "@/lib/supabase/server";
import { reviewProfessionalReport } from "@/lib/network/moderation";
import { memberCards } from "@/lib/activity/members";
import { MemberAvatar } from "@/components/activity/avatar";
import Link from "next/link";
export const metadata = { title: "Trust & safety moderation | SERA.et", robots: { index: false, follow: false } };
export default async function ModerationQueue({searchParams}:{
  searchParams:Promise<{status?:string}>
}) {
  const { user } = await requiredProfile("/admin/moderation");
  if (!["admin","moderator"].includes(String(user.app_metadata?.role ?? ""))) notFound();
  const status=(await searchParams).status;
  const db=await serverClient();
  let query=db.from("professional_reports").select("id,reporter_id,reported_id,reason,details,created_at,status,reviewed_at")
    .order("created_at",{ascending:false}).limit(100);
  if (status && ["open","reviewing","resolved","dismissed"].includes(status)) query=query.eq("status",status);
  const {data:reports,error}=await query;
  const {data:reviews}=(reports??[]).length ? await db.from("professional_report_reviews").select("report_id,note").in("report_id",(reports??[]).map(r=>r.id)) : {data:[]};
  const reviewNotes=new Map((reviews??[]).map(r=>[r.report_id,r.note]));
  const people=await memberCards((reports??[]).flatMap(r=>[r.reporter_id,r.reported_id]));
  return <main className="sera-network network-app sera-moderation">
    <p className="network-kicker">TRUST & SAFETY</p>
    <h1 style={{fontSize:"clamp(2rem,5vw,3rem)",letterSpacing:"-.04em"}}>Moderation workspace</h1>
    <p>Confidential member reports. Review evidence carefully and document every decision.</p>
    <nav aria-label="Report filters" className="sera-moderation-filters">
      {["all","open","reviewing","resolved","dismissed"].map(s=><a key={s} href={s==="all"?"/admin/moderation":"/admin/moderation?status="+s} className={(!status&&s==="all")||status===s?"is-active":""}>{s[0].toUpperCase()+s.slice(1)}</a>)}
    </nav>
    {error&&<p role="alert">Reports are temporarily unavailable.</p>}
    <div style={{display:"grid",gap:16}}>
      {(reports??[]).map(r=><article key={r.id} className="sera-moderation-report">
        <div style={{display:"flex",justifyContent:"space-between",flexWrap:"wrap",gap:8}}>
          <strong>{r.reason} · {r.status}</strong><time dateTime={r.created_at}>{new Date(r.created_at).toLocaleString()}</time>
        </div>
        <div className="sera-moderation-people"><div><MemberAvatar name={people.get(r.reported_id)?.name??"Reported member"} photo={people.get(r.reported_id)?.photo} size={36}/><span><small>Reported member</small><Link href={"/members/"+r.reported_id}>{people.get(r.reported_id)?.name??"View member"}</Link></span></div><div><MemberAvatar name={people.get(r.reporter_id)?.name??"Reporter"} photo={people.get(r.reporter_id)?.photo} size={36}/><span><small>Reported by</small><Link href={"/members/"+r.reporter_id}>{people.get(r.reporter_id)?.name??"View member"}</Link></span></div></div>
        <p style={{whiteSpace:"pre-wrap"}}>{r.details||"No additional context provided."}</p>
        <form action={reviewProfessionalReport} className="sera-moderation-form">
          <input type="hidden" name="id" value={r.id}/>
          <label htmlFor={"status-"+r.id}>Review decision</label>
          <select id={"status-"+r.id} name="status" defaultValue={r.status==="open"?"reviewing":r.status} style={{padding:12,borderRadius:12,border:"1px solid #b7dbe3"}}>
            <option value="reviewing">Under review</option><option value="resolved">Resolved</option><option value="dismissed">Dismissed</option>
          </select>
          <label htmlFor={"note-"+r.id}>Internal review notes</label>
          <textarea id={"note-"+r.id} name="note" maxLength={2000} rows={3} defaultValue={reviewNotes.get(r.id)??""} style={{padding:12,borderRadius:12,border:"1px solid #b7dbe3"}}/>
          <button type="submit" className="network-button">Save review</button>
        </form>
      </article>)}
      {!error&&!(reports??[]).length&&<p>No reports in this queue.</p>}
    </div>
  </main>;
}
