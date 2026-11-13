import { NextResponse } from "next/server";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";
const REPO="Mahiruni/Sera";
const headers={"Cache-Control":"no-store","Vary":"X-Sera-ABI","X-Content-Type-Options":"nosniff"};
const unavailable=()=>NextResponse.json({available:false},{headers});
const abis=new Set(["arm64-v8a","armeabi-v7a","x86","x86_64"]);

type Asset={name:string;browser_download_url:string;digest?:string};
type Release={tag_name:string;body?:string;draft?:boolean;prerelease?:boolean;assets?:Asset[]};
function parseRelease(r:Release){
  if(!r||r.draft||r.prerelease)return null;
  const versionName=/^android-v(\d+\.\d+\.\d+)$/.exec(r.tag_name||"")?.[1];
  const versionCode=Number(/Android versionCode:\s*(\d+)/.exec(r.body||"")?.[1]);
  if(!versionName||!Number.isSafeInteger(versionCode)||versionCode<2)return null;
  const prefix="https://github.com/"+REPO+"/releases/download/"+r.tag_name+"/SERA-"+versionName;
  function asset(abi?:string){
    const suffix=abi?"-"+abi:"";
    const url=prefix+suffix+".apk";
    const a=Array.isArray(r.assets)?r.assets.find(x=>x.name==="SERA-"+versionName+suffix+".apk"&&x.browser_download_url===url):undefined;
    const sha256=/^sha256:([a-fA-F0-9]{64})$/.exec(a?.digest||"")?.[1]?.toLowerCase();
    return sha256?{url,sha256}:null;
  }
  if(!asset())return null;
  return {versionName,versionCode,asset,body:r.body||""};
}

/** One stable channel: legacy clients receive universal; capable clients request a matching ABI. */
export async function GET(request:Request){
  try{
    const res=await fetch("https://api.github.com/repos/"+REPO+"/releases?per_page=50",{headers:{Accept:"application/vnd.github+json","User-Agent":"SERA-Android-Update"},cache:"no-store",signal:AbortSignal.timeout(7000)});
    if(!res.ok)return unavailable();
    const releases=await res.json() as Release[];
    if(!Array.isArray(releases))return unavailable();
    const latest=releases.map(parseRelease).filter(x=>x!==null).sort((a,b)=>b.versionCode-a.versionCode)[0];
    if(!latest)return unavailable();
    const abi=request.headers.get("x-sera-abi")||"";
    const variant=abis.has(abi)?latest.asset(abi):null;
    const selected=variant||latest.asset();
    if(!selected)return unavailable();
    const {versionName,versionCode}=latest;
    const highlights=latest.body.split("\n").filter(line=>line.startsWith("Highlight: ")).map(line=>line.slice(11).trim()).filter(line=>line.length<=48&&line.split(/\s+/).length<=4&&/^[a-zA-Z0-9 &-]+$/.test(line)).slice(0,8);
    const summary=highlights.length?highlights:["Signed SERA update"];
    return NextResponse.json({available:true,versionCode,versionName,apkUrl:variant?selected.url:"https://sera.hisabtech.com/downloads/sera-android.apk?version="+versionName,sha256:selected.sha256,notes:summary.join(" · "),highlights:summary},{headers});
  }catch{return unavailable();}
}
