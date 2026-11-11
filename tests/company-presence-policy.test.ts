import assert from "node:assert/strict";
import test from "node:test";
import {
  canShowPresence,
  companyInboxAvailability,
  companyAvailabilityLabel,
} from "../lib/organizations/presence-policy";

test("individual activity can appear on personal posts and profiles", () => {
  assert.equal(canShowPresence("person", "feed"), true);
  assert.equal(canShowPresence("person", "profile"), true);
  assert.equal(canShowPresence("person", "inbox"), true);
});
test("companies and organizations never show activity on posts or profiles", () => {
  for (const kind of ["company", "organization"] as const) {
    assert.equal(canShowPresence(kind, "feed"), false);
    assert.equal(canShowPresence(kind, "profile"), false);
    assert.equal(canShowPresence(kind, "inbox"), true);
  }
});
test("company status reflects only consented team inbox activity", () => {
  const now = new Date("2026-11-12T00:00:00Z");
  assert.equal(companyInboxAvailability("2026-11-11T23:59:00Z", true, now), "available");
  assert.equal(companyInboxAvailability("2026-11-11T23:50:00Z", true, now), "away");
  assert.equal(companyInboxAvailability("2026-11-11T23:00:00Z", true, now), "offline");
  assert.equal(companyInboxAvailability(null, true, now), "offline");
  assert.equal(companyInboxAvailability("2026-11-11T23:59:00Z", false, now), "hidden");
  assert.equal(companyAvailabilityLabel.available, "Company team available");
});
