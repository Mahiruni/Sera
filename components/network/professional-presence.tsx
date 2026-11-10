"use client";
import { useEffect, useState } from "react";
export function ProfessionalPresence({ member }: { member: string }) {
  const [online, setOnline] = useState(false);
  const [visible, setVisible] = useState(false);
  useEffect(() => {
    let active = true;
    const poll = async () => {
      if (document.visibilityState !== "visible") return;
      try {
        const res = await fetch("/api/network/presence?member="+encodeURIComponent(member),{cache:"no-store"});
        if (res.ok && active) setOnline(Boolean((await res.json()).online));
      } catch { if(active) setOnline(false); }
    };
    const heartbeat = () => {
      if (document.visibilityState !== "visible") return;
      void fetch("/api/network/presence",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({visibility:"connections"})}).catch(()=>{});
    };
    void poll();
    heartbeat();
    const a=window.setInterval(()=>{void poll();},15000);
    const b=window.setInterval(heartbeat,45000);
    document.addEventListener("visibilitychange",poll);
    return ()=>{active=false;window.clearInterval(a);window.clearInterval(b);document.removeEventListener("visibilitychange",poll);};
  },[member]);
  return <span role="status" aria-label={online?"Online now":"Offline or hidden"} style={{fontSize:12,color:online?"#087c73":"#68838c"}}>{online?"● Online now":"Offline or hidden"}</span>;
}
