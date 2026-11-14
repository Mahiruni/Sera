import {createServer} from "node:http";
import {config,authorize,createSessions,exchange} from "./oidc.mjs";
const c=config(),sessions=createSessions();
const page=(title,body)=>"<!doctype html><html lang='en'><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'><title>SERA Fayda sandbox</title><style>body{font:16px system-ui;color:#173d40;background:#f2f8f7;max-width:560px;margin:60px auto;padding:24px}main{background:white;border:1px solid #dce9e6;border-radius:20px;padding:28px}h1{font-size:30px}a,button{display:inline-block;background:#087e83;color:white;border:0;border-radius:12px;padding:14px 20px;font:inherit;text-decoration:none}small{color:#486568}label{display:block;margin:20px 0}</style><main><small>SERA.et · LOCAL TEST ONLY</small><h1>"+title+"</h1>"+body+"</main></html>";
const headers={"Content-Type":"text/html; charset=utf-8","Cache-Control":"no-store","Referrer-Policy":"no-referrer","Content-Security-Policy":"default-src 'none'; style-src 'unsafe-inline'; form-action 'self'; base-uri 'none'; frame-ancestors 'none'","X-Content-Type-Options":"nosniff"};
const server=createServer(async(req,res)=>{
 const send=(status,body)=>{res.writeHead(status,headers);res.end(body);};
 if(req.headers.host!=="localhost:3000")return send(403,page("Local access only","Open http://localhost:3000."));
 const u=new URL(req.url,"http://localhost:3000");
 try{
 if(req.method==="GET"&&u.pathname==="/")return send(200,page("Verify with Fayda","<p>Test the official sandbox using published synthetic identities only. Never enter a real ID. No SERA account or verified badge will be created.</p><form method='post' action='/start'><label><input type='checkbox' name='consent' value='yes' required> I agree to a sandbox test.</label><button>Continue to Fayda</button></form>"));
 if(req.method==="POST"&&u.pathname==="/start"){
  if(req.headers.origin!=="http://localhost:3000")return send(403,page("Request blocked","Invalid origin."));
  let body="";for await(const chunk of req){body+=chunk;if(body.length>1024)return send(400,page("Invalid request","Try again."));}
  if(new URLSearchParams(body).get("consent")!=="yes")return send(400,page("Consent required","<a href='/'>Return</a>"));
  const s=sessions.start();res.writeHead(303,{...headers,Location:authorize(c,s),"Set-Cookie":"sera_fayda="+s.id+"; HttpOnly; SameSite=Lax; Path=/callback; Max-Age=300"});return res.end();
 }
 if(req.method==="GET"&&u.pathname==="/callback"){
  const id=(req.headers.cookie||"").split(";").map(x=>x.trim()).find(x=>x.startsWith("sera_fayda="))?.slice(11);
  const s=sessions.consume(id,u.searchParams.get("state"));res.setHeader("Set-Cookie","sera_fayda=; HttpOnly; SameSite=Lax; Path=/callback; Max-Age=0");
  if(u.searchParams.has("error")||!u.searchParams.get("code"))throw Error("Authentication cancelled");
  const result=await exchange(c,s,u.searchParams.get("code"));
  return send(200,page("Sandbox authentication successful","<p>The provider's signed identity response was validated.</p><p>Sandbox only: no real verification badge was issued.</p><p>Name claim available: "+(result.nameAvailable?"Yes":"No")+"</p><a href='/'>Start another test</a>"));
 }
 return send(404,page("Not found","<a href='/'>Return</a>"));
 }catch{return send(400,page("Verification could not complete","<p>Check sandbox configuration, provider availability and the registered callback. No identity data was saved.</p><a href='/'>Try again</a>"));}
});
server.listen(3000,"localhost",()=>console.log("SERA Fayda sandbox: http://localhost:3000 (test only)"));
