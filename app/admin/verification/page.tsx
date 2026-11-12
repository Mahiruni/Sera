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
 return <main className="sera-identity-page container page"><Link href="/admin/moderation">← Moderation</Link><header><h1>Identity review</h1><p>Confidential evidence. Compare the document to the professional name. Do not approve solely on email confirmation or an unverified claim.</p></header>{error&&<p role="alert">Review queue unavailable.</p>}{!error&&!rows.length&&<p>No requests awaiting review.</p>}{await Promise.all(rows.map(async r=>{const evidence=r.status==="pending"?await db.storage.from("sera-identity-private").createSignedUrl(r.path,60):null;return <section key={r.id}><h2>{r.verified_name}</h2><p>{r.document_type.replaceAll("_"," ")} · {r.status}</p><Link href={"/members/"+r.user_id}>View professional profile</Link>{r.status==="pending"&&(evidence?.data?.signedUrl?<a href={evidence.data.signedUrl} target="_blank" rel="noreferrer">Open private evidence (60 seconds)</a>:<p role="alert">Evidence unavailable. Do not approve.</p>)}<IdentityReview id={r.id} status={r.status}/></section>}))}</main>
}
