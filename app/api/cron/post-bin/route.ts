import {NextRequest,NextResponse} from "next/server";
import {moderationWriter} from "@/lib/moderation/writer";
export const maxDuration=60;
export async function GET(req:NextRequest){
 const secret=process.env.CRON_SECRET;
 if(!secret||req.headers.get("authorization")!==`Bearer ${secret}`)return new NextResponse(null,{status:401});
 const db=moderationWriter();if(!db)return NextResponse.json({error:"Cleanup unavailable"},{status:503});
 const identity=await db.rpc("sera_identity_cleanup_candidates");
 let identityRemoved=0;let identityCleanupFailed=Boolean(identity.error);
 if(!identity.error){const paths=(identity.data??[]).map((r:{path:string})=>r.path).filter((p:string)=>/^[0-9a-f-]{36}\/[0-9a-f-]{36}\/evidence$/i.test(p));if(paths.length){const result=await db.storage.from("sera-identity-private").remove(paths);identityCleanupFailed=Boolean(result.error);identityRemoved=result.data?.length??0;}}
 const purge=await db.rpc("sera_purge_expired_posts");if(purge.error)return NextResponse.json({error:"Purge failed"},{status:503});
 const {data,error}=await db.from("sera_post_media_cleanup").select("path").order("created_at").limit(100);
 if(error)return NextResponse.json({error:"Queue unavailable"},{status:503});
 let removed=0;
 for(const row of data||[]){const path=String(row.path);if(!/^[0-9a-f-]{36}\/[0-9a-f-]{36}\/[^/]+$/i.test(path))continue;
 const refs=await db.from("post_images").select("id").eq("path",path).limit(1);if(refs.error||refs.data?.length)continue;
 const result=await db.storage.from("sera-post-media").remove([path]);if(result.error)continue;
 const done=await db.from("sera_post_media_cleanup").delete().eq("path",path);if(!done.error)removed++;
 }
 const chat=await db.rpc("sera_claim_abandoned_chat_uploads");
 let chatRemoved=0;let chatCleanupFailed=Boolean(chat.error);
 if(!chat.error){
   for(const row of (chat.data??[]) as {path:string;object_id:string}[]){
     if(!/^[0-9a-f-]{36}\/[0-9a-f-]{36}\/[0-9a-f-]{36}\.(jpg|jpeg|png|webp|pdf|m4a|mp4|webm|ogg|mp3)$/.test(row.path))continue;
     const result=await db.storage.from("professional-message-files").remove([row.path]);
     if(result.error){chatCleanupFailed=true;continue;}
     const finished=await db.rpc("sera_finish_abandoned_chat_upload",{p_path:row.path,p_object:row.object_id});
     if(finished.error||finished.data!==true)chatCleanupFailed=true;else chatRemoved++;
   }
 }
 return NextResponse.json({chatRemoved,chatCleanupFailed,purged:purge.data,removed,queued:data?.length||0,identityRemoved,identityCleanupFailed},{headers:{"Cache-Control":"no-store"}});
}