import test from "node:test";
import assert from "node:assert/strict";
import {
  validatedOrigin,
  DEFAULT_ORIGIN,
  jsonLd,
  publicPages,
  pageMetadata,
} from "../lib/seo";
import { locales, pageLanguage, contentLanguage } from "../lib/locales";
test("canonical origin rejects insecure, credentialed or unverified domain configuration", () => {
  assert.equal(validatedOrigin(undefined), DEFAULT_ORIGIN);
  for (const url of [
    "http://sera.et",
    "https://user:pass@example.com",
    "https://example.com/path",
    "https://sera.et",
  ])
    assert.throws(() => validatedOrigin(url, false));
  assert.equal(validatedOrigin("https://sera.et", true), "https://sera.et");
});
test("only published useful public pages enter the indexing allowlist", () => {
  assert.equal(
    new Set(publicPages.map((p) => p.path)).size,
    publicPages.length,
  );
  for (const privatePath of [
    "/jobs",
    "/account",
    "/profile",
    "/messages",
    "/demo",
    "/privacy",
    "/ar",
  ])
    assert.ok(!publicPages.some((p) => String(p.path) === privatePath));
  assert.equal(
    (pageMetadata("/demo", "Demo", "Sample").robots as { index: boolean })
      .index,
    false,
  );
});
test("all seven preferences retain honest content-language and Arabic page direction inputs", () => {
  assert.equal(locales.length, 7);
  assert.equal(contentLanguage("om"), "en");
  assert.equal(contentLanguage("am"), "am");
  assert.equal(pageLanguage("/ar", "en"), "ar");
  assert.equal(pageLanguage("/guides", "am"), "en");
  assert.equal(pageLanguage("/jobs", "am"), "am");
});
test("structured data escapes script delimiters", () => {
  assert.ok(!jsonLd({ text: "</script><script>" }).includes("<"));
  assert.deepEqual(JSON.parse(jsonLd({ text: "<value>" })), {
    text: "<value>",
  });
});
