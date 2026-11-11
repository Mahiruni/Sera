import assert from "node:assert/strict";
import test from "node:test";
import { normalizeOrganizationSlug, validateOrganizationDraft } from "../lib/organizations/validation";

test("normalizes company slugs", () => {
  assert.equal(normalizeOrganizationSlug("  Hisab Technologies PLC  "), "hisab-technologies-plc");
});
test("accepts an Ethiopian company", () => {
  const result = validateOrganizationDraft({ kind: "company", name: "Hisab Technologies", city: "Addis Ababa" });
  assert.equal(result.ok, true);
  if (result.ok) {
    assert.equal(result.value.slug, "hisab-technologies");
    assert.equal(result.value.countryCode, "ET");
  }
});
test("rejects invalid organization types and names", () => {
  const result = validateOrganizationDraft({ kind: "personal", name: "A" });
  assert.equal(result.ok, false);
  if (!result.ok) {
    assert.ok(result.errors.kind);
    assert.ok(result.errors.name);
  }
});
test("rejects overly long descriptions", () => {
  const result = validateOrganizationDraft({ kind: "organization", name: "Test Org", description: "x".repeat(2001) });
  assert.equal(result.ok, false);
  if (!result.ok) assert.ok(result.errors.description);
});
