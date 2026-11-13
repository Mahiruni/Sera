import test from "node:test";
import assert from "node:assert/strict";
import {testDatabase} from "./helpers/database.mjs";
test("notification recovery is private, idempotent, lease-protected and retryable",async()=>{
 const db=await testDatabase();
 try{
 const user="11111111-1111-4111-8111-111111111111",device="22222222-2222-4222-8222-222222222222",event="33333333-3333-4333-8333-333333333333",thread="44444444-4444-4444-8444-444444444444";
 await db.exec(`insert into auth.users(id,email) values('${user}','queue@example.test'); insert into public.professional_push_devices(id,user_id,token,platform) values('${device}','${user}','synthetic-device-token-only','android');`);
 const result=await db.query<{n:number}>(`select public.sera_enqueue_message_push('${event}','message','${user}','${thread}') as n`);
 assert.equal(result.rows[0].n,1);
 assert.equal((await db.query<{n:number}>(`select public.sera_enqueue_message_push('${event}','message','${user}','${thread}') as n`)).rows[0].n,0);
 const claim=await db.query<{lease_id:string;attempts:number}>(`select lease_id,attempts from public.sera_claim_message_push_jobs(8,'${event}')`);
 assert.equal(claim.rows[0].attempts,1);
 assert.equal((await db.query(`select * from public.sera_claim_message_push_jobs(8,'${event}')`)).rows.length,0);
 const finish=async(lease:string,outcome:string)=>(await db.query<{ok:boolean}>(`select public.sera_finish_message_push('${event}','message','${device}','${lease}','${outcome}') as ok`)).rows[0].ok;
 assert.equal(await finish(thread,"accepted"),false);
 assert.equal(await finish(claim.rows[0].lease_id,"retry"),true);
 assert.equal((await db.query(`select * from public.sera_claim_message_push_jobs(8,'${event}')`)).rows.length,0);
 await db.exec("update public.sera_message_push_jobs set next_attempt_at=now()");
 const retry=await db.query<{lease_id:string;attempts:number}>(`select lease_id,attempts from public.sera_claim_message_push_jobs(8,'${event}')`);
 assert.equal(retry.rows[0].attempts,2);assert.equal(await finish(retry.rows[0].lease_id,"accepted"),true);
 const auth=await db.query<{allowed:boolean}>(`select has_table_privilege('authenticated','public.sera_message_push_jobs','SELECT') as allowed`);
 assert.equal(auth.rows[0].allowed,false);
 assert.equal((await db.query(`select * from public.sera_claim_message_push_jobs(8,'${event}')`)).rows.length,0);
 }finally{await db.close();}
});
