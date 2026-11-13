import {drainMessagePushJobs} from "@/lib/network/message-push-recovery";
export const runtime="nodejs";
export const maxDuration=60;
export async function GET(request:Request){
 const secret=process.env.SERA_MESSAGE_RECOVERY_SECRET;
 if(!secret||request.headers.get("authorization")!==`Bearer ${secret}`)return new Response(null,{status:401});
 try{return Response.json(await drainMessagePushJobs(),{headers:{"Cache-Control":"no-store"}});}
 catch{return Response.json({error:"Notification recovery unavailable"},{status:503,headers:{"Cache-Control":"no-store"}});}
}
