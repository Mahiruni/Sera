import { test } from "node:test";
import assert from "node:assert/strict";
import { dockSection, feedAutoHideEnabled, nextFeedChrome } from "../lib/navigation";

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

test("automatic navigation hiding is limited to the authenticated main feed",()=>{
  assert.equal(feedAutoHideEnabled("/feed",true),true);
  for(const path of ["/","/posts/id","/feed/detail","/connections","/jobs","/notifications","/discover","/profile","/settings","/network/messages"]){
    assert.equal(feedAutoHideEnabled(path,true),false);
  }
  assert.equal(feedAutoHideEnabled("/feed",false),false);
  assert.equal(feedAutoHideEnabled("/feed",true,true),false);
});
test("feed chrome uses directional hysteresis and returns at the top",()=>{
  let state={anchor:0,hidden:false};
  for(const y of [2,14,32,70])state=nextFeedChrome(state,y,1000);
  assert.equal(state.hidden,false);
  state=nextFeedChrome(state,90,1000);assert.equal(state.hidden,true);
  state=nextFeedChrome(state,130,1000);assert.equal(state.hidden,true);
  state=nextFeedChrome(state,125,1000);assert.equal(state.hidden,true);
  state=nextFeedChrome(state,117,1000);assert.equal(state.hidden,false);
  state=nextFeedChrome(state,120,1000);assert.equal(state.hidden,false);
  state=nextFeedChrome(state,145,1000);assert.equal(state.hidden,true);
  state=nextFeedChrome(state,-30,1000);assert.equal(state.hidden,false);
  assert.equal(state.anchor,0);
});
