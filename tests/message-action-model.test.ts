import test from "node:test";
import assert from "node:assert/strict";
import {parseMessageAction} from "../lib/network/message-action-model";
const base={action:"reply",owner:"11111111-1111-4111-8111-111111111111",conversation:"22222222-2222-4222-8222-222222222222",message:"33333333-3333-4333-8333-333333333333",clientId:"44444444-4444-4444-8444-444444444444",body:"  Hello  "};
test("valid notification reply retains stable ID and trims text",()=>{assert.equal(parseMessageAction(base)?.body,"Hello");assert.equal(parseMessageAction(base)?.clientId,base.clientId)});
test("rejects spoofed identifiers and invalid body",()=>{assert.equal(parseMessageAction({...base,owner:"other"}),null);assert.equal(parseMessageAction({...base,body:" "}),null);assert.equal(parseMessageAction({...base,body:"x".repeat(4001)}),null)});
test("explicit read requires message and conversation identifiers",()=>{assert.equal(parseMessageAction({...base,action:"read",body:null})?.action,"read");assert.equal(parseMessageAction({...base,action:"read",message:""}),null)});
