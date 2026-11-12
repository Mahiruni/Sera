import Link from "next/link";
import { UsersRound } from "lucide-react";
import { serverClient } from "@/lib/supabase/server";
import { memberCards } from "@/lib/activity/members";
import { MemberPreview } from "@/components/network/shared";
import { changeProfessionalRelationship } from "@/lib/network/relationships";
import { recommendProfessionals,type ProfessionalCandidate } from "@/lib/network/recommendations";

export async function RecommendedProfessionals({userId}:{userId:string}){
 const db=await serverClient();
 const [talent,relations,follows]=await Promise.all([
  db.rpc("sera_recommend_talent"),
  db.from("professional_connection_requests").select("requester_id,recipient_id,status").or("requester_id.eq."+userId+",recipient_id.eq."+userId),
  db.from("professional_follows").select("followed_id").eq("follower_id",userId),
 ]);
 if(talent.error||relations.error||follows.error)return <p role="status" className="network-search-caption">Professional recommendations are temporarily unavailable.</p>;
 const excluded=new Set<string>((follows.data||[]).map(r=>r.followed_id));
 for(const r of relations.data||[])excluded.add(r.requester_id===userId?r.recipient_id:r.requester_id);
 const matches=recommendProfessionals((talent.data||[]) as ProfessionalCandidate[],userId,excluded);
 if(!matches.length)return null;
 const cards=await memberCards(matches.map(p=>p.member_id));
 return <section className="network-universal-section" aria-label="Recommended professionals">
  <header className="network-universal-section-head"><h2><UsersRound size={18} aria-hidden="true"/> People to connect with</h2><Link href="/talent" className="network-text-link">Explore talent</Link></header>
  <p className="network-search-caption">Professionals with published portfolios and skills in common with you.</p>
  <div className="network-universal-cards">{matches.map(p=><article key={p.member_id}>
    <MemberPreview member={{id:p.member_id,full_name:p.full_name,headline:p.headline,location:"",photo:cards.get(p.member_id)?.photo}}/>
    <p className="network-search-caption">Shared skills: {p.matched_skills}</p>
    {p.showcase_title&&<p className="network-search-caption">Featured work: {p.showcase_title}</p>}
    <form action={changeProfessionalRelationship}><input type="hidden" name="target" value={p.member_id}/><input type="hidden" name="action" value="request"/><button className="network-button" type="submit">Connect</button></form>
  </article>)}</div>
 </section>;
}
