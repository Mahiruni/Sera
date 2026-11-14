import test from "node:test";
import assert from "node:assert/strict";
import {notificationTab,kindsForTab,notificationPage,notificationHref,notificationTimeGroup,communicationNotificationHref} from "../lib/network/notification-model";
test("notification categories are allowlisted and preserve pagination",()=>{
 assert.equal(notificationTab("invalid"),"all");
 assert.equal(notificationTab(["network"]),"all");
 assert.deepEqual(kindsForTab("network"),["follow","connection_request","connection_accepted"]);
 assert.equal(kindsForTab("unread"),null);
 assert.equal(notificationPage("0"),1);assert.equal(notificationPage("999"),100);assert.equal(notificationPage("1e3"),1);
 assert.equal(notificationHref("social",2),"/notifications?tab=social&page=2");
});
test("notification dates use Ethiopia calendar boundaries",()=>{
 const now=Date.parse("2026-11-12T21:30:00Z");
 assert.equal(notificationTimeGroup("2026-11-12T21:10:00Z",now),"Today");
 assert.equal(notificationTimeGroup("2026-11-12T20:59:00Z",now),"Yesterday");
 assert.equal(notificationTimeGroup("2026-11-10T20:00:00Z",now),"Earlier");
 assert.equal(notificationTimeGroup("invalid",now),"Earlier");
});

test("communication alerts route only to validated targets",()=>{const id="11111111-1111-4111-8111-111111111111";assert.equal(communicationNotificationHref({kind:"missed_call",conversation_id:id}),"/network/messages?thread="+id);assert.equal(communicationNotificationHref({kind:"space_message",space_id:id}),"/collaboration?space="+id);assert.equal(communicationNotificationHref({kind:"space_message",space_id:"../admin"}),null);assert.deepEqual(kindsForTab("calls"),["missed_call"]);});
