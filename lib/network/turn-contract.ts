/** Preserve only the provider's authenticated relay endpoints; flatten for Android parity. */
export function normalizeCloudflareIce(raw:unknown):{urls:string;username:string;credential:string}[]{
 if(!raw||typeof raw!=="object"||!Array.isArray((raw as {iceServers?:unknown}).iceServers))return [];
 const out:{urls:string;username:string;credential:string}[]=[];
 for(const s of (raw as {iceServers:unknown[]}).iceServers){
  if(!s||typeof s!=="object")continue;
  const {urls,username,credential}=s as {urls?:unknown;username?:unknown;credential?:unknown};
  if(typeof username!=="string"||!username||username.length>512||typeof credential!=="string"||!credential||credential.length>512)continue;
  for(const u of Array.isArray(urls)?urls:[urls]){
   if(typeof u!=="string"||!/^turns:turn\.cloudflare\.com:(443|5349)\?transport=tcp$|^turn:turn\.cloudflare\.com:(3478|443)\?transport=udp$|^turn:turn\.cloudflare\.com:(3478|80)\?transport=tcp$/.test(u))continue;
   if(!out.some(x=>x.urls===u))out.push({urls:u,username,credential});
  }
 }
 const priority=(url:string)=>url==="turns:turn.cloudflare.com:443?transport=tcp"?0:url.startsWith("turns:")?1:url.endsWith("transport=udp")?2:3;
 return out.sort((a,b)=>priority(a.urls)-priority(b.urls)).slice(0,6);
}
