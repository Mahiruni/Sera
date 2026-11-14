import {currentUser} from "@/lib/supabase/server";
import {webPushConfig} from "@/lib/network/web-push-config";
export const dynamic="force-dynamic";
export async function GET(){
 const user=await currentUser();
 if(!user?.email_confirmed_at)return new Response(null,{status:401});
 const config=webPushConfig();
 return Response.json(config??{configured:false},{status:config?200:503,headers:{"Cache-Control":"private, no-store"}});
}
