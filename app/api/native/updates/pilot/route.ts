import { NextResponse } from "next/server";
import { createClient } from "@supabase/supabase-js";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";
const headers = { "Cache-Control": "private, no-store", "Vary": "Authorization, X-Sera-ABI", "X-Content-Type-Options": "nosniff" };
const unavailable = () => NextResponse.json({available:false},{headers});
const repo = "Mahiruni/Sera";

/** Only authenticated eligible callers on a supported ABI discover the early-access release.
 * The signed public prerelease asset contains no secrets and grants no calling entitlement. */
export async function GET(request:Request) {
  try {
    const bearer=request.headers.get("authorization")||"";
    if(!/^Bearer [A-Za-z0-9._~-]+$/.test(bearer)||request.headers.get("x-sera-abi")!=="arm64-v8a")return unavailable();
    const url=process.env.NEXT_PUBLIC_SUPABASE_URL,key=process.env.NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY;
    if(!url||!key)return unavailable();
    const db=createClient(url,key,{global:{headers:{Authorization:bearer}},auth:{persistSession:false,autoRefreshToken:false}});
    const {data:{user},error:authError}=await db.auth.getUser(bearer.slice(7));
    if(authError||!user?.email_confirmed_at)return unavailable();
    // Reuse the database's actual calling authorization rather than client claims or email lists.
    const {data:conversations,error}=await db.from("professional_conversations").select("id").or("member_a.eq."+user.id+",member_b.eq."+user.id).limit(100);
    if(error||!conversations?.length)return unavailable();
    let allowed=false;
    for(let i=0;i<conversations.length&&!allowed;i+=10){
      const checks=await Promise.all(conversations.slice(i,i+10).map(c=>db.rpc("sera_call_relay_access",{p_conversation:c.id,p_issue:false})));
      allowed=checks.some(c=>!c.error&&(c.data==="cloudflare"||c.data==="coturn"));
    }
    if(!allowed)return unavailable();
    const res=await fetch("https://api.github.com/repos/"+repo+"/releases?per_page=20",{headers:{Accept:"application/vnd.github+json","User-Agent":"SERA-Android-Update"},cache:"no-store",signal:AbortSignal.timeout(7000)});
    if(!res.ok)return unavailable();
    const releases:unknown=await res.json();
    if(!Array.isArray(releases))return unavailable();
    const candidates=releases.flatMap((r)=>{
      if(!r?.prerelease||r.draft||typeof r.body!=="string"||!r.body.split("\n").includes("Channel: sera-calling-arm64"))return [];
      const match=/^android-v(\d+\.\d+\.\d+)$/.exec(r.tag_name||"");
      const code=Number(/Android versionCode:\s*(\d+)/.exec(r.body)?.[1]);
      if(!match||!Number.isSafeInteger(code)||code<94)return [];
      const versionName=match[1];
      const asset=Array.isArray(r.assets)?r.assets.find((a:{name?:string})=>a.name==="SERA-"+versionName+".apk"):null;
      const apkUrl="https://github.com/"+repo+"/releases/download/"+r.tag_name+"/SERA-"+versionName+".apk";
      const sha256=/^sha256:([a-fA-F0-9]{64})$/.exec(asset?.digest||"")?.[1]?.toLowerCase();
      if(!sha256||asset?.browser_download_url!==apkUrl||!Number.isSafeInteger(asset.size)||asset.size<=0||asset.size>12*1024*1024)return [];
      return [{available:true,versionCode:code,versionName,apkUrl,sha256,notes:"AURA voice calling · optimized ARM64 download",highlights:["AURA voice calling","Smaller ARM64 download"]}];
    }).sort((a,b)=>b.versionCode-a.versionCode);
    return candidates.length?NextResponse.json(candidates[0],{headers}):unavailable();
  } catch {return unavailable();}
}
