import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {runInNewContext} from "node:vm";
import * as crypto from "node:crypto";
import ts from "typescript";
import {normalizeCloudflareIce} from "../lib/network/turn-contract";

// Execute the actual server module with isolated, in-memory dependencies.
// No real user, Cloudflare token, network or Supabase service key is needed.
const source=ts.transpileModule(readFileSync(new URL("../lib/network/call-relay.ts",import.meta.url),"utf8"),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText;
type Row={member_id:string;sealed:string;reuse_until:string};
type Config={enabled:boolean;iceServers?:ReturnType<typeof normalizeCloudflareIce>};
function fixture(){
 const rows=new Map<string,Row>();
 const state={quota:true,authorized:true,verified:true,providerOk:true,claims:0,requests:0,now:Date.now()};
 const env={NEXT_PUBLIC_SUPABASE_URL:"https://example.invalid",SERA_SUPABASE_SERVICE_ROLE_KEY:"fixture-only",SERA_CLOUDFLARE_TURN_KEY_ID:"test-key-id",SERA_CLOUDFLARE_TURN_API_TOKEN:"fixture-token-only"};
 const store={from:(table:string)=>{
  assert.equal(table,"sera_relay_credential_cache");
  return {select:()=>({eq:(field:string,id:string)=>({gt:(_field:string,until:string)=>({maybeSingle:async()=>({data:rows.get(id)&&rows.get(id)!.reuse_until>until?rows.get(id):null,error:null})})})}),upsert:async(row:Row)=>{rows.set(row.member_id,{...row});return {error:null};}};
 }};
 const db=(id="member-a")=>({auth:{getUser:async()=>({data:{user:{id,email_confirmed_at:state.verified?"confirmed":null}},error:null})},rpc:async(name:string,args:{p_issue:boolean})=>{assert.equal(name,"sera_call_relay_access");if(!state.authorized)return {data:null,error:null};if(args.p_issue){state.claims++;if(!state.quota)return {data:null,error:null};}return {data:"cloudflare",error:null};}});
 class Clock extends Date{constructor(value?:number){super(value??state.now);}static now(){return state.now;}}
 const api={exports:{} as {callRelayConfig:(client:ReturnType<typeof db>,conversation:string,issue:boolean)=>Promise<Config>}};
 runInNewContext(source,{exports:api.exports,require:(name:string)=>{
  if(name==="server-only")return {};
  if(name==="@supabase/supabase-js")return {createClient:()=>store};
  if(name==="node:crypto")return crypto;
  if(name==="@/lib/network/turn-contract")return {normalizeCloudflareIce};
  throw new Error("Unexpected import: "+name);
 },Buffer,Date:Clock,AbortSignal,process:{env},console:{warn:()=>{}},fetch:async()=>{state.requests++;return {ok:state.providerOk,status:503,json:async()=>({iceServers:[{urls:"turns:turn.cloudflare.com:443?transport=tcp",username:"user-"+state.requests,credential:"credential-"+state.requests}]})};}});
 return {rows,state,env,call:(id="member-a",issue=true)=>api.exports.callRelayConfig(db(id),"conversation",issue)};
}
test("availability checks do not mint credentials; repeated calls reuse even at quota",async()=>{
 const f=fixture();assert.equal((await f.call("member-a",false)).enabled,true);assert.equal(f.state.claims,0);
 const first=await f.call();assert.equal(first.enabled,true);assert.equal(f.state.requests,1);
 f.state.quota=false;for(let i=0;i<25;i++)assert.deepEqual((await f.call()).iceServers,first.iceServers);
 assert.equal(f.state.claims,1);assert.equal(f.state.requests,1);
});
test("expired cache fails closed at quota and recovers when capacity returns",async()=>{
 const f=fixture();await f.call();f.state.now+=46*60*1000;f.state.quota=false;
 assert.equal((await f.call()).enabled,false);assert.equal(f.state.requests,1);
 f.state.quota=true;assert.equal((await f.call()).enabled,true);assert.equal(f.state.requests,2);
});
test("authorization is rechecked even on cache hit",async()=>{
 const f=fixture();await f.call();f.state.authorized=false;assert.equal((await f.call()).enabled,false);
 f.state.authorized=true;f.state.verified=false;assert.equal((await f.call()).enabled,false);assert.equal(f.state.requests,1);
});
test("encrypted cache is member-bound and rejects rotated keys or tampering",async()=>{
 const f=fixture();await f.call();const row=f.rows.get("member-a")!;assert.ok(!row.sealed.includes("credential-1"));
 f.rows.set("member-b",{...row,member_id:"member-b"});f.state.quota=false;assert.equal((await f.call("member-b")).enabled,false);
 f.env.SERA_CLOUDFLARE_TURN_API_TOKEN="rotated-fixture-token";assert.equal((await f.call()).enabled,false);
 f.env.SERA_CLOUDFLARE_TURN_API_TOKEN="fixture-token-only";f.rows.set("member-a",{...row,sealed:row.sealed.slice(0,-4)+"AAAA"});assert.equal((await f.call()).enabled,false);
 assert.equal(f.state.requests,1);
});
test("simultaneous requests share issuance within one server instance",async()=>{
 const f=fixture();const results=await Promise.all(Array.from({length:12},()=>f.call()));
 assert.ok(results.every(x=>x.enabled));assert.equal(f.state.claims,1);assert.equal(f.state.requests,1);
});
test("provider failure is not cached and can recover on retry",async()=>{
 const f=fixture();f.state.providerOk=false;assert.equal((await f.call()).enabled,false);assert.equal(f.rows.size,0);
 f.state.providerOk=true;assert.equal((await f.call()).enabled,true);assert.equal(f.state.requests,2);
});
