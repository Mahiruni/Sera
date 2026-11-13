import {validMessageId,MESSAGE_LIMIT} from "./message-contract";
type MessageAction={action:"read"|"reply";owner:string;conversation:string;message:string;clientId:string;body:string};
export function parseMessageAction(value:unknown):MessageAction|null{
 if(!value||typeof value!=="object")return null;
 const v=value as Record<string,unknown>;
 if(v.action!=="read"&&v.action!=="reply")return null;
 if(!validMessageId(v.owner)||!validMessageId(v.conversation)||!validMessageId(v.message)||!validMessageId(v.clientId))return null;
 const body=typeof v.body==="string"?v.body.trim():"";
 if(v.action==="reply"&&(!body||body.length>MESSAGE_LIMIT))return null;
 return {action:v.action,owner:v.owner,conversation:v.conversation,message:v.message,clientId:v.clientId,body};
}
