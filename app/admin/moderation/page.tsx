import { notFound } from "next/navigation";
import { requiredProfile } from "@/lib/auth/profile";
import { serverClient } from "@/lib/supabase/server";
import { reviewProfessionalReport } from "@/lib/network/moderation";
export const metadata = { title: "Trust & safety moderation | SERA.et", robots: { index: false, follow: false } };
export default async function ModerationQueue({searchParams}:{
  searchParams:Promise<{status?:string}>
}) {
  const { user } = await requiredProfile("/admin/moderation");
  if (!["admin","moderator"].includes(String(user.app_metadata?.role ?? ""))) notFound();
  const status=(await searchParams).status;
  const db=await serverClient();
  let query=db.from("professional_reports").select("id,reporter_id,reported_id,reason,details,created_at,status,moderator_note,reviewed_at")
    .order("created_at",{ascending:false}).limit(100);
  if (status && ["open","reviewing","resolved","dismissed"].includes(status)) query=query.eq("status",status);
  const {data:reports,error}=await query;
  return <main className="sera-network network-app" style={{maxWidth:1050,margin:"auto",padding:"28px 16px 120px"}}>
    <p className="network-kicker">TRUST & SAFETY</p>
    <h1 style={{fontSize:"clamp(2rem,5vw,3rem)",letterSpacing:"-.04em"}}>Moderation workspace</h1>
    <p>Confidential member reports. Review evidence carefully and document every decision.</p>
    <nav aria-label="Report filters" style={{display:"flex",gap:10,flexWrap:"wrap",margin:"20px 0"}}>
      {["all","open","reviewing","resolved","dismissed"].map(s=><a key={s} href={s==="all"?"/admin/moderation":"/admin/moderation?status="+s} style={{border:"1px solid #b7dbe3",borderRadius:20,padding:"8px 14px",color:"#11617a"}}>{s[0].toUpperCase()+s.slice(1)}</a>)}
    </nav>
    {error&&<p role="alert">Reports are temporarily unavailable.</p>}
    <div style={{display:"grid",gap:16}}>
      {(reports??[]).map(r=><article key={r.id} style={{border:"1px solid #cce4ea",borderRadius:20,padding:20,background:"#fff",color:"#153c4c"}}>
        <div style={{display:"flex",justifyContent:"space-between",flexWrap:"wrap",gap:8}}>
          <strong>{r.reason} · {r.status}</strong><time dateTime={r.created_at}>{new Date(r.created_at).toLocaleString()}</time>
        </div>
        <p style={{fontSize:13}}>Reported member: {r.reported_id}</p>
        <p style={{fontSize:13}}>Reporter: {r.reporter_id}</p>
        <p style={{whiteSpace:"pre-wrap"}}>{r.details||"No additional context provided."}</p>
        <form action={reviewProfessionalReport} style={{display:"grid",gap:10}}>
          <input type="hidden" name="id" value={r.id}/>
          <label htmlFor={"status-"+r.id}>Review decision</label>
          <select id={"status-"+r.id} name="status" defaultValue={r.status==="open"?"reviewing":r.status} style={{padding:12,borderRadius:12,border:"1px solid #b7dbe3"}}>
            <option value="reviewing">Under review</option><option value="resolved">Resolved</option><option value="dismissed">Dismissed</option>
          </select>
          <label htmlFor={"note-"+r.id}>Internal review notes</label>
          <textarea id={"note-"+r.id} name="note" maxLength={2000} rows={3} defaultValue={r.moderator_note??""} style={{padding:12,borderRadius:12,border:"1px solid #b7dbe3"}}/>
          <button type="submit" className="network-button">Save review</button>
        </form>
      </article>)}
      {!error&&!(reports??[]).length&&<p>No reports in this queue.</p>}
    </div>
  </main>;
}
