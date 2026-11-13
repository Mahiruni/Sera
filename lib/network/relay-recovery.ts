/** Only reuse provider-issued TURN endpoints, never synthesize credentials or downgrade to STUN. */
export function secureRelayAttempts(servers:RTCIceServer[]){
 const valid=servers.flatMap(s=>(Array.isArray(s.urls)?s.urls:[s.urls]).filter(u=>/^turns?:/i.test(u)&&typeof s.username==="string"&&s.username.length>0&&typeof s.credential==="string"&&s.credential.length>0).map(urls=>({...s,urls})));
 if(!valid.length)return [];
 const tls=valid.filter(s=>s.urls.startsWith("turns:")).sort((a,b)=>Number(!a.urls.includes(":443?"))-Number(!b.urls.includes(":443?")));
 return [{servers:valid,budgetMs:17000,label:"Finding secure connection…"},{servers:tls.length?tls:valid,budgetMs:15000,label:"Trying alternative secure connection…"}];
}
