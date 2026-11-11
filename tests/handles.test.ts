import assert from "node:assert/strict";
import test from "node:test";
import { validatePublicHandle, profileLink, canChangeHandle, nextHandleChangeAt, HANDLE_CHANGE_COOLDOWN_DAYS } from "../lib/identity/handles";

test("accepts professional alphabetic handles with interior underscores or hyphens", () => {
  for (const value of ["mahir", "mahir_aman", "hisab-technologies", "SERA_Team"]) {
    assert.ok(validatePublicHandle(value), value);
  }
  assert.equal(validatePublicHandle("SERA_Team"), "sera_team");
});

test("rejects digits, slashes, punctuation, repeated or edge separators", () => {
  for (const value of ["mahir123", "name/name", "abc.def", "abc__def", "_username", "username_", "a", "company--team", "with space"]) {
    assert.equal(validatePublicHandle(value), null, value);
  }
});

test("links include their slash separator but the handle does not", () => {
  assert.equal(profileLink("person", "mahir_aman"), "/u/mahir_aman");
  assert.equal(profileLink("company", "hisab-technologies"), "/business/hisab-technologies");
  assert.equal(profileLink("company", "not/valid"), null);
});

test("handle changes require sixty complete days", () => {
  assert.equal(HANDLE_CHANGE_COOLDOWN_DAYS, 60);
  const changed = "2026-09-01T00:00:00.000Z";
  const next = nextHandleChangeAt(changed)!;
  assert.equal(next.toISOString(), "2026-10-31T00:00:00.000Z");
  assert.equal(canChangeHandle(changed, new Date(next.getTime() - 1)), false);
  assert.equal(canChangeHandle(changed, next), true);
  assert.equal(canChangeHandle(null, new Date(next.getTime() - 1)), true);
});
