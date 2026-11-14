import {createClient} from "@supabase/supabase-js";
import {serverClient} from "@/lib/supabase/server";
import {validMessageId} from "@/lib/network/message-contract";
import {cleanPreviewText,notificationPreviewMode,notificationText} from "@/lib/network/message-preview-model";
import {shouldSuppressMessage} from "@/lib/network/message-control-model";
export const runtime="nodejs";
const headers={"Cache-Control":"private, no-store"};
const empty=(status:number)=>new Response(null,{status,headers});
export async function GET(request:Request){
 const bearer=request.headers.get("authorization")??"";
 const url=process.env.NEXT_PUBLIC_SUPABASE_URL,key=process.env.NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY;
 if(!url||!key)return empty(503);
 if(bearer&&!/^Bearer [A-Za-z0-9._~-]+$/.test(bearer))return empty(401);
 const params=new URL(request.url).searchParams;
 const owner=params.get("owner"),target=params.get("conversation"),id=params.get("message"),kind=params.get("kind");
 if(!validMessageId(owner)||!validMessageId(target)||!validMessageId(id)||(kind!=="space_message"&&kind!=="missed_call"))return empty(400);
 const db=bearer?createClient(url,key,{global:{headers:{Authorization:bearer}},auth:{persistSession:false,autoRefreshToken:false}}):await serverClient();
 const {data:{user}}=await db.auth.getUser(bearer?bearer.slice(7):undefined);
 if(!user?.email_confirmed_at||user.id!==owner)return empty(401);
 const {data:n,error}=await db.from("professional_notifications").select("id,actor_id,kind,space_id,conversation_id,source_event_id,created_at,read_at").eq("id",id).eq("recipient_id",owner).eq("kind",kind).maybeSingle();
 if(error||!n||n.read_at||(kind==="space_message"?n.space_id:n.conversation_id)!==target)return empty(404);
 const {data:pref,error:prefError}=await db.from("sera_message_preferences").select("notification_preview_mode,notification_previews,notifications_enabled,quiet_enabled,quiet_start,quiet_end,quiet_timezone").eq("user_id",owner).maybeSingle();
 const control=kind==="missed_call"?await db.from("sera_conversation_preferences").select("muted_until").eq("user_id",owner).eq("conversation_id",target).maybeSingle():{data:null,error:null};
 if(prefError||control.error||shouldSuppressMessage(pref,control.data?.muted_until??null,new Date()))return empty(204);
 const {data:blocks,error:blockError}=await db.from("professional_blocks").select("blocker_id").or("and(blocker_id.eq."+owner+",blocked_id.eq."+n.actor_id+"),and(blocker_id.eq."+n.actor_id+",blocked_id.eq."+owner+")").limit(1);
 if(blockError||blocks?.length)return empty(204);
 const mode=notificationPreviewMode(pref?.notification_preview_mode,pref?.notification_previews);
 const locale=request.headers.get("cookie")?.match(/(?:^|;\s*)sera-language=(am|om|en)(?:;|$)/)?.[1]??request.headers.get("accept-language")??"en";
 const tr=(s:string)=>notificationText(locale,s);
 let spaceName="",messageText="";
 if(kind==="space_message"){
   const {data:spaces,error:spaceError}=await db.rpc("sera_list_spaces");const space=(spaces??[]).find((s:{id:string;status:string})=>s.id===target&&s.status==="active");if(spaceError||!space)return empty(404);
   spaceName=cleanPreviewText(space.name,80)||tr("Team conversation");
   if(mode==="full"){const {data:messages,error:messageError}=await db.rpc("sera_list_space_messages",{p_space:target});if(messageError)return empty(204);const row=(messages??[]).find((m:{id:string;sender_id:string})=>m.id===n.source_event_id&&m.sender_id===n.actor_id);messageText=cleanPreviewText(row?.body);}
 }else{
   const {data:thread}=await db.from("professional_conversations").select("member_a,member_b").eq("id",target).maybeSingle();
   if(!thread||!((thread.member_a===owner&&thread.member_b===n.actor_id)||(thread.member_b===owner&&thread.member_a===n.actor_id)))return empty(404);
 }
 if(mode==="hidden")return Response.json({mode,title:"Sera",body:tr(kind==="missed_call"?"You missed a call.":"You have a new team message."),createdAt:n.created_at},{headers});
 const {data:cards,error:cardError}=await db.rpc("member_cards",{ids:[n.actor_id]});const card=cards?.[0];
 if(cardError||!card||card.id!==n.actor_id)return empty(204);
 const sender=cleanPreviewText(card.full_name,80)||tr("Sera member");
 const avatar=typeof card.avatar_path==="string"?(await db.storage.from("sera-account-private").createSignedUrl(card.avatar_path,120)).data?.signedUrl??null:null;
 return Response.json({mode,title:kind==="space_message"?spaceName:sender,body:kind==="space_message"?sender+": "+(messageText||tr("Sent you a message")):tr("Missed call"),senderId:n.actor_id,senderName:sender,avatar,createdAt:n.created_at},{headers});
}
