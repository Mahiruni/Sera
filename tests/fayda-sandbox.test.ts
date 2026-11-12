import test from "node:test";
import assert from "node:assert/strict";
import {generateKeyPairSync,sign} from "node:crypto";
import {createSessions,verifyJwt,authorize,challenge,config,assertion} from "../scripts/fayda-sandbox/oidc.mjs";
const {privateKey,publicKey}=generateKeyPairSync("rsa",{modulusLength:2048});
const jwk={...publicKey.export({format:"jwk"}),kid:"test"};
const c={client:"test-client",issuer:"https://esignet.ida.fayda.et",authorize:"https://esignet.ida.fayda.et/authorize",redirect:"http://localhost:3000/callback",token:"https://esignet.ida.fayda.et/v1/esignet/oauth/v2/token",key:{...privateKey.export({format:"jwk"}),kid:"test"}};
const claims={iss:c.issuer,aud:c.client,sub:"synthetic",nonce:"n",iat:100,exp:200};
function jwt(p:object,h={alg:"RS256",kid:"test"}){const x=Buffer.from(JSON.stringify(h)).toString("base64url")+"."+Buffer.from(JSON.stringify(p)).toString("base64url");return x+"."+sign("RSA-SHA256",Buffer.from(x),privateKey).toString("base64url");}
test("sandbox validates signed identity, nonce, audience, expiry and subject",()=>{
 assert.equal(verifyJwt(jwt(claims),[jwk],c,{nonce:"n",now:150}).sub,"synthetic");
 for(const p of [{...claims,aud:"other"},{...claims,iss:"other"},{...claims,exp:149},{...claims,nonce:"wrong"},{...claims,sub:"wrong"}])assert.throws(()=>verifyJwt(jwt(p),[jwk],c,{nonce:"n",subject:"synthetic",now:150}));
 assert.throws(()=>verifyJwt(jwt(claims,{alg:"none",kid:"test"}),[jwk],c,{now:150}));
 assert.throws(()=>verifyJwt(jwt(claims),[{...jwk,n:"bad"}],c,{now:150}));
});
test("sandbox sessions are single use, state bound and expire",()=>{let now=0;const store=createSessions({clock:()=>now,ttl:10});const s=store.start();assert.throws(()=>store.consume(s.id,"wrong"));assert.equal(store.consume(s.id,s.state).nonce,s.nonce);assert.throws(()=>store.consume(s.id,s.state));const t=store.start();now=11;assert.throws(()=>store.consume(t.id,t.state));});
test("authorization is localhost bound and uses PKCE; client assertion is signed",()=>{const s=createSessions().start();const u=new URL(authorize(c,s));assert.equal(u.searchParams.get("redirect_uri"),c.redirect);assert.equal(u.searchParams.get("code_challenge"),challenge(s.verifier));assert.equal(u.searchParams.get("state"),s.state);const a=assertion(c,100);assert.equal(verifyJwt(a,[jwk],{...c,issuer:c.client,client:c.token},{now:101}).sub,c.client);assert.throws(()=>config({FAYDA_CLIENT_ID:"x",FAYDA_ISSUER:"https://evil.example",FAYDA_JWKS_URI:"https://esignet.ida.fayda.et/keys",FAYDA_PRIVATE_KEY:"x"}));});
