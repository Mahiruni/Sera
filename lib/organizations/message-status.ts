/** Delivery semantics: sent = persisted, delivered = recipient client acknowledgement, read = opened. */
export type DeliveryStatus = "sending" | "sent" | "delivered" | "read" | "failed";
export type MessageReceipt = {
  created_at: string;
  delivered_at: string | null;
  read_at: string | null;
};
export function messageDeliveryStatus(
  receipt: MessageReceipt | null,
  localState?: "sending" | "failed",
): DeliveryStatus {
  if (localState === "failed") return "failed";
  if (!receipt || localState === "sending") return "sending";
  if (receipt.read_at) return "read";
  if (receipt.delivered_at) return "delivered";
  return "sent";
}
export const messageStatusLabel: Record<DeliveryStatus,string> = {
  sending: "Sending", sent: "Sent", delivered: "Delivered", read: "Read", failed: "Failed",
};
