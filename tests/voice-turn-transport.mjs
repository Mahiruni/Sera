import { chromium } from "playwright";
import { createHmac } from "node:crypto";
import { createServer } from "node:http";
const secret=process.env.SERA_TEST_TURN_SECRET;
if(!secret)throw Error("Missing test-only relay secret.");
const username=Math.floor(Date.now()/1000+300)+":sera-test";
const credential=createHmac("sha1",secret).update(username).digest("base64");
const server=createServer((_req,res)=>{res.writeHead(200,{"Content-Type":"text/html"});res.end("<!doctype html><title>SERA voice relay QA</title>");});
await new Promise(resolve=>server.listen(0,"127.0.0.1",resolve));
const origin="http://127.0.0.1:"+server.address().port;
const browser=await chromium.launch({args:["--autoplay-policy=no-user-gesture-required","--no-sandbox"]});
try{
 const page=await browser.newPage();await page.goto(origin);
 const result=await page.evaluate(async({username,credential})=>{
  const config={iceServers:[{urls:"turn:127.0.0.1:3478?transport=udp",username,credential}],iceTransportPolicy:"relay"};
  const a=new RTCPeerConnection(config),b=new RTCPeerConnection(config);
  const context=new AudioContext();await context.resume();
  const tones=[];
  function attach(pc,hz){const osc=context.createOscillator();osc.frequency.value=hz;const gain=context.createGain();gain.gain.value=.04;const dest=context.createMediaStreamDestination();osc.connect(gain).connect(dest);osc.start();tones.push(osc);dest.stream.getTracks().forEach(t=>pc.addTrack(t,dest.stream));}
  function gather(pc){return new Promise((resolve,reject)=>{if(pc.iceGatheringState==="complete")return resolve();const timer=setTimeout(()=>reject(Error("ICE gathering timeout")),15000);pc.addEventListener("icegatheringstatechange",()=>{if(pc.iceGatheringState==="complete"){clearTimeout(timer);resolve();}});});}
  function relayOnly(sdp){const candidates=sdp.split(/\r?\n/).filter(x=>x.startsWith("a=candidate:"));return candidates.length>0&&candidates.every(x=>/ typ relay(?: |$)/.test(x));}
  try{
   attach(a,440);attach(b,660);
   await a.setLocalDescription(await a.createOffer());await gather(a);
   if(!relayOnly(a.localDescription.sdp))throw Error("Offer is not relay-only.");
   await b.setRemoteDescription(a.localDescription);await b.setLocalDescription(await b.createAnswer());await gather(b);
   if(!relayOnly(b.localDescription.sdp))throw Error("Answer is not relay-only.");
   await a.setRemoteDescription(b.localDescription);
   const deadline=Date.now()+20000;
   while(Date.now()<deadline){
    const statsA=await a.getStats(),statsB=await b.getStats();
    const received=stats=>[...stats.values()].filter(x=>x.type==="inbound-rtp"&&x.kind==="audio").reduce((n,x)=>n+(x.packetsReceived||0),0);
    const selectedRelay=stats=>{const transport=[...stats.values()].find(x=>x.type==="transport"&&x.selectedCandidatePairId);const pair=transport&&stats.get(transport.selectedCandidatePairId);return pair&&stats.get(pair.localCandidateId)?.candidateType==="relay"&&stats.get(pair.remoteCandidateId)?.candidateType==="relay";};
    if(a.connectionState==="connected"&&b.connectionState==="connected"&&selectedRelay(statsA)&&selectedRelay(statsB)&&received(statsA)>20&&received(statsB)>20)return {aReceived:received(statsA),bReceived:received(statsB),relayOnly:true};
    await new Promise(resolve=>setTimeout(resolve,500));
   }
   throw Error("Two-way audio RTP did not connect over TURN.");
  }finally{a.close();b.close();tones.forEach(x=>x.stop());await context.close();}
 },{username,credential});
 console.log("PASS: actual bidirectional synthetic audio RTP over local authenticated TURN",result);
}finally{await browser.close();server.close();}
