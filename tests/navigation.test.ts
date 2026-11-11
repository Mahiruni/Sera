import { test } from "node:test";
import assert from "node:assert/strict";
import { dockSection } from "../lib/navigation";

test("community stories keep Home selected when opening or editing a post", () => {
  for (const path of [
    "/",
    "/feed",
    "/posts/story-id",
    "/posts/story-id/edit",
  ]) {
    assert.equal(dockSection(path), "home");
  }
});

test("detail pages retain the navigation section that owns their task", () => {
  assert.equal(dockSection("/jobs/role-id"), "discover");
  assert.equal(dockSection("/members/member-id"), "discover");
  assert.equal(dockSection("/saved"), "saved");
  assert.equal(dockSection("/applications/application-id"), "workspace");
  assert.equal(dockSection("/network/messages"), "workspace");
  assert.equal(dockSection("/employer/pipeline"), "workspace");
  assert.equal(dockSection("/settings/safety"), "profile");
  assert.equal(dockSection("/profile"), "profile");
});

test("route boundaries and information pages do not select an unrelated section", () => {
  for (const path of [
    "/jobs-board",
    "/saved-search",
    "/about",
    "/privacy",
    "/help",
  ]) {
    assert.equal(dockSection(path), null);
  }
});
