import "server-only";
import {createClient, type SupabaseClient} from "@supabase/supabase-js";
import {createCipheriv, createDecipheriv, createHash, randomBytes} from "node:crypto";
import {normalizeCloudflareIce} from "@/lib/network/turn-contract";

type Ice = ReturnType<typeof normalizeCloudflareIce>;
type Config = {enabled:boolean;iceServers?:Ice};
const disabled:Config={enabled:false};
const reuseMs=45*60*1000; // Three-hour provider TTL; keep >2 hours for a new call.
const pending=new Map<string,Promise<Config>>();
function key(token:string){return createHash("sha256").update("sera-turn-cache-v1\0"+token).digest();}
function seal(ice:Ice,until:number,member:string,id:string,token:string){
 const iv=randomBytes(12),c=createCipheriv("aes-256-gcm",key(token),iv);
 c.setAAD(Buffer.from(member+":"+id));
 const body=Buffer.concat([c.update(JSON.stringify({iceServers:ice,until}),"utf8"),c.final()]);
 return [iv,c.getAuthTag(),body].map(b=>b.toString("base64url")).join(".");
}
function open(sealed:string,member:string,id:string,token:string):Ice{
 try{
  const [iv,tag,body]=sealed.split(".").map(s=>Buffer.from(s,"base64url"));
  if(!iv||iv.length!==12||!tag||tag.length!==16||!body)return [];
  const d=createDecipheriv("aes-256-gcm",key(token),iv);d.setAAD(Buffer.from(member+":"+id));d.setAuthTag(tag);
  const raw=JSON.parse(Buffer.concat([d.update(body),d.final()]).toString("utf8"));
  if(!Number.isFinite(raw.until)||raw.until<=Date.now())return [];
  return normalizeCloudflareIce(raw);
 }catch{return [];}
}
function cacheDb(){
 const url=process.env.NEXT_PUBLIC_SUPABASE_URL,service=process.env.SERA_SUPABASE_SERVICE_ROLE_KEY||process.env.SUPABASE_SERVICE_ROLE_KEY;
 return url&&service?createClient(url,service,{auth:{persistSession:false,autoRefreshToken:false}}):null;
}
async function readCache(store:SupabaseClient|null,member:string,id:string,token:string):Promise<Ice>{
 if(!store)return [];
 try{
  const r=await store.from("sera_relay_credential_cache").select("sealed,reuse_until").eq("member_id",member).gt("reuse_until",new Date().toISOString()).maybeSingle();
  return !r.error&&r.data?open(r.data.sealed,member,id,token):[];
 }catch{return [];}
}
async function issueConfig(db:SupabaseClient,conversation:string,member:string,id:string,token:string):Promise<Config>{
 const store=cacheDb();
 const cached=await readCache(store,member,id,token);
 if(cached.length)return {enabled:true,iceServers:cached};
 const claim=await db.rpc("sera_call_relay_access",{p_conversation:conversation,p_issue:true});
 if(claim.error||claim.data!=="cloudflare"){
  // A concurrent request may have populated the persistent cache.
  const recovered=await readCache(store,member,id,token);
  if(recovered.length)return {enabled:true,iceServers:recovered};
  console.warn("Sera call relay issuance unavailable; rolling limits remain enforced");
  return disabled;
 }
 const started=Date.now();
 const r=await fetch("https://rtc.live.cloudflare.com/v1/turn/keys/"+id+"/credentials/generate-ice-servers",{method:"POST",headers:{Authorization:"Bearer "+token,"Content-Type":"application/json"},body:JSON.stringify({ttl:10800}),cache:"no-store",redirect:"error",signal:AbortSignal.timeout(8000)});
 if(!r.ok){console.warn("Sera Cloudflare TURN credential request failed",{status:r.status});return disabled;}
 const iceServers=normalizeCloudflareIce(await r.json());
 if(!iceServers.length){console.warn("Sera Cloudflare TURN returned no supported relay endpoints");return disabled;}
 if(store){
  try{
   const until=started+reuseMs;
   const saved=await store.from("sera_relay_credential_cache").upsert({member_id:member,sealed:seal(iceServers,until,member,id,token),reuse_until:new Date(until).toISOString()});
   if(saved.error)console.warn("Sera relay cache write unavailable",{code:saved.error.code});
  }catch{console.warn("Sera relay cache write unavailable");}
 }
 return {enabled:true,iceServers};
}
export async function callRelayConfig(db:SupabaseClient,conversation:string,issue:boolean):Promise<Config>{
 try{
  const {data:provider,error}=await db.rpc("sera_call_relay_access",{p_conversation:conversation,p_issue:false});
  if(error||!provider)return disabled;
  if(provider==="coturn"){const r=await db.rpc("sera_call_config",{p_conversation:conversation});return !r.error&&r.data?.enabled===true?r.data:disabled;}
  const id=process.env.SERA_CLOUDFLARE_TURN_KEY_ID,token=process.env.SERA_CLOUDFLARE_TURN_API_TOKEN;
  if(provider!=="cloudflare"||!id||!token||!/^[-a-zA-Z0-9_]{8,128}$/.test(id))return disabled;
  if(!issue)return {enabled:true};
  const {data:{user},error:authError}=await db.auth.getUser();
  if(authError||!user?.email_confirmed_at)return disabled;
  const scope=user.id+":"+id;
  const existing=pending.get(scope);
  if(existing)return await existing;
  const work=issueConfig(db,conversation,user.id,id,token);
  pending.set(scope,work);
  try{return await work;}finally{if(pending.get(scope)===work)pending.delete(scope);}
 }catch(e){console.warn("Sera call relay request failed",{kind:e instanceof Error?e.name:"unknown"});return disabled;}
}
