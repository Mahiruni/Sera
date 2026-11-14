import {validMessageId} from "./message-contract";
export type ActivityReadInput={owner:string;target:string;event:string;kind:"space_message"|"missed_call"};
export function parseActivityRead(value:unknown):ActivityReadInput|null{
 if(!value||typeof value!=="object")return null;
 const x=value as Record<string,unknown>;
 if(!validMessageId(x.owner)||!validMessageId(x.target)||!validMessageId(x.event)||(x.kind!=="space_message"&&x.kind!=="missed_call"))return null;
 return {owner:x.owner,target:x.target,event:x.event,kind:x.kind};
}
export function activityTargetMatches(input:ActivityReadInput,row:{recipient_id:string;kind:string;space_id:string|null;conversation_id:string|null;id:string}):boolean{
 return row.id===input.event&&row.recipient_id===input.owner&&row.kind===input.kind&&(input.kind==="space_message"?row.space_id:row.conversation_id)===input.target;
}
