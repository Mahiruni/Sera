import test from "node:test";
import assert from "node:assert/strict";
import {secureRelayAttempts} from "../lib/network/relay-recovery";
test("relay fallback reuses authenticated endpoints and prioritizes TLS 443",()=>{
 const udp="turn:turn.cloudflare.com:3478?transport=udp", tls="turns:turn.cloudflare.com:5349?transport=tcp", tls443="turns:turn.cloudflare.com:443?transport=tcp";
 const attempts=secureRelayAttempts([{urls:[udp,tls,tls443,"stun:stun.cloudflare.com:3478"],username:"test",credential:"secret"}]);
 assert.equal(attempts.length,2);
 assert.equal(attempts[0].servers.length,3);
 assert.deepEqual(attempts[1].servers.map(s=>s.urls),[tls443,tls]);
 assert.equal(attempts[1].servers[0].credential,"secret");
 assert.equal(attempts.reduce((n,a)=>n+a.budgetMs,0),32000);
});
test("relay planning never falls back to STUN or missing credentials",()=>{
 assert.deepEqual(secureRelayAttempts([{urls:"stun:stun.cloudflare.com:3478"}]),[]);
 assert.deepEqual(secureRelayAttempts([{urls:"turns:turn.cloudflare.com:443?transport=tcp"}]),[]);
 const udp="turn:turn.cloudflare.com:3478?transport=udp";
 const attempts=secureRelayAttempts([{urls:udp,username:"test",credential:"secret"}]);
 assert.equal(attempts[1].servers[0].urls,udp);
});
