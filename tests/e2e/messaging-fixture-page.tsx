import {MessageWorkspace} from "@/components/network/sera-message-workspace";
import type {MessageView,ThreadPreview} from "@/lib/network/message-contract";
const userId="00000000-0000-4000-8000-000000000001";
const peerId="00000000-0000-4000-8000-000000000002";
const thread:ThreadPreview={id:"00000000-0000-4000-8000-000000000003",peerId,name:"Alex Morgan",photo:null,lastBody:"The project review is ready.",lastAt:"2026-11-12T14:30:00Z",unread:2,lastMessageId:null,lastSenderId:userId,lastDeliveredAt:null};
const other:ThreadPreview={...thread,id:"00000000-0000-4000-8000-000000000004",peerId:"00000000-0000-4000-8000-000000000005",name:"Samira Bekele",lastBody:"Thanks for sharing the update.",unread:0};
const msg=(id:string,senderId:string,body:string,extra:Partial<MessageView>={}):MessageView=>({id,senderId,body,createdAt:"2026-11-12T14:30:00Z",readAt:null,deliveredAt:null,clientId:null,forwarded:false,unsent:false,reply:null,files:[],reactions:[],...extra});
const first=msg("00000000-0000-4000-8000-000000000011",peerId,"Hi! The project review is ready.");
const messages=[
 first,
 msg("00000000-0000-4000-8000-000000000012",userId,"Excellent. I will review it today.",{readAt:"2026-11-12T14:31:00Z",deliveredAt:"2026-11-12T14:31:00Z",reactions:[{memberId:peerId,emoji:"👍"}]}),
 msg("00000000-0000-4000-8000-000000000013",peerId,"Thank you! Let me know if you have any feedback.",{reply:{id:first.id,senderId:peerId,body:first.body,unsent:false}}),
 msg("00000000-0000-4000-8000-000000000014",userId,"The next review is scheduled for tomorrow.",{forwarded:true,deliveredAt:"2026-11-12T14:31:00Z"}),
 msg("00000000-0000-4000-8000-000000000015",peerId,"Message unsent",{unsent:true}),
];
export default async function Fixture({searchParams}:{searchParams:Promise<{view?:string}>}) {
 const {view}=await searchParams;
 return <div className="sera-network network-app sera-inbox sera-chat-page"><MessageWorkspace userId={userId} threads={[thread,other]} selected={view==="inbox"?null:thread} messages={messages} hasMore={false} search="" loadError="" candidates={[]}/></div>;
}
