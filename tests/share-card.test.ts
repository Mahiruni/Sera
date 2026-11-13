import {describe,it,expect} from "vitest";
import {professionalShare,locationShare,messageShareCards} from "../lib/network/share-card";
const id="f0caa111-0000-4000-8000-000000000001";
describe("safe SERA share cards",()=>{
 it("canonicalizes only supported SERA destinations",()=>{expect(professionalShare("https://sera.hisabtech.com/members/"+id)?.label).toBe("Professional profile");for(const url of ["https://evil.example/members/"+id,"https://sera.hisabtech.com.evil.example/members/"+id,"https://sera.hisabtech.com/members/"+id+"?private=1","https://sera.hisabtech.com/api/account/files?kind=cv","javascript:alert(1)"])expect(professionalShare(url)).toBeNull();});
 it("bounds coordinates and excludes unexpected URL parameters",()=>{expect(locationShare("9","38")?.url).toContain("9.000000,38.000000");expect(locationShare("91","38")).toBeNull();expect(locationShare("","38")).toBeNull();expect(messageShareCards("https://www.google.com/maps/search/?api=1&query=9,38&evil=1")).toEqual([]);});
 it("deduplicates and preserves normal message text",()=>{const url="https://sera.hisabtech.com/jobs/"+id;expect(messageShareCards("Check this\n"+url+"\n"+url)).toHaveLength(1);});
});
