import test from "node:test";
import assert from "node:assert/strict";
import {
  discoveryInput,
  universalDiscoveryInput,
  profileEssentials,
  skillTopics,
} from "../lib/network/model";
import { safeDestination } from "../lib/auth/validation";
test("discovery strips filter grammar, retains multilingual search and bounds pagination", () => {
  assert.deepEqual(
    discoveryInput({
      q: " React,body.eq.secret (design)%_\\ ",
      tab: "projects",
      page: "9999",
    }),
    { query: "React body eq secret design", kind: "projects", page: 1000 },
  );
  assert.equal(
    discoveryInput({ q: "አዲስ አበባ", tab: "people" }).query,
    "አዲስ አበባ",
  );
  assert.equal(
    discoveryInput({ q: ["bad"], tab: "admin", page: "-1" }).kind,
    "people",
  );
  assert.equal(discoveryInput({ q: "x".repeat(200) }).query.length, 80);
  assert.equal(safeDestination("/discover"), "/discover");
  assert.equal(safeDestination("//example.com/discover"), "/dashboard");
});
test("profile progress is derived from actual essentials and never assumes missing data is complete", () => {
  assert.deepEqual(profileEssentials(null), { percent: 0, next: "Your name" });
  assert.deepEqual(
    profileEssentials({
      full_name: "Mahi",
      headline: "Engineer",
      summary: " ",
      skills: "React",
    }),
    { percent: 43, next: "About you" },
  );
  assert.deepEqual(
    profileEssentials({
      full_name: "Mahi",
      headline: "Engineer",
      summary: "Building",
      skills: "React",
      location: "Addis Ababa",
      experience: "Engineering",
      education: "Engineering",
    }),
    { percent: 100, next: "All essentials added" },
  );
});
test("interest shortcuts are bounded, deduplicated and derived from saved skill text", () => {
  assert.deepEqual(skillTopics("React, Design;React\nPhotography"), [
    "React",
    "Design",
    "Photography",
  ]);
  assert.deepEqual(skillTopics(undefined), []);
  assert.equal(skillTopics("aa,bb,cc,dd,ee,ff,gg").length, 6);
  assert.equal(skillTopics("x".repeat(41)).length, 0);
});

test("universal search defaults to All for a global query and preserves category deep links", () => {
  assert.deepEqual(universalDiscoveryInput({ q: "አዲስ አበባ" }), { query: "አዲስ አበባ", kind: "all", page: 1 });
  assert.equal(universalDiscoveryInput({}).kind, "people");
  assert.equal(universalDiscoveryInput({ tab: "all" }).kind, "all");
  assert.equal(universalDiscoveryInput({ q: "design", tab: "jobs", page: "2" }).kind, "jobs");
  assert.equal(universalDiscoveryInput({ q: "design", tab: ["all"] }).kind, "people");
  assert.equal(universalDiscoveryInput({ q: "%_\\", tab: "all" }).query, "");
});

test("recent searches are sanitized, deduplicated and bounded", async () => {
  const { recentSearches, rememberSearch } = await import("../lib/network/search-model");
  assert.deepEqual(rememberSearch(["React", "Design"], "react"), ["react", "Design"]);
  assert.deepEqual(recentSearches(["አዲስ አበባ", null, "%_", "Design"]), ["አዲስ አበባ", "Design"]);
  assert.equal(recentSearches(Array.from({length:20},(_,i)=>"search "+i)).length,8);
});
test("suggestions prioritize exact and prefix labels", async () => {
  const { rankSuggestions } = await import("../lib/network/search-model");
  const items = [{id:"1",label:"Senior React",kind:"Jobs" as const},{id:"2",label:"React developer",kind:"People" as const},{id:"3",label:"React",kind:"Posts" as const}];
  assert.deepEqual(rankSuggestions(items,"React").map(r=>r.id),["3","2","1"]);
});
