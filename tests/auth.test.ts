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
    accept_terms: "yes",
    password: "strong-test-password",
    full_name: "Test person",
    journey: "candidate",
    locale: "am",
  }))
    f.set(k, v);
  assert.equal(readRegistration(f)?.email, "test@example.com");
  f.delete("accept_terms");
  assert.equal(readRegistration(f), null);
  f.set("accept_terms", "yes");
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
      locale: "aa",
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

test("email localization uses supported languages, Arabic RTL and a truthful Afar fallback", async () => {
  const { supportedEmailLocales } = await import("../lib/emails/render");
  for (const locale of supportedEmailLocales) {
    for (const kind of emailKinds) {
      const e = renderEmail({
        kind,
        locale,
        name: "<test>",
        origin: "https://example.com",
        actionUrl: "https://example.com/action",
      });
      assert.equal(e.language, locale);
      assert.ok(e.html.includes(`lang="${locale}"`));
      assert.ok(e.html.includes(`dir="${locale === "ar" ? "rtl" : "ltr"}"`));
      assert.ok(e.html.includes("&lt;test&gt;"));
      assert.ok(e.subject && e.text);
    }
  }
  assert.equal(
    renderEmail({
      kind: "welcome",
      locale: "aa",
      origin: "https://example.com",
      actionUrl: "https://example.com/action",
    }).language,
    "en",
  );
});

// Community sharing must survive sign-in while the redirect allowlist stays closed.
test("community destinations allow only local story and member UUID routes", () => {
  assert.equal(safeDestination("/feed"), "/feed");
  const story = "/posts/36c7a54e-88d8-4fc0-aa91-11582cae249f";
  assert.equal(safeDestination(story), story);
  for (const bad of [
    story + "?next=evil",
    "/posts/../settings",
    "//evil.com/feed",
    "/members/not-a-uuid",
  ])
    assert.equal(safeDestination(bad), "/dashboard");
});
