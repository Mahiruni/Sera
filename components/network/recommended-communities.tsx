import Link from "next/link";
import { UsersRound, ArrowRight } from "lucide-react";
import { requiredProfile } from "@/lib/auth/profile";
import { serverClient } from "@/lib/supabase/server";
import { revalidatePath } from "next/cache";
import { recommendCommunities,relevantSpaces,type MatchProfile,type CommunityCandidate,type CollaborationCandidate } from "@/lib/network/recommendations";

async function joinRecommended(form:FormData){
 "use server";
 await requiredProfile("/discover");
 const id=String(form.get("id")||"");
 if(!/^[0-9a-f-]{36}$/i.test(id))return;
 const db=await serverClient();
 const {error}=await db.rpc("sera_join_community",{p_id:id,p_join:true});
 if(error)throw new Error("Unable to join this community. Please try again.");
 revalidatePath("/discover");revalidatePath("/communities");
}
export async function RecommendedCommunities({profile}:{profile:MatchProfile|null}){
 if(!profile)return null;
 const db=await serverClient();
 const [directory,spaces]=await Promise.all([db.rpc("sera_community_directory",{p_query:"",p_topic:""}),db.rpc("sera_list_spaces")]);
 const groups=directory.error?[]:recommendCommunities(profile,(directory.data||[]) as CommunityCandidate[]);
 // Spaces are invitation-only: never search the underlying private table.
 const teams=spaces.error?[]:relevantSpaces(profile,(spaces.data||[]) as CollaborationCandidate[]);
 if(!groups.length&&!teams.length)return directory.error||spaces.error?<p role="status" className="network-search-caption">Some community recommendations are temporarily unavailable.</p>:null;
 return <section className="network-universal-section" aria-label="Recommended communities and collaboration">
  <header className="network-universal-section-head"><h2><UsersRound size={18} aria-hidden="true"/> Communities for you</h2><Link href="/communities" className="network-text-link">Explore communities <ArrowRight size={15}/></Link></header>
  <p className="network-search-caption">Meet professionals who share your skills and career interests.</p>
  <div className="network-universal-cards">{groups.map(({group,matchedSkills,matchedRoles})=><article key={group.id}>
   <Link className="network-universal-preview" href={"/communities?q="+encodeURIComponent(group.name)}><span className="network-universal-copy"><strong>{group.name}</strong><small>{group.topic} · {group.member_count} members</small><span className="network-universal-snippet">{group.description}</span></span><ArrowRight size={17} aria-hidden="true"/></Link>
   <p className="network-search-caption">Matches: {[...new Set([...matchedSkills,...matchedRoles])].slice(0,4).join(", ")}</p>
   <form action={joinRecommended}><input type="hidden" name="id" value={group.id}/><button type="submit" className="network-button">Join community</button></form>
  </article>)}</div>
  {!!teams.length&&<><header className="network-universal-section-head"><h2>Your relevant collaboration spaces</h2><Link href="/collaboration" className="network-text-link">All spaces</Link></header><div className="network-universal-cards">{teams.map(({space,matches})=><article key={space.id}><Link className="network-universal-preview" href={"/collaboration?space="+space.id}><span className="network-universal-copy"><strong>{space.name}</strong><small>{space.status==="invited"?"Pending invitation":"Your team"}</small><span className="network-universal-snippet">{space.description}</span></span><ArrowRight size={17}/></Link><p className="network-search-caption">Matches: {matches.slice(0,4).join(", ")}</p></article>)}</div></>}
  {(directory.error||spaces.error)&&<p role="status" className="network-search-caption">Some recommendations could not be loaded.</p>}
 </section>;
}
