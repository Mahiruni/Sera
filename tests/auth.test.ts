import test from "node:test";
import assert from "node:assert/strict";
import {
  safeDestination,
  normalizedEmail,
  readRegistration,
  readProfile,
  passwordIssue,
} from "../lib/auth/validation";
import { renderEmail, emailKinds, escapeHtml } from "../lib/emails/render";
test("redirect allowlist rejects external, encoded and protocol-relative destinations", () => {
  for (const path of [
    "https://evil.test",
    "//evil.test",
    "/\\evil.test",
    "/%2f%2fevil.test",
    "/dashboard?next=evil",
  ])
    assert.equal(safeDestination(path), "/dashboard");
  assert.equal(safeDestination("/reset-password"), "/reset-password");
});
test("registration accepts only supported journeys/locales and validates required fields", () => {
  const f = new FormData();
  for (const [k, v] of Object.entries({
    email: " TEST@example.com ",
    password: "strong-test-password",
    full_name: "Test person",
    journey: "candidate",
    locale: "am",
  }))
    f.set(k, v);
  assert.equal(readRegistration(f)?.email, "test@example.com");
  f.set("journey", "admin");
  assert.equal(readRegistration(f), null);
  f.set("journey", "candidate");
  f.set("locale", "xx");
  assert.equal(readRegistration(f), null);
  assert.ok(passwordIssue("short"));
  assert.equal(normalizedEmail("not-an-email"), null);
});
test("profile limits reject overlong input and ignore injected authorization fields", () => {
  const f = new FormData();
  for (const [k, v] of Object.entries({
    full_name: "Test person",
    locale: "en",
    journey: "employer",
    role: "admin",
  }))
    f.set(k, v);
  assert.ok(readProfile(f));
  assert.ok(!("role" in readProfile(f)!));
  f.set("skills", "x".repeat(501));
  assert.equal(readProfile(f), null);
});
test("emails escape personal content, provide plain text and reject unsafe links", () => {
  for (const kind of emailKinds) {
    const e = renderEmail({
      kind,
      name: "<script>alert(1)</script>",
      locale: "ar",
      actionUrl: "https://example.com/action",
      origin: "https://example.com",
    });
    assert.ok(!e.html.includes("<script>"));
    assert.ok(e.html.includes("&lt;script&gt;"));
    assert.ok(e.text.includes("https://example.com/action"));
    assert.ok(e.html.includes('lang="en"'));
    assert.ok(e.html.includes('role="presentation"'));
  }
  assert.throws(() =>
    renderEmail({
      kind: "welcome",
      actionUrl: "javascript:alert(1)",
      origin: "https://example.com",
    }),
  );
  assert.equal(escapeHtml('"<&'), "&quot;&lt;&amp;");
});
