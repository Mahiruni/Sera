import test from "node:test";
import assert from "node:assert/strict";
import {professionalShare,locationShare,messageShareCards} from "../lib/network/share-card";
const id="f0caa111-0000-4000-8000-000000000001";
test("only approved SERA link destinations are canonicalized",()=>{assert.equal(professionalShare("https://sera.hisabtech.com/members/"+id)?.label,"Professional profile");for(const url of ["https://evil.example/members/"+id,"https://sera.hisabtech.com.evil.example/members/"+id,"https://sera.hisabtech.com/members/"+id+"?private=1","https://sera.hisabtech.com/api/account/files?kind=cv","javascript:alert(1)"])assert.equal(professionalShare(url),null);});
test("locations are bounded and unexpected map URL parameters rejected",()=>{assert.ok(locationShare("9","38")?.url.endsWith("9.000000,38.000000"));assert.equal(locationShare("91","38"),null);assert.equal(locationShare("","38"),null);assert.deepEqual(messageShareCards("https://www.google.com/maps/search/?api=1&query=9,38&evil=1"),[]);});
test("share cards deduplicate and preserve ordinary messages",()=>{const url="https://sera.hisabtech.com/jobs/"+id;assert.equal(messageShareCards("Check this\n"+url+"\n"+url).length,1);assert.deepEqual(messageShareCards("hi"),[]);});
