import {createClient} from "@supabase/supabase-js";
import {serverClient} from "@/lib/supabase/server";
import {parseActivityRead,activityTargetMatches} from "@/lib/network/activity-read-model";
export const runtime="nodejs";
const reply=(status:number)=>new Response(null,{status,headers:{"Cache-Control":"private, no-store"}});
export async function POST(request:Request){
 const bearer=request.headers.get("authorization")??"";
 const url=process.env.NEXT_PUBLIC_SUPABASE_URL,key=process.env.NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY;
 if(!url||!key)return reply(503);
 if(bearer&&!/^Bearer [A-Za-z0-9._~-]+$/.test(bearer))return reply(401);
 if(!bearer&&request.headers.get("origin")!==new URL(request.url).origin)return reply(403);
 const input=parseActivityRead(await request.json().catch(()=>null));if(!input)return reply(400);
 const db=bearer?createClient(url,key,{global:{headers:{Authorization:bearer}},auth:{persistSession:false,autoRefreshToken:false}}):await serverClient();
 const {data:{user}}=await db.auth.getUser(bearer?bearer.slice(7):undefined);
 if(!user?.email_confirmed_at||user.id!==input.owner)return reply(401);
 const {data:n,error}=await db.from("professional_notifications").select("id,recipient_id,actor_id,kind,space_id,conversation_id,read_at").eq("id",input.event).eq("recipient_id",input.owner).maybeSingle();
 if(error)return reply(503);if(!n||!activityTargetMatches(input,n))return reply(404);
 const {data:blocks,error:blockError}=await db.from("professional_blocks").select("blocker_id").or("and(blocker_id.eq."+input.owner+",blocked_id.eq."+n.actor_id+"),and(blocker_id.eq."+n.actor_id+",blocked_id.eq."+input.owner+")").limit(1);
 if(blockError)return reply(503);if(blocks?.length)return reply(403);
 if(input.kind==="space_message"){
  const {data:spaces,error:spaceError}=await db.rpc("sera_list_spaces");if(spaceError)return reply(503);
  if(!(spaces??[]).some((s:{id:string;status:string})=>s.id===input.target&&s.status==="active"))return reply(403);
 }else{
  const {data:thread,error:threadError}=await db.from("professional_conversations").select("member_a,member_b").eq("id",input.target).maybeSingle();
  if(threadError)return reply(503);if(!thread||!((thread.member_a===input.owner&&thread.member_b===n.actor_id)||(thread.member_b===input.owner&&thread.member_a===n.actor_id)))return reply(403);
 }
 if(n.read_at)return reply(204);
 const {error:updateError}=await db.from("professional_notifications").update({read_at:new Date().toISOString()}).eq("id",input.event).eq("recipient_id",input.owner).eq("kind",input.kind).eq(input.kind==="space_message"?"space_id":"conversation_id",input.target).is("read_at",null);
 return reply(updateError?503:204);
}
