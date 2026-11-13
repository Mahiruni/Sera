import {createClient} from "@supabase/supabase-js";
import {validMessageId} from "@/lib/network/message-contract";
import {callRelayConfig} from "@/lib/network/call-relay";
export const runtime="nodejs";
export async function POST(request:Request){
 const bearer=request.headers.get("authorization")||"";
 if(!/^Bearer [A-Za-z0-9._~-]+$/.test(bearer))return new Response(null,{status:401});
 const url=process.env.NEXT_PUBLIC_SUPABASE_URL,key=process.env.NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY;
 if(!url||!key)return new Response(null,{status:503});
 const db=createClient(url,key,{global:{headers:{Authorization:bearer}},auth:{persistSession:false,autoRefreshToken:false}});
 const {data:{user}}=await db.auth.getUser(bearer.slice(7));
 if(!user?.email_confirmed_at)return new Response(null,{status:401});
 const body=await request.json().catch(()=>null);
 if(!body||!validMessageId(body.conversation)||typeof body.issue!=="boolean")return new Response(null,{status:400});
 return Response.json(await callRelayConfig(db,body.conversation,body.issue),{headers:{"Cache-Control":"private, no-store","X-Content-Type-Options":"nosniff"}});
}
