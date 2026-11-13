const uuid=/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
export function messagePushPayload(recipient:string,conversation:string,message:string,kind:"message"|"company_message"="message"){
 if(![recipient,conversation,message].every(v=>typeof v==="string"&&uuid.test(v)))return null;
 // Private content is deliberately never included in the provider payload.
 return {recipientId:recipient,conversationId:conversation,messageId:message,kind,
 title:kind==="company_message"?"New company message":"New SERA message",
 body:"You have a new private message.",
 url:kind==="message"?"/network/messages?thread="+conversation:"/network/messages"};
}
