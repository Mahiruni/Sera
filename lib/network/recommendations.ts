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
