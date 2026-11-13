export function cleanPreviewText(value:unknown,limit=140):string{
 if(typeof value!=="string")return "";
 return Array.from(value.replace(/[\u0000-\u001f\u007f\u202a-\u202e\u2066-\u2069]/g," ").replace(/\s+/g," ").trim()).slice(0,limit).join("");
}
export function attachmentPreview(mime:unknown):string{
 if(typeof mime!=="string")return "Shared an attachment";
 if(mime.startsWith("image/"))return "Sent you a photo";
 if(mime.startsWith("audio/"))return "Sent a voice message";
 if(mime.startsWith("video/"))return "Sent you a video";
 return mime==="application/pdf"?"Shared a PDF document":"Shared an attachment";
}
