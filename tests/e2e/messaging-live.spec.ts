import {test,expect,type Browser,type Page} from "@playwright/test";
import {createClient,type SupabaseClient} from "@supabase/supabase-js";
import {PDFDocument} from "pdf-lib";

test.use({trace:"off",screenshot:"off"});
test.setTimeout(180000);
const url=process.env.NEXT_PUBLIC_SUPABASE_URL!;
const key=process.env.NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY!;
const baseURL=process.env.SERA_E2E_BASE_URL!;
const owner={email:process.env.SERA_E2E_OWNER_EMAIL!,password:process.env.SERA_E2E_OWNER_PASSWORD!};
const connected={email:process.env.SERA_E2E_CONNECTED_EMAIL!,password:process.env.SERA_E2E_CONNECTED_PASSWORD!};
const unrelated={email:process.env.SERA_E2E_UNRELATED_EMAIL!,password:process.env.SERA_E2E_UNRELATED_PASSWORD!};
type Credentials={email:string;password:string};
async function client(c:Credentials){
 const db=createClient(url,key,{auth:{persistSession:false,autoRefreshToken:false}});
 const r=await db.auth.signInWithPassword(c);
 if(r.error||!r.data.user?.email_confirmed_at)throw new Error("Dedicated verified messaging QA account could not authenticate.");
 return {db,id:r.data.user.id};
}
async function browserSession(browser:Browser,c:Credentials,thread:string,width:number){
 const context=await browser.newContext({baseURL,viewport:{width,height:844}});
 const page=await context.newPage();
 await page.goto("/sign-in?next="+encodeURIComponent("/network/messages?thread="+thread));
 await page.locator('input[name="email"]').fill(c.email);
 await page.locator('input[name="password"]').fill(c.password);
 await page.locator('form').filter({has:page.locator('input[name="password"]')}).locator('button[type="submit"]').click();
 await page.waitForURL(u=>!u.pathname.includes("sign-in"),{timeout:30000});
 await page.goto("/network/messages?thread="+thread);
 await expect(page.locator(".sera-chat-thread")).toBeVisible({timeout:30000});
 return {context,page};
}
const row=(page:Page,body:string)=>page.locator(".sera-chat-message-row").filter({has:page.locator("p",{hasText:body})});
async function send(page:Page,body:string){await page.getByRole("textbox",{name:"Message",exact:true}).fill(body);await page.getByRole("button",{name:"Send message",exact:true}).click();}
async function cleanup(db:SupabaseClient,thread:string,sender:string,markers:string[]){
 for(const marker of markers){const r=await db.from("professional_messages").select("id").eq("conversation_id",thread).eq("sender_id",sender).eq("body",marker);for(const m of r.data??[]){const removed=await db.rpc("sera_unsend_message",{p_message:m.id});if(Array.isArray(removed.data)&&removed.data.length)await db.storage.from("professional-message-files").remove(removed.data);}}
}
test("two authenticated accounts: send, read, reply, react, private PDF, offline and isolation",async({browser})=>{
 const a=await client(owner),b=await client(connected),c=await client(unrelated);
 expect(a.id).toBe(process.env.SERA_E2E_OWNER_ID);
 expect(new Set([a.id,b.id,c.id]).size).toBe(3);
 const [member_a,member_b]=[a.id,b.id].sort();
 const existing=await a.db.from("professional_conversations").select("id").eq("member_a",member_a).eq("member_b",member_b).maybeSingle();
 if(existing.error)throw new Error("QA conversation lookup failed.");
 let thread=existing.data?.id;
 if(!thread){
  const relationship=await a.db.from("professional_connection_requests").select("requester_id").eq("status","accepted").or("and(requester_id.eq."+a.id+",recipient_id.eq."+b.id+"),and(requester_id.eq."+b.id+",recipient_id.eq."+a.id+")").limit(1);
  if(!relationship.data?.length)throw new Error("QA accounts must already be connected; no unrelated conversation will be created.");
  const created=await a.db.from("professional_conversations").insert({member_a,member_b}).select("id").single();
  if(created.error||!created.data)throw new Error("QA conversation could not be created.");
  thread=created.data.id;
 }
 if(!thread)throw new Error("Missing dedicated QA conversation.");
 const id=thread;
 const marker="SERA-QA-"+crypto.randomUUID(),reply=marker+"-reply",offline=marker+"-offline",pdfName=marker+".pdf";
 const markersA=[marker,offline,pdfName],markersB=[reply];
 const one=await browserSession(browser,owner,id,1280),two=await browserSession(browser,connected,id,375);
 try{
  await send(one.page,marker);
  await expect(row(two.page,marker)).toHaveCount(1,{timeout:50000});
  await expect(row(one.page,marker).getByLabel("Read",{exact:true})).toBeVisible({timeout:50000});
  await row(two.page,marker).getByRole("button",{name:"Message actions"}).click();
  await row(two.page,marker).getByRole("button",{name:"React",exact:true}).click();
  await two.page.getByRole("button",{name:"React 👍",exact:true}).click();
  await expect(row(one.page,marker).getByRole("button",{name:"👍 1"})).toBeVisible({timeout:50000});
  await row(two.page,marker).getByRole("button",{name:"Message actions"}).click();
  await row(two.page,marker).getByRole("button",{name:"Reply",exact:true}).click();
  await expect(two.page.locator(".sera-chat-reply-preview")).toBeVisible();
  await send(two.page,reply);
  await expect(row(one.page,reply)).toHaveCount(1,{timeout:50000});
  await expect(row(one.page,reply).locator(".sera-chat-quote")).toBeVisible();
  const pdf=await PDFDocument.create();pdf.addPage();const bytes=await pdf.save();
  await one.page.locator('input[type="file"]').setInputFiles({name:pdfName,mimeType:"application/pdf",buffer:Buffer.from(bytes)});
  await send(one.page,pdfName);
  const file=two.page.getByRole("link",{name:new RegExp(pdfName.replaceAll(".","\\."))});
  await expect(file).toBeVisible({timeout:50000});
  const href=await file.getAttribute("href");if(!href)throw new Error("PDF link missing");
  const result=await two.page.request.get(href);expect(result.ok()).toBeTruthy();expect((await result.body()).subarray(0,5).toString()).toBe("%PDF-");
  await one.context.setOffline(true);await send(one.page,offline);
  await expect(one.page.locator(".sera-chat-bubble.is-pending").filter({hasText:offline})).toBeVisible();
  await one.context.setOffline(false);await expect(row(two.page,offline)).toHaveCount(1,{timeout:50000});
  const outsider=await c.db.from("professional_messages").select("id").eq("conversation_id",id).limit(1);
  expect(outsider.error||!outsider.data?.length).toBeTruthy();
  await row(one.page,marker).getByRole("button",{name:"Message actions"}).click();
  await row(one.page,marker).getByRole("button",{name:"Unsend",exact:true}).click();
  await one.page.getByRole("dialog",{name:"Unsend message"}).getByRole("button",{name:"Unsend",exact:true}).click();
  await expect(row(two.page,marker)).toHaveCount(0,{timeout:50000});
 }finally{
  await one.context.setOffline(false);
  await cleanup(a.db,id,a.id,markersA);await cleanup(b.db,id,b.id,markersB);
  await one.context.close();await two.context.close();
 }
});
