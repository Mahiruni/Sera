import test from "node:test";
import assert from "node:assert/strict";
import {
  profileChecklist,
  pageNumber,
  stageLabel,
  employerReturn,
} from "../lib/workspace/model";
import type { Profile } from "../components/auth/forms";
const profile: Profile = {
  id: "qa",
  full_name: "QA User",
  locale: "en",
  journey: "candidate",
  location: "",
  phone: "",
  skills: "",
  experience: "",
  preferred_roles: "",
  company_name: "",
  company_role: "",
  onboarded_at: null,
};
test("workspace setup explains candidate and employer fields and handles private files honestly", () => {
  const candidate = profileChecklist(profile, null, "candidate");
  assert.equal(candidate.total, 9);
  assert.equal(candidate.complete, 1);
  assert.equal(candidate.percent, 11);
  assert(
    candidate.entries.some(([label, done]) => label === "CV uploaded" && !done),
  );
  const complete = profileChecklist(
    {
      ...profile,
      headline: "Engineer",
      location: "Addis Ababa",
      summary: "Summary",
      skills: "TypeScript",
      experience: "Engineer",
      education: "BSc",
    },
    { avatar_path: "photo", cv_path: "cv" },
    "candidate",
  );
  assert.equal(complete.percent, 100);
  const employer = profileChecklist(
    { ...profile, company_name: "QA Company", company_role: "Recruiter" },
    null,
    "employer",
  );
  assert.equal(employer.total, 7);
  assert.equal(employer.complete, 3);
  assert(!employer.entries.some(([label]) => label === "CV uploaded"));
});
test("posting updates preserve bounded filters without accepting external redirects", () => {
  const data = new FormData();
  data.set("jobsPage", "2");
  data.set("appsPage", "3");
  data.set("filter", "closed");
  assert.equal(
    employerReturn(data),
    "/employer?jobsPage=2&appsPage=3&status=closed#postings",
  );
  data.set("jobsPage", "https://example.com");
  data.set("filter", "//example.com");
  assert.equal(
    employerReturn(data, true),
    "/employer?jobsPage=1&appsPage=3&status=all&error=status#postings",
  );
});
test("workspace page controls are bounded and application statuses remain understandable", () => {
  for (const value of [undefined, "-1", "0", "x", "Infinity", "99999"])
    assert.equal(pageNumber(value), 1);
  assert.equal(pageNumber("9999"), 1000);
  assert.equal(pageNumber("2"), 2);
  assert.equal(stageLabel("screened"), "In review");
  assert.equal(stageLabel("rejected"), "Not selected");
  assert.equal(stageLabel("unexpected"), "Status unavailable");
});
