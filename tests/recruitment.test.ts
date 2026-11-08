import test from "node:test";
import assert from "node:assert/strict";
import {
  readJob,
  readApplication,
  readMessage,
} from "../lib/recruitment/validation";
test("job posts validate lengths, salary and acknowledgement without trusting supplied owner", () => {
  const f = new FormData();
  for (const [k, v] of Object.entries({
    title: "Engineer",
    company: "Test organization",
    location: "Addis Ababa",
    work_type: "Contract",
    description:
      "A legitimate role description with enough detail for applicants.",
    requirements: "Relevant engineering experience",
    salary_min: "100",
    salary_max: "500",
    posting_acknowledgement: "yes",
    employer_id: "attacker",
  }))
    f.set(k, v);
  assert.ok(readJob(f));
  assert.ok(!("employer_id" in readJob(f)!));
  f.set("salary_max", "50");
  assert.equal(readJob(f), null);
  f.set("salary_max", "500");
  f.delete("posting_acknowledgement");
  assert.equal(readJob(f), null);
});
test("application sharing and message validation reject invalid ids and overlong content", () => {
  const f = new FormData();
  f.set("job_id", "1");
  f.set("cover_note", "This is a thoughtful application note.");
  f.set("share_profile", "yes");
  assert.equal(readApplication(f), null);
  f.set("job_id", "36c7a54e-88d8-4fc0-aa91-11582cae249f");
  assert.ok(readApplication(f));
  f.delete("share_profile");
  assert.equal(readApplication(f), null);
  f.set("application_id", "36c7a54e-88d8-4fc0-aa91-11582cae249f");
  f.set("body", "   ");
  assert.equal(readMessage(f), null);
  f.set("body", "x".repeat(4001));
  assert.equal(readMessage(f), null);
  f.set("body", "Thanks for your application.");
  assert.ok(readMessage(f));
});
