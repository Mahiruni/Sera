import test from "node:test";
import assert from "node:assert/strict";
import {FILE_LIMIT,validAttachment,hasFileSignature,messageFilePath,validMessageId} from "../lib/network/message-contract";
test("private message metadata is bounded and normalized",()=>{
  assert.equal(validAttachment({name:"voice.m4a",mime:"audio/mp4",size:1000,durationMs:120000}),true);
  assert.equal(validAttachment({name:"voice.m4a",mime:"audio/mp4",size:1000,durationMs:120001}),false);
  assert.equal(validAttachment({name:"pdf",mime:"application/pdf",size:FILE_LIMIT+1}),false);
  assert.equal(validAttachment({name:"pdf",mime:"application/pdf",size:1,durationMs:100}),false);
  assert.equal(validAttachment({name:"bad",mime:"text/html",size:100}),false);
  assert.equal(validAttachment({name:"bad",mime:"__proto__",size:100}),false);
});
test("voice and document magic bytes are validated",()=>{
  assert.equal(hasFileSignature(Uint8Array.from([26,69,223,163]),"audio/webm"),true);
  assert.equal(hasFileSignature(new TextEncoder().encode("0000ftypM4A "),"audio/mp4"),true);
  assert.equal(hasFileSignature(new TextEncoder().encode("%PDF-1.7"),"application/pdf"),true);
  assert.equal(hasFileSignature(new TextEncoder().encode("<html>"),"application/pdf"),false);
});
test("retry uses deterministic per-user, per-conversation storage path",()=>{
  assert.equal(validMessageId("11111111-1111-4111-8111-111111111111"),true);
  assert.equal(validMessageId("../invalid"),false);
  assert.equal(messageFilePath("user","thread","client","audio/mp4"),"user/thread/client.m4a");
});
