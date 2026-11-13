import test from "node:test";
import assert from "node:assert/strict";
import {voiceText} from "../lib/network/voice-i18n";
test("voice controls, lifecycle and summary are available in all three languages",()=>{
 for(const label of ["Voice call","Answer","Decline","Mute","End call","Connected","Reconnecting…","Call ended","Call again","Recent calls","Finding secure connection…"]){
   assert.equal(voiceText("en",label),label);
   assert.notEqual(voiceText("am",label),label);
   assert.notEqual(voiceText("om",label),label);
 }
 assert.equal(voiceText("om","01:23"),"01:23");
 assert.equal(voiceText("am","unknown diagnostic"),"unknown diagnostic");
});
