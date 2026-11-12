import Link from "next/link";
import {notFound} from "next/navigation";
import {requiredProfile} from "@/lib/auth/profile";
import {serverClient} from "@/lib/supabase/server";
import {IdentityReview} from "@/components/identity/verification-form";
import "@/app/identity-verification.css";
export const metadata={title:"Identity review | SERA.et",robots:{index:false,follow:false}};
export default async function IdentityQueue(){
 const {user}=await requiredProfile("/admin/verification");if(!["admin","moderator"].includes(String(user.app_metadata?.role)))notFound();
 const db=await serverClient();const {data,error}=await db.rpc("sera_identity_queue");
 const rows=(data||[]) as {id:string;user_id:string;verified_name:string;document_type:string;status:string;path:string;submitted_at:string}[];
 return <main className="sera-identity-page container page"><Link href="/admin/moderation">← Moderation</Link><header><h1>Identity review</h1><p>Confidential evidence. Check the document, name, authenticity and ownership evidence; request additional confirmation through the existing member contact process if needed. Do not approve solely on email confirmation or an unverified claim.</p></header>{error&&<p role="alert">Review queue unavailable.</p>}{!error&&!rows.length&&<p>No requests awaiting review.</p>}{rows.map(r=>{return <section key={r.id}><h2>{r.verified_name}</h2><p>{r.document_type.replaceAll("_"," ")} · {r.status}</p><Link href={"/members/"+r.user_id}>View professional profile</Link>{r.status==="pending"&&<a href={"/api/identity/evidence/"+r.id} target="_blank" rel="noreferrer">Download private evidence</a>}<IdentityReview id={r.id} status={r.status}/></section>})}</main>
}
