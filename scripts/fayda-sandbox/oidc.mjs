import {createHash,createPrivateKey,createPublicKey,randomBytes,sign,verify} from "node:crypto";
const b64=v=>Buffer.from(v).toString("base64url");
export const random=()=>randomBytes(32).toString("base64url");
export const challenge=v=>createHash("sha256").update(v).digest("base64url");
const host="esignet.ida.fayda.et";
export function config(env=process.env){
 const client=env.FAYDA_CLIENT_ID,issuer=env.FAYDA_ISSUER,jwks=env.FAYDA_JWKS_URI,key=env.FAYDA_PRIVATE_KEY;
 if(!client||!issuer||!jwks||!key)throw Error("Set FAYDA_CLIENT_ID, FAYDA_ISSUER, FAYDA_JWKS_URI and FAYDA_PRIVATE_KEY from the official sandbox configuration.");
 for(const value of [issuer,jwks]){const u=new URL(value);if(u.protocol!=="https:"||u.hostname!==host||u.username||u.password||u.port||u.hash)throw Error("Only the official sandbox host is permitted.");}
 return {client,issuer,jwks,key:JSON.parse(Buffer.from(key,"base64").toString("utf8")),redirect:"http://localhost:3000/callback",authorize:"https://"+host+"/authorize",token:"https://"+host+"/v1/esignet/oauth/v2/token",userinfo:"https://"+host+"/v1/esignet/oidc/userinfo"};
}
export function assertion(c,now=Math.floor(Date.now()/1000)){
 const h=b64(JSON.stringify({alg:"RS256",typ:"JWT",kid:c.key.kid})),p=b64(JSON.stringify({iss:c.client,sub:c.client,aud:c.token,iat:now,exp:now+120,jti:random()}));
 const input=h+"."+p;return input+"."+sign("RSA-SHA256",Buffer.from(input),createPrivateKey({key:c.key,format:"jwk"})).toString("base64url");
}
export function authorize(c,s){
 const u=new URL(c.authorize);u.search=new URLSearchParams({client_id:c.client,redirect_uri:c.redirect,response_type:"code",scope:"openid profile",state:s.state,nonce:s.nonce,code_challenge:challenge(s.verifier),code_challenge_method:"S256",acr_values:"mosip:idp:acr:generated-code"}).toString();return u.toString();
}
/** @param {string} token @param {any[]} keys @param {any} c @param {{nonce?:string,subject?:string,now?:number}} options */
export function verifyJwt(token,keys,c,{nonce,subject,now=Math.floor(Date.now()/1000)}={}){
 if(typeof token!=="string"||token.length>2000000)throw Error("Invalid token");
 const parts=token.split(".");if(parts.length!==3)throw Error("Invalid token");
 const h=JSON.parse(Buffer.from(parts[0],"base64url").toString()),p=JSON.parse(Buffer.from(parts[1],"base64url").toString());
 if(h.alg!=="RS256"||typeof h.kid!=="string"||h.crit||h.jku||h.x5u)throw Error("Unexpected token header");
 const candidates=keys.filter(k=>k.kid===h.kid&&k.kty==="RSA"&&(!k.use||k.use==="sig")&&(!k.alg||k.alg==="RS256"));
 if(candidates.length!==1||!verify("RSA-SHA256",Buffer.from(parts[0]+"."+parts[1]),createPublicKey({key:candidates[0],format:"jwk"}),Buffer.from(parts[2],"base64url")))throw Error("Invalid signature");
 const aud=Array.isArray(p.aud)?p.aud:[p.aud];
 if(p.iss!==c.issuer||!aud.includes(c.client)||(aud.length>1&&p.azp!==c.client)||(p.azp!==undefined&&p.azp!==c.client)||!Number.isFinite(p.exp)||p.exp<=now||!Number.isFinite(p.iat)||p.iat>now+60||(p.nbf!==undefined&&(!Number.isFinite(p.nbf)||p.nbf>now+60))||typeof p.sub!=="string"||!p.sub)throw Error("Invalid identity claims");
 if(nonce!==undefined&&p.nonce!==nonce)throw Error("Nonce mismatch");
 if(subject!==undefined&&p.sub!==subject)throw Error("Subject mismatch");
 return p;
}
export function createSessions({ttl=300000,limit=100,clock=Date.now}={}){
 const sessions=new Map();
 return {start(){for(const [id,s] of sessions)if(s.expires<=clock())sessions.delete(id);if(sessions.size>=limit)throw Error("Too many sessions");const id=random(),s={state:random(),nonce:random(),verifier:random(),expires:clock()+ttl};sessions.set(id,s);return {id,...s};},consume(id,state){const s=sessions.get(id);if(!s||s.expires<=clock()||s.state!==state)throw Error("Invalid or expired session");sessions.delete(id);return s;}};
}
async function request(url,options={}){
 const r=await fetch(url,{...options,redirect:"error",signal:AbortSignal.timeout(15000)});if(!r.ok)throw Error("Provider request failed");return r;
}
export async function exchange(c,s,code){
 const params=new URLSearchParams({grant_type:"authorization_code",code,redirect_uri:c.redirect,client_id:c.client,code_verifier:s.verifier,client_assertion_type:"urn:ietf:params:oauth:client-assertion-type:jwt-bearer",client_assertion:assertion(c)});
 const tokens=await (await request(c.token,{method:"POST",headers:{"content-type":"application/x-www-form-urlencoded"},body:params})).json();
 const jwks=await (await request(c.jwks)).json();if(!Array.isArray(jwks.keys))throw Error("Invalid key set");
 const id=verifyJwt(tokens.id_token,jwks.keys,c,{nonce:s.nonce});
 if(typeof tokens.access_token!=="string"||!tokens.access_token)throw Error("Missing access token");
 const result=await request(c.userinfo,{headers:{Authorization:"Bearer "+tokens.access_token,Accept:"application/jwt"}});
 const info=verifyJwt((await result.text()).trim(),jwks.keys,c,{subject:id.sub});
 // Do not return or store identity attributes, tokens, FAN or subject identifiers.
 return {sandbox:true,authenticated:true,nameAvailable:typeof info.name==="string"&&info.name.trim().length>0,badgeEligible:false};
}
