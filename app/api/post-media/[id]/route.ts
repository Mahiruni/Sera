import {serverClient} from "@/lib/supabase/server";
import {uuid} from "@/lib/social/validation";
export const dynamic="force-dynamic";
export async function GET(_:Request,{params}:{params:Promise<{id:string}>}){
 const {id}=await params;if(!uuid(id))return new Response(null,{status:404});
 const db=await serverClient();const {data:user}=await db.auth.getUser();if(!user.user)return new Response(null,{status:404});
 const {data:row,error}=await db.from("post_images").select("path").eq("id",id).maybeSingle();if(error||!row)return new Response(null,{status:404});
 const {data,error:downloadError}=await db.storage.from("sera-post-media").download(row.path);
 if(downloadError||!data)return new Response(null,{status:404});
 return new Response(data,{headers:{"Content-Type":data.type||"image/webp","Cache-Control":"private, no-store","X-Content-Type-Options":"nosniff"}});
}