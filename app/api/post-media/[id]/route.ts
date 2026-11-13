import {serverClient} from "@/lib/supabase/server";
import {uuid} from "@/lib/social/validation";
import {createClient} from "@supabase/supabase-js";
import sharp from "sharp";
export const dynamic="force-dynamic";
export const runtime="nodejs";
const sizes=new Set([480,800,1200]);
const privateHeaders={"Cache-Control":"private, no-store","X-Content-Type-Options":"nosniff","Vary":"Authorization, Cookie"};
export async function GET(request:Request,{params}:{params:Promise<{id:string}>}){
 const {id}=await params;if(!uuid(id))return new Response(null,{status:404});
 const bearer=request.headers.get("authorization");
 let db=await serverClient();
 if(bearer){
   const url=process.env.NEXT_PUBLIC_SUPABASE_URL,key=process.env.NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY;
   if(!url||!key||!/^Bearer [A-Za-z0-9._~-]+$/.test(bearer))return new Response(null,{status:404});
   db=createClient(url,key,{global:{headers:{Authorization:bearer}},auth:{persistSession:false,autoRefreshToken:false}});
 }
 const {data:user}=await db.auth.getUser(bearer?bearer.slice(7):undefined);if(!user.user)return new Response(null,{status:404});
 const {data:row,error}=await db.from("post_images").select("path").eq("id",id).maybeSingle();if(error||!row)return new Response(null,{status:404});
 const {data,error:downloadError}=await db.storage.from("sera-post-media").download(row.path);
 if(downloadError||!data)return new Response(null,{status:404});
 const w=Number(new URL(request.url).searchParams.get("w"));
 // Resized variants are never public or shared-cached; original access checks still apply.
 if(sizes.has(w)){
   try{
     const image=await sharp(new Uint8Array(await data.arrayBuffer()),{limitInputPixels:16000000,animated:false})
       .rotate().resize({width:w,height:w,fit:"inside",withoutEnlargement:true}).webp({quality:w===480?75:82}).toBuffer();
     if(image.byteLength<data.size)return new Response(new Uint8Array(image),{headers:{...privateHeaders,"Content-Type":"image/webp"}});
   }catch{/* Keep the original available if a preview cannot be produced. */}
 }
 return new Response(data,{headers:{...privateHeaders,"Content-Type":data.type||"image/webp"}});
}
