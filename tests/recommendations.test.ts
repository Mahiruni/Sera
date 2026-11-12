import test from "node:test";
import assert from "node:assert/strict";
import {recommendJobs,matchTerms} from "../lib/network/recommendations";
const job=(id:string,title:string,description:string)=>({id,title,description,company:"Example",location:"Addis Ababa",work_type:"Full-time"});
test("recommendations rank explicit role and skill matches without location-only recommendations",()=>{
 const rows=recommendJobs({skills:"React, SQL",preferred_roles:"Software Engineer",location:"Addis Ababa"},[job("a","Software Engineer","React and SQL"),job("b","Developer","React"),job("c","Accountant","Finance")]);
 assert.deepEqual(rows.map(r=>r.job.id),["a","b"]);assert.deepEqual(rows[0].matchedSkills,["react","sql"]);
});
test("skill matching avoids substring false positives and duplicate terms",()=>{
 assert.deepEqual(matchTerms("React, react; SQL"),["react","sql"]);
 assert.equal(recommendJobs({skills:"Java"},[job("a","Developer","JavaScript")]).length,0);
 assert.equal(recommendJobs({skills:"C++"},[job("a","Developer","C++")]).length,1);
});

test("professional recommendations exclude self, existing relationships and duplicates",async()=>{
 const {recommendProfessionals}=await import("../lib/network/recommendations");
 const person=(id:string,skills:string)=>({member_id:id,full_name:id,headline:"Engineer",matched_skills:skills,showcase_title:"Project"});
 const candidates=[person("self","React, SQL"),person("connected","React, SQL"),person("a","React"),person("b","React, SQL"),person("a","React"),person("none","")];
 assert.deepEqual(recommendProfessionals(candidates,"self",new Set(["connected"])).map(p=>p.member_id),["b","a"]);
});

test("project recommendations prioritize explicit tags and exclude self, private and reposted content",async()=>{
 const {recommendProjects}=await import("../lib/network/recommendations");
 const project=(id:string,overrides:Partial<import("../lib/network/recommendations").ProjectCandidate>={})=>({id,author_id:"other",title:"Dashboard",body:"React application",category:"Technology",skills:[],published_at:"2026-11-12T00:00:00Z",audience:"public",repost_of:null,...overrides});
 const rows=recommendProjects({skills:"React, SQL"},[project("a"),project("b",{skills:["React","SQL"]}),project("self",{author_id:"self",skills:["React"]}),project("private",{audience:"only_me",skills:["React"]}),project("connections",{audience:"connections",skills:["React"]}),project("repost",{repost_of:"a"}),project("unrelated",{body:"Accounting",skills:[]}),project("a")],"self");
 assert.deepEqual(rows.map(r=>r.project.id),["b","a"]);
 assert.deepEqual(rows[0].matchedSkills,["react","sql"]);
});
test("projects support exact skill punctuation, recency ties and empty profiles",async()=>{
 const {recommendProjects}=await import("../lib/network/recommendations");
 const project=(id:string,body:string,published_at:string)=>({id,author_id:"other",title:"Demo",body,category:"Engineering",skills:[],published_at,audience:"public"});
 const rows=[project("old","Built with C++","2026-11-01"),project("new","C++ project","2026-11-12"),project("other","JavaScript","2026-11-12")];
 assert.deepEqual(recommendProjects({skills:"C++"},rows,"self").map(r=>r.project.id),["new","old"]);
 assert.equal(recommendProjects({skills:"Java"},rows,"self").length,0);
 assert.equal(recommendProjects({},rows,"self").length,0);
});

test("community recommendations exclude memberships and rank relevant skills",async()=>{
 const {recommendCommunities}=await import("../lib/network/recommendations");
 const group=(id:string,description:string,joined=false)=>({id,name:id,description,topic:"technology",member_count:10,joined,is_owner:false});
 assert.deepEqual(recommendCommunities({skills:"React, SQL"},[group("a","React"),group("b","React and SQL"),group("c","React",true),group("d","Accounting"),group("a","React")]).map(r=>r.group.id),["b","a"]);
});
test("collaboration recommendations only include active or invited spaces",async()=>{
 const {relevantSpaces}=await import("../lib/network/recommendations");
 const space=(id:string,status:string)=>({id,name:"React team",description:"SQL",status});
 assert.deepEqual(relevantSpaces({skills:"React"},[space("a","active"),space("b","invited"),space("c","declined"),space("d","private")]).map(r=>r.space.id),["a","b"]);
});
