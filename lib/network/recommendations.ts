export type MatchProfile={skills?:string|null;preferred_roles?:string|null;location?:string|null};
export type MatchJob={id:string;title:string;company:string;location:string;work_type:string;description?:string|null;requirements?:string|null};
export function matchTerms(raw?:string|null){
 return [...new Set((raw||"").split(/[,;\n]/).map(t=>t.trim().toLocaleLowerCase()).filter(t=>t.length>=2&&t.length<=80))].slice(0,20);
}
function containsTerm(text:string,term:string){
 const escaped=term.replace(/[.*+?^${}()|[\]\\]/g,"\\$&");
 return new RegExp("(^|[^\\p{L}\\p{N}])"+escaped+"($|[^\\p{L}\\p{N}])","iu").test(text);
}
export function recommendJobs(profile:MatchProfile,jobs:MatchJob[]){
 const skills=matchTerms(profile.skills),roles=matchTerms(profile.preferred_roles);
 const location=(profile.location||"").trim().toLocaleLowerCase();
 return jobs.map(job=>{
  const title=job.title.toLocaleLowerCase(),text=[job.title,job.description,job.requirements].filter(Boolean).join(" ").toLocaleLowerCase();
  const matchedSkills=skills.filter(t=>containsTerm(text,t)),matchedRoles=roles.filter(t=>containsTerm(title,t));
  const sameLocation=!!location&&job.location.toLocaleLowerCase().includes(location);
  const score=matchedRoles.length*6+matchedSkills.length*3+(sameLocation?1:0);
  return {job,score,matchedSkills,matchedRoles,sameLocation};
 }).filter(r=>r.matchedSkills.length||r.matchedRoles.length).sort((a,b)=>b.score-a.score||a.job.id.localeCompare(b.job.id)).slice(0,5);
}

export type ProfessionalCandidate={member_id:string;full_name:string;headline:string;matched_skills:string;showcase_title:string};
export function recommendProfessionals<T extends ProfessionalCandidate>(candidates:T[],userId:string,excluded:ReadonlySet<string>){
 const seen=new Set<string>();
 return candidates.filter(p=>{
  if(!p.member_id||p.member_id===userId||excluded.has(p.member_id)||seen.has(p.member_id)||!matchTerms(p.matched_skills).length)return false;
  seen.add(p.member_id);return true;
 }).sort((a,b)=>matchTerms(b.matched_skills).length-matchTerms(a.matched_skills).length||a.member_id.localeCompare(b.member_id)).slice(0,5);
}

export type ProjectCandidate={id:string;author_id:string;title:string;body:string;category:string;skills:string[];published_at:string;audience?:string;repost_of?:string|null};
export function recommendProjects<T extends ProjectCandidate>(profile:MatchProfile,projects:T[],userId:string){
 const skills=matchTerms(profile.skills),roles=matchTerms(profile.preferred_roles);
 const seen=new Set<string>();
 return projects.flatMap(project=>{
  if(!project.id||project.author_id===userId||project.audience!=="public"||project.repost_of||seen.has(project.id))return [];
  seen.add(project.id);
  const tags=new Set((project.skills||[]).map(s=>s.trim().toLocaleLowerCase()));
  const title=project.title||"",text=[title,project.category,project.body].filter(Boolean).join(" ");
  const matchedTags=skills.filter(s=>tags.has(s));
  const matchedSkills=skills.filter(s=>tags.has(s)||containsTerm(text,s));
  const matchedRoles=roles.filter(s=>containsTerm(title+" "+(project.category||""),s));
  const score=matchedTags.length*5+(matchedSkills.length-matchedTags.length)*2+matchedRoles.length*3;
  return score?[{project,score,matchedSkills,matchedRoles}]:[];
 }).sort((a,b)=>b.score-a.score||b.project.published_at.localeCompare(a.project.published_at)||a.project.id.localeCompare(b.project.id)).slice(0,5);
}
