import test from "node:test";
import assert from "node:assert/strict";
import {parseActivityRead,activityTargetMatches} from "../lib/network/activity-read-model";
const owner="11111111-1111-4111-8111-111111111111",target="22222222-2222-4222-8222-222222222222",event="33333333-3333-4333-8333-333333333333",other="44444444-4444-4444-8444-444444444444";
test("activity opens validate exact target and recipient",()=>{
 const input=parseActivityRead({owner,target,event,kind:"space_message"})!;
 const row={id:event,recipient_id:owner,kind:"space_message",space_id:target,conversation_id:null};
 assert.equal(activityTargetMatches(input,row),true);
 assert.equal(activityTargetMatches(input,{...row,id:other}),false);
 assert.equal(activityTargetMatches(input,{...row,recipient_id:other}),false);
 assert.equal(activityTargetMatches(input,{...row,space_id:other}),false);
 assert.equal(parseActivityRead({owner,target,event,kind:"message"}),null);
 assert.equal(parseActivityRead({owner,target:"../admin",event,kind:"missed_call"}),null);
});
