const uuid=/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
export function messagePushPayload(recipient:string,conversation:string,message:string,kind:"message"|"company_message"="message"){
 if(![recipient,conversation,message].every(v=>typeof v==="string"&&uuid.test(v)))return null;
 // Private content is deliberately never included in the provider payload.
 return {recipientId:recipient,conversationId:conversation,messageId:message,kind,
 title:kind==="company_message"?"New company message":"Sera",
 body:"You have a new private message.",
 url:kind==="message"?"/network/messages?thread="+conversation:"/network/messages"};
}

export function activityPushPayload(recipient:string,target:string,event:string,kind:"space_message"|"missed_call"){
 if(![recipient,target,event].every(v=>typeof v==="string"&&uuid.test(v)))return null;
 return {recipientId:recipient,conversationId:target,messageId:event,kind,title:"Sera",body:kind==="missed_call"?"You missed a call.":"You have a new team message.",url:kind==="missed_call"?"/network/messages?thread="+target:"/collaboration?space="+target};
}
