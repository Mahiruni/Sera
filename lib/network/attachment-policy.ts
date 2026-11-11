/** Shared attachment policy for personal and company conversations.
 * Professional profile CV upload remains separately limited to 3 MiB. */
export const MESSAGE_ATTACHMENT_LIMIT = 5 * 1024 * 1024;
export const MESSAGE_ATTACHMENT_TYPES = [
  "image/jpeg", "image/png", "image/webp", "application/pdf",
] as const;
export type MessageAttachmentType = typeof MESSAGE_ATTACHMENT_TYPES[number];

export function checkMessageAttachmentMetadata(file: { size: number; type: string }): string | null {
  if (!MESSAGE_ATTACHMENT_TYPES.some(type => type === file.type))
    return "Only JPG, PNG, WebP images and PDF documents are supported. Videos are not allowed.";
  if (!Number.isSafeInteger(file.size) || file.size < 1 || file.size > MESSAGE_ATTACHMENT_LIMIT)
    return "Attachments must be 5 MB or smaller.";
  return null;
}

export function matchesMessageAttachmentSignature(
  type: string,
  header: Uint8Array,
): boolean {
  if (type === "application/pdf") {
    return header.length >= 5 && [37,80,68,70,45].every((b,i)=>header[i]===b);
  }
  if (type === "image/png")
    return header.length >= 8 && [137,80,78,71,13,10,26,10].every((b,i)=>header[i]===b);
  if (type === "image/jpeg")
    return header.length >= 3 && header[0]===255 && header[1]===216 && header[2]===255;
  if (type === "image/webp")
    return header.length >= 12 &&
      [82,73,70,70].every((b,i)=>header[i]===b) &&
      [87,69,66,80].every((b,i)=>header[8+i]===b);
  return false;
}

export async function checkMessageAttachment(file: File): Promise<string | null> {
  const metadata = checkMessageAttachmentMetadata(file);
  if (metadata) return metadata;
  const firstBytes = new Uint8Array(await file.slice(0, 16).arrayBuffer());
  return matchesMessageAttachmentSignature(file.type, firstBytes)
    ? null
    : "This file does not match its declared format. Choose a genuine image or PDF.";
}
