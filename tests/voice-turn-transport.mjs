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
 const result=await page.evaluate(async({username,credential,weak})=>{
  const config={iceServers:[{urls:"turn:127.0.0.1:3478?transport=udp",username,credential}],iceTransportPolicy:"relay"};
  const a=new RTCPeerConnection(config),b=new RTCPeerConnection(config);
  const context=new AudioContext();await context.resume();
  const tones=[],analysers=new Map();
  function listen(pc){pc.ontrack=e=>{const source=context.createMediaStreamSource(e.streams[0]??new MediaStream([e.track]));const analyser=context.createAnalyser();analyser.fftSize=2048;analyser.smoothingTimeConstant=.2;const quiet=context.createGain();quiet.gain.value=0;source.connect(analyser).connect(quiet).connect(context.destination);analysers.set(pc,analyser);};}
  if(weak){listen(a);listen(b);}
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
   const deadline=Date.now()+(weak?45000:20000);
   while(Date.now()<deadline){
    const statsA=await a.getStats(),statsB=await b.getStats();
    const received=stats=>[...stats.values()].filter(x=>x.type==="inbound-rtp"&&x.kind==="audio").reduce((n,x)=>n+(x.packetsReceived||0),0);
    const selectedRelay=stats=>{const transport=[...stats.values()].find(x=>x.type==="transport"&&x.selectedCandidatePairId);const pair=transport&&stats.get(transport.selectedCandidatePairId);return pair&&stats.get(pair.localCandidateId)?.candidateType==="relay"&&stats.get(pair.remoteCandidateId)?.candidateType==="relay";};
    if(a.connectionState==="connected"&&b.connectionState==="connected"&&selectedRelay(statsA)&&selectedRelay(statsB)&&received(statsA)>20&&received(statsB)>20){
      if(!weak)return {aReceived:received(statsA),bReceived:received(statsB),relayOnly:true};
      // Validate a real encoder at the lowest production payload cap, not just policy math.
      for(const peer of [a,b]){
       const sender=peer.getSenders().find(s=>s.track?.kind==="audio"),params=sender.getParameters();
       if(!params.encodings.length)throw Error("No audio sender encoding.");
       params.encodings.forEach(e=>{e.maxBitrate=12000;});await sender.setParameters(params);
      }
      const initialA=received(statsA),initialB=received(statsB);
      const toneHeard=(peer,hz)=>{const analyser=analysers.get(peer);if(!analyser)return false;const data=new Float32Array(analyser.frequencyBinCount);analyser.getFloatFrequencyData(data);const bin=Math.round(hz*analyser.fftSize/context.sampleRate);let peak=-Infinity;for(let i=Math.max(0,bin-2);i<=Math.min(data.length-1,bin+2);i++)peak=Math.max(peak,data[i]);return peak>-65;};
      let heardA=0,heardB=0;
      for(let n=0;n<10;n++){await new Promise(resolve=>setTimeout(resolve,700));if(toneHeard(a,660))heardA++;if(toneHeard(b,440))heardB++;}
      const aReceived=received(await a.getStats())-initialA,bReceived=received(await b.getStats())-initialB;
      if(a.connectionState!=="connected"||b.connectionState!=="connected"||aReceived<30||bReceived<30||heardA<5||heardB<5)throw Error("Weak-link decoded audio verification failed: "+JSON.stringify({aReceived,bReceived,heardA,heardB}));
      return {aReceived,bReceived,heardA,heardB,relayOnly:true,payloadCapBps:12000};
    }
    await new Promise(resolve=>setTimeout(resolve,500));
   }
   throw Error("Two-way audio RTP did not connect over TURN.");
  }finally{a.close();b.close();tones.forEach(x=>x.stop());await context.close();}
 },{username,credential,weak:process.env.SERA_TEST_WEAK_NETWORK==="1"});
 console.log("PASS: actual bidirectional synthetic audio RTP over local authenticated TURN",result);
}finally{await browser.close();server.close();}
