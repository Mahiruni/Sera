import {NextRequest,NextResponse} from "next/server";
import {moderationWriter} from "@/lib/moderation/writer";
import {drainMessagePushJobs} from "@/lib/network/message-push-recovery";
import {notifyProfessionalDevices} from "@/lib/network/push-delivery";
export const maxDuration=60;
export async function GET(request:NextRequest){
 const secret=process.env.CRON_SECRET;
 if(!secret||request.headers.get("authorization")!==`Bearer ${secret}`)return new NextResponse(null,{status:401});
 if(!process.env.FIREBASE_SERVICE_ACCOUNT_JSON)return NextResponse.json({error:"Push not configured"},{status:503});
 const db=moderationWriter();if(!db)return NextResponse.json({error:"Database unavailable"},{status:503});
 const recovery=await drainMessagePushJobs().catch(()=>({claimed:0,failed:1}));
 const {data,error}=await db.rpc("sera_claim_job_pushes");if(error)return NextResponse.json({error:"Claim failed"},{status:503});
 const rows=(data??[]) as {notification_id:string;recipient_id:string}[];
 const results=await Promise.allSettled(rows.map(async row=>{
  const pref=await db.from("sera_job_alert_preferences").select("enabled").eq("user_id",row.recipient_id).maybeSingle();
  if(pref.data?.enabled!==true)return;
  await notifyProfessionalDevices(row.recipient_id,{kind:"job_match",title:"A new opportunity for you",body:"Open SERA to explore a relevant career opportunity.",url:"/notifications?tab=opportunities"});
 }));
 return NextResponse.json({recovery,claimed:rows.length,failed:results.filter(r=>r.status==="rejected").length},{headers:{"Cache-Control":"no-store"}});
}
