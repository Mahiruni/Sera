import test from "node:test";
import assert from "node:assert/strict";
import {VideoAdaptation,authorizedVideoQuality,videoConstraints,videoProfiles} from "../lib/network/video-policy";
test("video entitlement only permits the authorized ceiling",()=>{
 assert.equal(authorizedVideoQuality("hd",false),"standard");
 assert.equal(authorizedVideoQuality("super_hd",false),"standard");
 assert.equal(authorizedVideoQuality("hd",true),"hd");
 assert.equal(authorizedVideoQuality("super_hd",true),"super_hd");
 assert.deepEqual(videoConstraints("data_saver").height,{ideal:360,max:360});
 assert.deepEqual(videoConstraints("standard").height,{ideal:480,max:480});
});
test("video reduces quality quickly and restores it slowly without exceeding the ceiling",()=>{
 const v=new VideoAdaptation();const good={outgoingBps:5000000,loss:.01,rttSeconds:.1};
 assert.equal(v.level,"data_saver");
 for(let i=0;i<12;i++)v.update("standard",good);
 assert.equal(v.level,"standard");
 for(let i=0;i<12;i++)v.update("super_hd",good);
 assert.equal(v.level,"super_hd");
 assert.equal(v.update("standard",good),"standard");
 assert.equal(v.update("super_hd",{outgoingBps:250000,loss:.1}),"data_saver");
 assert.equal(v.update("super_hd",{outgoingBps:90000,loss:.2}),"paused");
 assert.equal(v.update("super_hd",good),"paused");
 assert.equal(v.update("super_hd",good),"paused");
 assert.equal(v.update("super_hd",good),"data_saver");
 for(let i=0;i<12;i++)v.update("super_hd",good,true);
 assert.equal(v.level,"data_saver");
 assert.equal(videoProfiles.paused.bitrate,0);
});
test("missing or invalid network measurements cannot silently unlock HD",()=>{
 const v=new VideoAdaptation();
 for(let i=0;i<20;i++)v.update("super_hd",{outgoingBps:NaN,loss:NaN,rttSeconds:-1});
 assert.equal(v.level,"data_saver");
 assert.equal(v.update("standard",{outgoingBps:0}),"paused");
});
