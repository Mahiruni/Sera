import test from "node:test";
import assert from "node:assert/strict";
import {controlText} from "../lib/network/message-control-copy";
import {notificationPreviewMode} from "../lib/network/message-preview-model";
test("full is the default, explicit privacy preferences win",()=>{assert.equal(notificationPreviewMode(undefined),"full");assert.equal(notificationPreviewMode("full"),"full");assert.equal(notificationPreviewMode("sender"),"sender");assert.equal(notificationPreviewMode("hidden"),"hidden");assert.equal(notificationPreviewMode(undefined,false),"hidden");});
test("control center supports English, Amharic and Afaan Oromo",()=>{assert.equal(controlText("en","Messaging control center"),"Messaging control center");assert.notEqual(controlText("am","Messaging control center"),"Messaging control center");assert.notEqual(controlText("om-ET","Messaging control center"),"Messaging control center");});
