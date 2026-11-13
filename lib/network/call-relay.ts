import "server-only";
import type {SupabaseClient} from "@supabase/supabase-js";
import {normalizeCloudflareIce} from "@/lib/network/turn-contract";
export async function callRelayConfig(db:SupabaseClient,conversation:string,issue:boolean){
 const disabled={enabled:false};
 try{
  const {data:provider,error}=await db.rpc("sera_call_relay_access",{p_conversation:conversation,p_issue:false});
  if(error||!provider)return disabled;
  if(provider==="coturn"){const r=await db.rpc("sera_call_config",{p_conversation:conversation});return !r.error&&r.data?.enabled===true?r.data:disabled;}
  const id=process.env.SERA_CLOUDFLARE_TURN_KEY_ID,token=process.env.SERA_CLOUDFLARE_TURN_API_TOKEN;
  if(provider!=="cloudflare"||!id||!token||!/^[-a-zA-Z0-9_]{8,128}$/.test(id))return disabled;
  if(!issue)return {enabled:true};
  const claim=await db.rpc("sera_call_relay_access",{p_conversation:conversation,p_issue:true});
  if(claim.error||claim.data!=="cloudflare")return disabled;
  const r=await fetch("https://rtc.live.cloudflare.com/v1/turn/keys/"+id+"/credentials/generate-ice-servers",{method:"POST",headers:{Authorization:"Bearer "+token,"Content-Type":"application/json"},body:JSON.stringify({ttl:10800}),cache:"no-store",redirect:"error",signal:AbortSignal.timeout(8000)});
  if(!r.ok)return disabled;
  const iceServers=normalizeCloudflareIce(await r.json());
  return iceServers.length?{enabled:true,iceServers}:disabled;
 }catch{return disabled;}
}
