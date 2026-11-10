"use client";
import { useEffect, useState } from "react";
export function ProfessionalPresence({ member }: { member: string }) {
  const [online, setOnline] = useState(false);
  const [sharing, setSharing] = useState(false);
  useEffect(() => {
    let active = true;
    const poll = async () => {
      if (document.visibilityState !== "visible") return;
      try {
        const res = await fetch("/api/network/presence?member="+encodeURIComponent(member),{cache:"no-store"});
        if (res.ok && active) setOnline(Boolean((await res.json()).online));
      } catch { if(active) setOnline(false); }
    };
    void poll();

    const a=window.setInterval(()=>{void poll();},15000);

    document.addEventListener("visibilitychange",poll);
    return ()=>{active=false;window.clearInterval(a);document.removeEventListener("visibilitychange",poll);};
  },[member]);
  useEffect(() => {
    if (!sharing) return;
    const heartbeat = () => {
      if (document.visibilityState !== "visible") return;
      void fetch("/api/network/presence",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({visibility:"connections"})}).catch(()=>{});
    };
    heartbeat();
    const interval=window.setInterval(heartbeat,45000);
    return ()=>window.clearInterval(interval);
  },[sharing]);
  return <span style={{display:"inline-flex",alignItems:"center",gap:12,flexWrap:"wrap"}}><span role="status" aria-label={online?"Online now":"Offline or hidden"} style={{fontSize:12,color:online?"#087c73":"#68838c"}}>{online?"● Online now":"Offline or hidden"}</span><button type="button" onClick={()=>{const next=!sharing;setSharing(next);if(!next)void fetch("/api/network/presence",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({visibility:"hidden"})}).catch(()=>{});}} style={{border:"1px solid #b4d8df",borderRadius:12,padding:"5px 10px",fontSize:12}}>{sharing?"Hide my online status":"Share my online status"}</button></span>;
}
