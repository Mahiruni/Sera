import Link from "next/link";
import { Sparkles } from "lucide-react";
import { serverClient } from "@/lib/supabase/server";
import { OpportunityPreview } from "@/components/network/shared";
import { matchTerms,recommendJobs,type MatchProfile } from "@/lib/network/recommendations";
export async function PersonalizedDiscovery({profile}:{profile:MatchProfile|null}){
 if(!profile||(!matchTerms(profile.skills).length&&!matchTerms(profile.preferred_roles).length))return <section className="network-universal-section"><header className="network-universal-section-head"><h2>Make discovery yours</h2></header><p>Add your skills and preferred roles to discover relevant opportunities.</p><Link href="/profile" className="network-button">Update your profile</Link></section>;
 const db=await serverClient();
 const {data,error}=await db.from("jobs").select("id,title,company,location,work_type,description,requirements").eq("status","open").order("created_at",{ascending:false}).limit(100);
 if(error)return <p role="status" className="network-search-caption">Personalized opportunities are temporarily unavailable.</p>;
 const matches=recommendJobs(profile,data||[]);
 return <section className="network-universal-section"><header className="network-universal-section-head"><h2><Sparkles size={18} aria-hidden="true"/> Recommended for you</h2><Link href="/discover?tab=jobs" className="network-text-link">All jobs</Link></header><p className="network-search-caption">Based on your profile skills and preferred roles.</p>{matches.length? <div className="network-universal-cards">{matches.map(({job,matchedSkills,matchedRoles,sameLocation})=><div key={job.id}><OpportunityPreview job={job}/><p className="network-search-caption">Matches: {[...new Set([...matchedRoles,...matchedSkills])].slice(0,4).join(", ")}{sameLocation?" · Your location":""}</p></div>)}</div>:<p>No matching open roles in recent listings yet. Explore all jobs or update your profile.</p>}</section>;
}
