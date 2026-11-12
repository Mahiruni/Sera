"use server";
import {serverClient} from "@/lib/supabase/server";
import {revalidatePath} from "next/cache";
export type IdentityState={error?:string;success?:string};
const bucket="sera-identity-private";
const uuid=(v:unknown):v is string=>typeof v==="string"&&/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(v);
export async function submitIdentity(_:IdentityState,form:FormData):Promise<IdentityState>{
 const db=await serverClient();const {data:{user}}=await db.auth.getUser();
 if(!user?.email_confirmed_at)return {error:"Sign in with a confirmed email."};
 const type=form.get("type"),file=form.get("evidence");
 if(!["national_id","passport","driver_license"].includes(String(type))||form.get("consent")!=="yes"||!(file instanceof File)||file.size<1||file.size>3*1024*1024||!["image/jpeg","image/png","application/pdf"].includes(file.type))return {error:"Choose a supported document (JPG, PNG or PDF, up to 3 MB) and confirm consent."};
 const bytes=new Uint8Array(await file.arrayBuffer());
 const valid=file.type==="application/pdf"?new TextDecoder().decode(bytes.slice(0,5))==="%PDF-":file.type==="image/jpeg"?bytes[0]===255&&bytes[1]===216&&bytes[2]===255:bytes[0]===137&&bytes[1]===80&&bytes[2]===78&&bytes[3]===71;
 if(!valid)return {error:"The document format is invalid."};
 const {data,error}=await db.rpc("sera_identity_start",{p_document_type:type,p_consent:true});
 if(error||!data?.id||!data?.path)return {error:"Unable to open a request. Check your current request or try later."};
 const uploaded=await db.storage.from(bucket).upload(data.path,bytes,{contentType:file.type,upsert:false});
 if(uploaded.error)return {error:"Upload failed or evidence already exists. Submit the existing draft or withdraw it before retrying."};
 const result=await db.rpc("sera_identity_submit",{p_id:data.id});
 revalidatePath("/settings/verification");
 return result.error?{error:"Document uploaded privately; finish submitting the saved draft."}:{success:"Your identity is awaiting review."};
}
export async function manageIdentity(_:IdentityState,form:FormData):Promise<IdentityState>{
 const db=await serverClient();const {data:{user}}=await db.auth.getUser();const id=form.get("id"),intent=form.get("intent");
 if(!user?.email_confirmed_at||!uuid(id))return {error:"Invalid request."};
 const {data:current}=await db.rpc("sera_identity_status");
 const {data:history}=await db.rpc("sera_identity_history");
 const status=current?.id===id?current:(history??[]).find((r:{id:string})=>r.id===id);
 if(!status)return {error:"Request unavailable."};
 if(intent==="submit"){const {error}=await db.rpc("sera_identity_submit",{p_id:id});if(error)return {error:"The draft needs an uploaded document."};}
 else if(intent==="withdraw"){const {error}=await db.rpc("sera_identity_withdraw",{p_id:id});if(error)return {error:"Unable to withdraw."};const removed=await db.storage.from(bucket).remove([status.path]);if(removed.error){revalidatePath("/settings/verification");return {error:"Withdrawn. Use Remove document to retry evidence removal."};}}
 else if(intent==="delete"){if(!["approved","expired","rejected","withdrawn","revoked"].includes(status.status))return {error:"Withdraw an active request first."};const {error}=await db.storage.from(bucket).remove([status.path]);if(error)return {error:"Unable to remove document."};}
 else return {error:"Invalid action."};
 revalidatePath("/settings/verification");revalidatePath("/feed");return {success:"Verification updated."};
}
export async function reviewIdentity(_:IdentityState,form:FormData):Promise<IdentityState>{
 const db=await serverClient();const {data:{user}}=await db.auth.getUser();const id=form.get("id"),decision=form.get("decision"),reason=String(form.get("reason")||"").trim();
 if(!user?.email_confirmed_at||!["admin","moderator"].includes(String(user.app_metadata?.role))||!uuid(id)||!["approved","rejected","revoked"].includes(String(decision))||reason.length<10||reason.length>1000)return {error:"A valid review and explanation are required."};
 const {error}=await db.rpc("sera_identity_review",{p_id:id,p_decision:decision,p_reason:reason,p_confirmed:form.get("confirmed")==="yes"});
 if(error)return {error:"Review failed. Confirm access, evidence and request status."};
 revalidatePath("/admin/verification");revalidatePath("/feed");return {success:"Decision recorded."};
}
