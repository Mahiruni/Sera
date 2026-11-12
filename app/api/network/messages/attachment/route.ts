import { currentUser,serverClient } from "@/lib/supabase/server";
import { validMessageId,isChatMime,FILE_LIMIT } from "@/lib/network/message-contract";
export const dynamic="force-dynamic";
export async function GET(request:Request){
  const user=await currentUser();
  if(!user?.email_confirmed_at)return new Response(null,{status:401});
  const id=new URL(request.url).searchParams.get("id");
  if(!validMessageId(id))return new Response(null,{status:400});
  const db=await serverClient();
  // RLS rechecks membership and unsend status on every download. No public or long-lived URL.
  const {data:meta}=await db.from("professional_message_attachments").select("storage_path,file_name,mime_type,byte_size").eq("id",id).maybeSingle();
  if(!meta||!isChatMime(meta.mime_type)||meta.byte_size>FILE_LIMIT)return new Response(null,{status:404});
  const {data:blob,error}=await db.storage.from("professional-message-files").download(meta.storage_path);
  if(error||!blob||blob.size!==meta.byte_size)return new Response(null,{status:404});
  const headers=new Headers({"Content-Type":meta.mime_type,"Cache-Control":"private, no-store","X-Content-Type-Options":"nosniff","Cross-Origin-Resource-Policy":"same-origin","Accept-Ranges":"bytes","Content-Disposition":(meta.mime_type==="application/pdf"?"attachment":"inline")+"; filename*=UTF-8''"+encodeURIComponent(meta.file_name)});
  const range=request.headers.get("range");
  if(range){
    const m=/^bytes=(\d*)-(\d*)$/.exec(range);
    if(!m||(!m[1]&&!m[2]))return new Response(null,{status:416,headers:{"Content-Range":"bytes */"+blob.size}});
    const start=m[1]?Number(m[1]):Math.max(0,blob.size-Number(m[2]));
    const end=m[1]&&m[2]?Math.min(blob.size-1,Number(m[2])):blob.size-1;
    if(!Number.isSafeInteger(start)||!Number.isSafeInteger(end)||start<0||start>=blob.size||end<start)return new Response(null,{status:416,headers:{"Content-Range":"bytes */"+blob.size}});
    headers.set("Content-Range","bytes "+start+"-"+end+"/"+blob.size);headers.set("Content-Length",String(end-start+1));
    return new Response(blob.slice(start,end+1).stream(),{status:206,headers});
  }
  headers.set("Content-Length",String(blob.size));return new Response(blob.stream(),{headers});
}
