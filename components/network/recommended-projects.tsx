import Link from "next/link";
import { FolderKanban,ArrowRight } from "lucide-react";
import { serverClient } from "@/lib/supabase/server";
import { memberCards } from "@/lib/activity/members";
import { MemberAvatar } from "@/components/activity/avatar";
import { matchTerms,recommendProjects,type MatchProfile,type ProjectCandidate } from "@/lib/network/recommendations";

export async function RecommendedProjects({profile,userId}:{profile:MatchProfile|null;userId:string}){
 if(!profile||(!matchTerms(profile.skills).length&&!matchTerms(profile.preferred_roles).length))return null;
 const db=await serverClient();
 // Recommendations use only public, currently published original projects.
 // The ordinary user-scoped client also enforces post and block visibility.
 const {data,error}=await db.from("posts").select("id,author_id,title,body,category,skills,published_at,audience,repost_of")
  .eq("status","published").eq("kind","project").eq("audience","public").is("deleted_at",null).is("repost_of",null).neq("author_id",userId)
  .order("published_at",{ascending:false}).order("id").limit(100);
 if(error)return <p role="status" className="network-search-caption">Project recommendations are temporarily unavailable.</p>;
 const matches=recommendProjects(profile,(data||[]) as ProjectCandidate[],userId);
 if(!matches.length)return null;
 const authors=await memberCards(matches.map(r=>r.project.author_id));
 return <section className="network-universal-section" aria-label="Recommended projects">
  <header className="network-universal-section-head"><h2><FolderKanban size={18} aria-hidden="true"/> Projects for you</h2><Link href="/discover?tab=projects" className="network-text-link">All projects <ArrowRight size={15}/></Link></header>
  <p className="network-search-caption">Explore published work aligned with your skills and career interests.</p>
  <div className="network-universal-cards">{matches.map(({project,matchedSkills,matchedRoles})=>{
   const author=authors.get(project.author_id);
   const reasons=[...new Set([...matchedSkills,...matchedRoles])].slice(0,4);
   return <article key={project.id}>
    <Link className="network-universal-preview" href={"/posts/"+project.id}>
     <span className="network-person-avatar"><MemberAvatar name={author?.name||"SERA member"} photo={author?.photo}/></span>
     <span className="network-universal-copy"><strong>{project.title||"Professional project"}</strong><small>{author?.name||"SERA member"}{project.category?" · "+project.category:""}</small>
     {project.body&&<span className="network-universal-snippet">{project.body.slice(0,160)}</span>}</span><ArrowRight size={17} aria-hidden="true"/>
    </Link>
    <p className="network-search-caption">Matches: {reasons.join(", ")}</p>
   </article>;
  })}</div>
 </section>;
}
