import { test } from "node:test";
import assert from "node:assert/strict";
import {
  initialState,
  filterJobs,
  apply,
  keywordMatch,
  isState,
  pay,
} from "../lib/model";
test("combined filters narrow original samples without changing source", () => {
  assert.deepEqual(
    filterJobs(
      initialState.jobs,
      "react",
      "it",
      "Addis Ababa",
      "Full-time",
    ).map((j) => j.id),
    [2],
  );
  assert.equal(initialState.jobs.length, 8);
  assert.equal(filterJobs(initialState.jobs, "not a real role").length, 0);
});
test("closed roles are excluded from discovery and applications", () => {
  const state = structuredClone(initialState);
  state.role = "seeker";
  state.jobs[0].status = "closed";
  assert.equal(filterJobs(state.jobs).length, 7);
  assert.equal(apply(state, 1, "hello").apps.length, 0);
});
test("application requires candidate role, valid job, and is idempotent", () => {
  assert.equal(apply(initialState, 1, "hello").apps.length, 0);
  const state = { ...initialState, role: "seeker" as const };
  const applied = apply(state, 1, "My cover letter");
  assert.equal(applied.apps.length, 1);
  assert.equal(applied.apps[0].cover, "My cover letter");
  assert.equal(apply(applied, 1, "duplicate"), applied);
  assert.equal(apply(state, 999, "invalid").apps.length, 0);
  assert.equal(initialState.apps.length, 0);
});
test("keyword overlap is explainable and never claims intelligence", () => {
  const job = initialState.jobs[0];
  const result = keywordMatch(initialState.profile, job);
  assert.ok(result.hits.includes("peachtree"));
  assert.ok(result.percent >= 0 && result.percent <= 100);
  assert.equal(
    keywordMatch(
      { headline: "", skills: "", exp: "", cv: "", loc: "", langs: "" },
      job,
    ).percent,
    0,
  );
});
test("invalid or older browser state is rejected before use", () => {
  assert.ok(isState(initialState));
  assert.equal(isState(null), false);
  assert.equal(isState({ ...initialState, version: 2 }), false);
  assert.equal(isState({ ...initialState, profile: {} }), false);
  assert.equal(isState({ ...initialState, jobs: [{ id: 1 }] }), false);
  assert.equal(
    isState({
      ...initialState,
      apps: [{ id: 1, jobId: 1, stage: "unknown", cover: "" }],
    }),
    false,
  );
  assert.equal(isState({ ...initialState, saved: ["bad"] }), false);
});
test("salary displays correct ETB ranges and unspecified salary honestly", () => {
  assert.equal(pay(initialState.jobs[0]), "18,000–25,000 ETB");
  assert.equal(pay(initialState.jobs[7]), "20,000 ETB");
  assert.equal(
    pay({ ...initialState.jobs[0], payMin: 0, payMax: 0 }),
    "Salary not specified",
  );
});
