import test from "node:test";import assert from "node:assert/strict";
import {cleanPreviewText,attachmentPreview,notificationPreviewMode,notificationText} from "../lib/network/message-preview-model";
test("notification text is bounded, single-line and strips bidi controls",()=>{assert.equal(cleanPreviewText("  hello\nworld\u202e  "),"hello world");assert.equal(cleanPreviewText("😀😀😀",2),"😀😀");assert.equal(cleanPreviewText(null),"");});
test("safe attachment labels contain no file names or paths",()=>{assert.equal(attachmentPreview("image/jpeg"),"Sent you a photo");assert.equal(attachmentPreview("application/pdf"),"Shared a PDF document");assert.equal(attachmentPreview(null),"Shared an attachment");});

test("preview mode preserves existing privacy and defaults new accounts to sender only",()=>{assert.equal(notificationPreviewMode(undefined,false),"hidden");assert.equal(notificationPreviewMode(undefined,true),"full");assert.equal(notificationPreviewMode(undefined),"sender");assert.equal(notificationPreviewMode("sender",false),"sender");});

test("notification copy localizes only system labels",()=>{assert.notEqual(notificationText("am","Sent you a message"),"Sent you a message");assert.notEqual(notificationText("om","Sent you a message"),"Sent you a message");assert.equal(notificationText("am","Hello from the sender"),"Hello from the sender");});
