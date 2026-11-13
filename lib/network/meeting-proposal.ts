export type MeetingProposal={title:string;at:string;minutes:number};
export function parseMeetingProposal(body:string):MeetingProposal|null{
 const parts=body.split("\n");
 if(parts.length!==3||!parts[0].startsWith("Meeting proposal: ")||!parts[1].startsWith("When: ")||!parts[2].startsWith("Duration: "))return null;
 const title=parts[0].slice(18).trim(),at=parts[1].slice(6).trim(),minutes=Number(parts[2].slice(10).replace(/ minutes$/,""));
 if(!title||title.length>120||!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d{1,3})?Z$/.test(at)||!Number.isFinite(Date.parse(at))||![15,30,45,60,90,120].includes(minutes))return null;
 return {title,at,minutes};
}
export function meetingProposalText(title:string,localTime:string,minutes:number):string|null{
 const name=title.trim().replace(/[\r\n]/g," "),date=new Date(localTime);
 if(!name||name.length>120||!Number.isFinite(date.getTime())||date.getTime()<=Date.now()||![15,30,45,60,90,120].includes(minutes))return null;
 return "Meeting proposal: "+name+"\nWhen: "+date.toISOString()+"\nDuration: "+minutes+" minutes";
}
