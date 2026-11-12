import {NextRequest,NextResponse} from "next/server";
import {serverClient} from "@/lib/supabase/server";
import {moderationWriter} from "@/lib/moderation/writer";
export const dynamic="force-dynamic";
export async function GET(_:NextRequest,{params}:{params:Promise<{id:string}>}){
 const {id}=await params;
 if(!/^[0-9a-f-]{36}$/i.test(id))return new NextResponse(null,{status:404});
 const db=await serverClient();const {data:{user}}=await db.auth.getUser();
 if(!user?.email_confirmed_at||!["admin","moderator"].includes(String(user.app_metadata?.role)))return new NextResponse(null,{status:404});
 const {data:path,error}=await db.rpc("sera_identity_evidence_path",{p_id:id});
 if(error||typeof path!=="string")return new NextResponse(null,{status:404});
 const writer=moderationWriter();if(!writer)return new NextResponse(null,{status:503});
 // Only the audited reviewer RPC can authorize a service-signed evidence URL.
 const result=await writer.storage.from("sera-identity-private").createSignedUrl(path,60,{download:"identity-evidence"});
 if(result.error||!result.data?.signedUrl)return new NextResponse(null,{status:404});
 const response=NextResponse.redirect(result.data.signedUrl);response.headers.set("Cache-Control","private, no-store");response.headers.set("Referrer-Policy","no-referrer");return response;
}
