import test from "node:test";
import assert from "node:assert/strict";
import {groupNotifications,notificationActorSummary} from "../lib/network/notification-model";
const event=(id:string,actor_id:string,kind="like",post_id:string|null="p",created_at="2026-11-12T10:00:00Z",read_at:string|null=null)=>({id,actor_id,kind,post_id,created_at,read_at});
test("groups consecutive social events by kind and post, deduplicates actors and tracks unread events",()=>{
 const a=event("a","u1"),b=event("b","u2"),c=event("c","u1","like","p","2026-11-12T09:00:00Z","2026-11-12T10:00:00Z");
 const g=groupNotifications([a,b,c,a,event("d","u3","job_match",null),event("e","u4","like","p")]);
 assert.equal(g.length,3);assert.deepEqual(g[0].actors,["u1","u2"]);assert.deepEqual(g[0].unreadIds,["a","b"]);assert.equal(g[0].events.length,3);
});
test("different posts, types, day boundaries, stale events and invitations remain separate",()=>{
 const rows=[event("a","u"),event("b","u","comment"),event("c","u","comment","q"),event("d","u","comment","q","2026-11-11T20:00:00Z"),event("e","u","connection_request",null),event("f","u","connection_request",null)];
 assert.equal(groupNotifications(rows).length,6);
 assert.equal(groupNotifications([event("a","u","like","p","2026-11-12T21:01:00Z"),event("b","v","like","p","2026-11-12T20:59:00Z")]).length,2);
 assert.equal(groupNotifications([event("a","u","like","p","2026-11-12T17:00:00Z"),event("b","v","like","p","2026-11-12T10:00:00Z")]).length,2);
 assert.equal(notificationActorSummary(["Abel","Sarah","Daniel"]),"Abel, Sarah and 1 other");
});
