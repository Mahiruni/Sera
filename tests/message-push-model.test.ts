import test from "node:test";
import assert from "node:assert/strict";
import {messagePushPayload,activityPushPayload} from "../lib/network/message-push-model";
const recipient="11111111-1111-4111-8111-111111111111",thread="22222222-2222-4222-8222-222222222222",id="33333333-3333-4333-8333-333333333333";
test("message notification is account-bound and targets the exact conversation",()=>{
 const p=messagePushPayload(recipient,thread,id)!;
 assert.equal(p.recipientId,recipient);assert.equal(p.messageId,id);assert.equal(p.conversationId,thread);
 assert.equal(p.url,"/network/messages?thread="+thread);
 assert.equal(p.body,"You have a new private message.");
});
test("malformed identifiers cannot enter notification routing",()=>{
 assert.equal(messagePushPayload(recipient,"../admin",id),null);
 assert.equal(messagePushPayload("",thread,id),null);
});

test("activity push carries only opaque ids and generic copy",()=>{const p=activityPushPayload(recipient,thread,id,"space_message")!;assert.equal(p.url,"/collaboration?space="+thread);assert.equal(p.title,"Sera");assert.equal(p.messageId,id);assert.equal(activityPushPayload(recipient,"../admin",id,"missed_call"),null);assert.equal(activityPushPayload(recipient,thread,id,"missed_call")?.url,"/network/messages?thread="+thread);});
