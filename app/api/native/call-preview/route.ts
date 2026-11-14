import {createClient} from "@supabase/supabase-js";
import {validMessageId} from "@/lib/network/message-contract";
import {cleanPreviewText,notificationPreviewMode} from "@/lib/network/message-preview-model";
export const runtime="nodejs";
const headers={"Cache-Control":"private, no-store"};
const empty=(status:number)=>new Response(null,{status,headers});
export async function GET(request:Request){
 const bearer=request.headers.get("authorization")??"";
 const url=process.env.NEXT_PUBLIC_SUPABASE_URL,key=process.env.NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY;
 if(!url||!key)return empty(503);
 if(!/^Bearer [A-Za-z0-9._~-]+$/.test(bearer))return empty(401);
 const params=new URL(request.url).searchParams;const owner=params.get("owner"),id=params.get("call");
 if(!validMessageId(owner)||!validMessageId(id))return empty(400);
 const db=createClient(url,key,{global:{headers:{Authorization:bearer}},auth:{persistSession:false,autoRefreshToken:false}});
 const {data:{user}}=await db.auth.getUser(bearer.slice(7));if(!user?.email_confirmed_at||user.id!==owner)return empty(401);
 const {data:pref,error}=await db.from("sera_message_preferences").select("notification_preview_mode,notification_previews").eq("user_id",owner).maybeSingle();
 if(error||notificationPreviewMode(pref?.notification_preview_mode,pref?.notification_previews)==="hidden")return empty(204);
 const {data:call}=await db.rpc("sera_call_incoming",{p_id:id});
 if(!call||call.id!==id||!validMessageId(call.caller)||Date.parse(call.expires_at)<=Date.now())return empty(404);
 const {data:cards}=await db.rpc("member_cards",{ids:[call.caller]});const card=cards?.[0];
 if(!card||card.id!==call.caller)return empty(404);
 const title=cleanPreviewText(card.full_name,80)||"Sera member";
 const avatar=typeof card.avatar_path==="string"?(await db.storage.from("sera-account-private").createSignedUrl(card.avatar_path,120)).data?.signedUrl??null:null;
 return Response.json({title,avatar,senderId:call.caller},{headers});
}
