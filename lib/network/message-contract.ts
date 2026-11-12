/** Shared, bounded private messaging contract. No session secrets in browser code. */
export const MESSAGE_LIMIT = 4000;
export const FILE_LIMIT = 5 * 1024 * 1024;
export const VOICE_LIMIT_MS = 120000;
export const REACTIONS = ["👍","❤️","😂","😮","😢","👏"] as const;
export const CHAT_MIME_EXT = {
  "image/jpeg":"jpg", "image/png":"png", "image/webp":"webp", "application/pdf":"pdf",
  "audio/mp4":"m4a", "audio/webm":"webm", "audio/ogg":"ogg", "audio/mpeg":"mp3",
} as const;
export type ChatMime = keyof typeof CHAT_MIME_EXT;
export type AttachmentMeta = { name:string; mime:ChatMime; size:number; durationMs?:number|null };
export type ChatFile = AttachmentMeta & { id:string; url:string };
export type MessageView = {
  id:string; senderId:string; body:string; createdAt:string; readAt:string|null; deliveredAt:string|null;
  clientId:string|null; forwarded:boolean; unsent:boolean;
  reply:{id:string;senderId:string;body:string;unsent:boolean}|null;
  files:ChatFile[]; reactions:{memberId:string;emoji:string}[];
};
export type ThreadPreview = { id:string; peerId:string; name:string; photo?:string|null; lastBody:string|null; lastAt:string|null; unread:number; lastMessageId:string|null; lastSenderId:string|null; lastDeliveredAt:string|null };
export type SendInput = { conversation:string; clientId:string; body:string; replyTo?:string|null; attachment?:AttachmentMeta|null };
export const validMessageId = (v:unknown):v is string => typeof v==="string" && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(v);
export const isChatMime = (v:unknown):v is ChatMime => typeof v==="string" && Object.prototype.hasOwnProperty.call(CHAT_MIME_EXT,v);
export function validAttachment(a:unknown):a is AttachmentMeta {
  if(!a || typeof a!=="object")return false;
  const f=a as AttachmentMeta;
  return typeof f.name==="string" && f.name.trim().length>0 && f.name.length<=180 &&
    isChatMime(f.mime) && Number.isInteger(f.size) && f.size>=1 && f.size<=FILE_LIMIT &&
    (f.durationMs==null || (f.mime.startsWith("audio/") && Number.isInteger(f.durationMs) && f.durationMs>=1 && f.durationMs<=VOICE_LIMIT_MS));
}
export function messageFilePath(user:string,conversation:string,clientId:string,mime:ChatMime) {
  return user+"/"+conversation+"/"+clientId+"."+CHAT_MIME_EXT[mime];
}
export function hasFileSignature(bytes:Uint8Array,mime:ChatMime):boolean {
  const text=(start:number,len:number)=>String.fromCharCode(...bytes.slice(start,start+len));
  switch(mime) {
    case "image/jpeg": return bytes.length>=3 && bytes[0]===255 && bytes[1]===216 && bytes[2]===255;
    case "image/png": return bytes.length>=8 && [137,80,78,71,13,10,26,10].every((x,i)=>bytes[i]===x);
    case "image/webp": return bytes.length>=12 && text(0,4)==="RIFF" && text(8,4)==="WEBP";
    case "application/pdf": return text(0,5)==="%PDF-";
    case "audio/mp4": return bytes.length>=12 && text(4,4)==="ftyp";
    case "audio/webm": return bytes.length>=4 && [26,69,223,163].every((x,i)=>bytes[i]===x);
    case "audio/ogg": return text(0,4)==="OggS";
    case "audio/mpeg": return text(0,3)==="ID3" || (bytes.length>=2 && bytes[0]===255 && (bytes[1]&224)===224);
  }
}
