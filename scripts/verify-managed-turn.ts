import {normalizeCloudflareIce} from "../lib/network/turn-contract";

// Only the production build may use production secrets. Never log credentials,
// response bodies, URLs containing the key ID, or raw exceptions.
async function verify(){
 if(process.env.VERCEL_ENV!=="production")return;
 const id=process.env.SERA_CLOUDFLARE_TURN_KEY_ID,token=process.env.SERA_CLOUDFLARE_TURN_API_TOKEN;
 if(!id||!token){console.log("SERA TURN preflight: not configured");return;}
 if(!/^[-a-zA-Z0-9_]{8,128}$/.test(id)){console.warn("SERA TURN preflight: invalid key ID format");return;}
 try{
  const response=await fetch("https://rtc.live.cloudflare.com/v1/turn/keys/"+id+"/credentials/generate-ice-servers",{
   method:"POST",headers:{Authorization:"Bearer "+token,"Content-Type":"application/json"},
   body:JSON.stringify({ttl:60}),cache:"no-store",redirect:"error",signal:AbortSignal.timeout(10000)
  });
  if(!response.ok){console.warn("SERA TURN preflight: provider HTTP "+response.status);return;}
  const servers=normalizeCloudflareIce(await response.json());
  if(!servers.length){console.warn("SERA TURN preflight: no supported relay endpoints");return;}
  console.log("SERA TURN preflight: PASS; "+servers.length+" approved authenticated relay endpoints; no media transmitted");
 }catch{console.warn("SERA TURN preflight: provider request failed");}
}
void verify();
