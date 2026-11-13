import test from "node:test";
import assert from "node:assert/strict";
import {SpeechAdaptation,speechProfiles,readSpeechLink,speechJitterTargetMs,speechQualityWarning} from "../lib/network/voice-network-policy";
test("weak network drops bitrate quickly and recovers gradually",()=>{
 const p=new SpeechAdaptation();assert.equal(speechProfiles[p.profile],20000);
 assert.equal(p.update({loss:.15}),"resilient");
 for(let i=0;i<2;i++)assert.equal(p.update({loss:.01,rttSeconds:.15,outgoingBps:120000}),"resilient");
 assert.equal(p.update({loss:.01,rttSeconds:.15,outgoingBps:120000}),"balanced");
 for(let i=0;i<3;i++)p.update({loss:.01,rttSeconds:.15,outgoingBps:120000});
 assert.equal(p.profile,"clear");
 assert.equal(p.update({loss:.06}),"balanced");
 assert.equal(p.update({outgoingBps:35000}),"resilient");
 for(let i=0;i<8;i++)p.update({loss:NaN,rttSeconds:-1,outgoingBps:0});
 assert.equal(p.profile,"resilient");
});
test("uses remote audio reception and selected candidate only",()=>{
 const {link,remoteTimestamp}=readSpeechLink([
  {id:"video",type:"remote-inbound-rtp",kind:"video",fractionLost:.5},
  {id:"audio",type:"remote-inbound-rtp",kind:"audio",fractionLost:.1,roundTripTime:.25,timestamp:123},
  {id:"transport",type:"transport",selectedCandidatePairId:"selected"},
  {id:"other",type:"candidate-pair",availableOutgoingBitrate:9999999},
  {id:"selected",type:"candidate-pair",availableOutgoingBitrate:35000}
 ]);
 assert.deepEqual(link,{loss:.1,rttSeconds:.25,outgoingBps:35000});
 assert.equal(remoteTimestamp,123);
});

test("jitter hints remain bounded and warnings require measured impairment",()=>{
 assert.equal(speechJitterTargetMs(undefined),undefined);assert.equal(speechJitterTargetMs(NaN),undefined);
 assert.equal(speechJitterTargetMs(.005),40);assert.equal(speechJitterTargetMs(.035),80);assert.equal(speechJitterTargetMs(.2),120);
 assert.equal(speechQualityWarning(undefined,undefined,undefined),false);
 assert.equal(speechQualityWarning(.02,.01,.2),false);
 assert.equal(speechQualityWarning(.09,undefined,undefined),true);
 assert.equal(speechQualityWarning(undefined,.08,undefined),true);
 assert.equal(speechQualityWarning(undefined,undefined,.9),true);
});
