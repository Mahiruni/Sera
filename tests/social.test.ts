import test from "node:test";
import assert from "node:assert/strict";
import {
  readPost,
  readComment,
  validMedia,
  pageNumber,
  MEDIA_LIMIT,
} from "../lib/social/validation";
function post(kind = "idea") {
  const f = new FormData();
  f.set("kind", kind);
  f.set("title", "Building useful things");
  f.set("body", "Here is what I learned.");
  return f;
}
test("social posts use the live schema and never accept ownership, counters or status", () => {
  const f = post();
  f.set("user_id", "attacker");
  f.set("author_id", "attacker");
  f.set("like_count", "99");
  f.set("status", "published");
  f.set("skills", "React, Design, React");
  const p = readPost(f)!;
  assert.deepEqual(p.skills, ["React", "Design"]);
  for (const field of ["user_id", "author_id", "like_count", "status"])
    assert.equal(field in p, false);
  assert.equal(p.kind, "idea");
  assert.equal(p.body, "Here is what I learned.");
});
test("post validation enforces type-specific lengths, HTTPS links and bounded skills", () => {
  const f = post("quote");
  f.set("title", "");
  assert.ok(readPost(f));
  f.set("body", "x".repeat(601));
  assert.equal(readPost(f), null);
  for (const url of [
    "javascript:alert(1)",
    "http://example.com",
    "https://user:pass@example.com",
    "//evil.com",
    "https://example.com/a b",
  ]) {
    const f = post("project");
    f.set("project_url", url);
    assert.equal(readPost(f), null, url);
  }
  const valid = post("project");
  valid.set("project_url", "https://example.com/project");
  assert.ok(readPost(valid));
  valid.set(
    "skills",
    Array.from({ length: 11 }, (_, i) => "skill" + i).join(","),
  );
  assert.equal(readPost(valid), null);
  const legacy = post("text");
  assert.equal(readPost(legacy), null);
  const blank = post();
  blank.set("body", "  ");
  assert.equal(readPost(blank), null);
});
test("comments reject missing post identity, malformed parents and blank or overlong text", () => {
  const f = new FormData();
  f.set("body", "A reply");
  assert.equal(readComment(f), null);
  f.set("post_id", "36c7a54e-88d8-4fc0-aa91-11582cae249f");
  assert.ok(readComment(f));
  f.set("parent_id", "bad");
  assert.equal(readComment(f), null);
  f.delete("parent_id");
  f.set("body", " ");
  assert.equal(readComment(f), null);
  f.set("body", "x".repeat(2001));
  assert.equal(readComment(f), null);
});
test("media input rejects unsupported formats, too many images and aggregate oversize", () => {
  const image = () => new File(["image"], "photo.jpg", { type: "image/jpeg" });
  assert.ok(validMedia([image()]));
  assert.ok(validMedia([]));
  assert.equal(
    validMedia([new File(["svg"], "x.svg", { type: "image/svg+xml" })]),
    false,
  );
  assert.equal(validMedia(Array.from({ length: 5 }, image)), false);
  const big = new File([new Uint8Array(MEDIA_LIMIT)], "big.png", {
    type: "image/png",
  });
  assert.equal(validMedia([big, image()]), false);
});
test("page controls cannot inject filters or request unbounded database ranges", () => {
  assert.equal(pageNumber("-1"), 1);
  assert.equal(pageNumber("abc"), 1);
  assert.equal(pageNumber(["1"]), 1);
  assert.equal(pageNumber("9999"), 1000);
  assert.equal(pageNumber("2"), 2);
});
