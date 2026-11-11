import assert from "node:assert/strict";
import test from "node:test";
import { messageDeliveryStatus, messageStatusLabel } from "../lib/organizations/message-status";

const saved = { created_at: "2026-11-12T00:00:00Z", delivered_at: null, read_at: null };
test("local queue and failed attempt are not mislabeled as sent", () => {
  assert.equal(messageDeliveryStatus(null), "sending");
  assert.equal(messageDeliveryStatus(saved, "sending"), "sending");
  assert.equal(messageDeliveryStatus(saved, "failed"), "failed");
});
test("database persistence means sent, not delivered", () => {
  assert.equal(messageDeliveryStatus(saved), "sent");
  assert.equal(messageDeliveryStatus({ ...saved, delivered_at:"2026-11-12T00:00:05Z" }), "delivered");
  assert.equal(messageDeliveryStatus({ ...saved, delivered_at:"2026-11-12T00:00:05Z", read_at:"2026-11-12T00:01:00Z" }), "read");
});
test("status labels are understandable to people", () => {
  assert.deepEqual(Object.values(messageStatusLabel), ["Sending","Sent","Delivered","Read","Failed"]);
});
