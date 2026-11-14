import test from "node:test";
import assert from "node:assert/strict";
import {publicFeedPreviews} from "../lib/social/public-feed-cache";
test("public feed cache excludes restricted, unknown and reposted content",()=>{
 const p={id:"a",audience:"public",title:"title",body:"body",published_at:"2026-11-14",author:{full_name:"Member",headline:"Engineer"}};
 const result=publicFeedPreviews([p,{...p,id:"b",audience:"connections"},{...p,id:"c",audience:undefined},{...p,id:"d",repost_of:"private"}]);
 assert.deepEqual(result.map(x=>x.id),["a"]);
 assert.equal(result[0].body,"body");
});
test("public preview cache bounds text and count",()=>{
 const p={id:"a",audience:"public",title:"title",body:"x".repeat(5000),published_at:"2026-11-14",author:{full_name:"Member",headline:"Engineer"}};
 const rows=publicFeedPreviews(Array.from({length:30},(_,i)=>({...p,id:String(i)})));
 assert.equal(rows.length,20);assert.equal(rows[0].body.length,4000);
});
