import test from "node:test";
import assert from "node:assert/strict";
import {
  discoveryInput,
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
