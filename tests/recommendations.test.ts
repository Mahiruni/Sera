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
