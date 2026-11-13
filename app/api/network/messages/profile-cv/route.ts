import {currentUser,serverClient} from "@/lib/supabase/server";
import {accountAssets,ASSET_BUCKET,ownedPath} from "@/lib/account/assets";
import {FILE_LIMIT,hasFileSignature} from "@/lib/network/message-contract";
export const dynamic="force-dynamic";
export async function GET(){
  const user=await currentUser();if(!user?.email_confirmed_at)return new Response(null,{status:401});
  try{
    const a=await accountAssets(user.id);
    const branded=ownedPath(a?.branded_path,user.id);
    const path=branded?a!.branded_path:a?.cv_path;
    if(!ownedPath(path,user.id))return new Response(null,{status:404});
    const db=await serverClient();const {data,error}=await db.storage.from(ASSET_BUCKET).download(path!);
    if(error||!data||data.size<5||data.size>FILE_LIMIT||!hasFileSignature(new Uint8Array(await data.slice(0,5).arrayBuffer()),"application/pdf"))return new Response(null,{status:422});
    const filename=(branded?"SERA-CV.pdf":a?.cv_name||"CV.pdf").replace(/[\x00-\x1f\x7f/\\]/g,"_").slice(0,180);
    return new Response(data.stream(),{headers:{"Content-Type":"application/pdf","Content-Length":String(data.size),"Cache-Control":"private, no-store","X-Content-Type-Options":"nosniff","Cross-Origin-Resource-Policy":"same-origin","Content-Disposition":"attachment; filename*=UTF-8''"+encodeURIComponent(filename),"X-Sera-Cv-Name":encodeURIComponent(filename)}});
  }catch{return new Response(null,{status:503});}
}
