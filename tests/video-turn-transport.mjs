import {chromium} from "playwright";
import {createHmac} from "node:crypto";
import {createServer} from "node:http";
const secret=process.env.SERA_TEST_TURN_SECRET;
if(!secret)throw Error("Missing test relay secret");
const username=Math.floor(Date.now()/1000+300)+":sera-video-test";
const credential=createHmac("sha1",secret).update(username).digest("base64");
const server=createServer((_req,res)=>{res.writeHead(200,{"Content-Type":"text/html"});res.end("<!doctype html><title>Sera Video QA</title>");});
await new Promise(resolve=>server.listen(0,"127.0.0.1",resolve));
const browser=await chromium.launch({args:["--autoplay-policy=no-user-gesture-required","--no-sandbox"]});
try{
 const page=await browser.newPage();
 await page.goto("http://127.0.0.1:"+server.address().port);
 const result=await page.evaluate(async({username,credential})=>{
  const cfg={iceServers:[{urls:"turn:127.0.0.1:3478?transport=udp",username,credential}],iceTransportPolicy:"relay"};
  const peers=[new RTCPeerConnection(cfg),new RTCPeerConnection(cfg)];
  const audio=new AudioContext({sampleRate:48000});await audio.resume();
  const sources=[],elements=[],timers=[],oscillators=[];
  const sleep=ms=>new Promise(resolve=>setTimeout(resolve,ms));
  const relayOnly=s=>{const c=s.split(/\r?\n/).filter(x=>x.startsWith("a=candidate:"));return c.length>0&&c.every(x=>/ typ relay(?: |$)/.test(x));};
  async function gather(p){
   const deadline=Date.now()+16000;
   while(Date.now()<deadline){if(p.iceGatheringState==="complete"&&relayOnly(p.localDescription?.sdp??""))return;await sleep(100);}
   throw Error("Video relay gathering timed out");
  }
  for(let i=0;i<2;i++){
   const p=peers[i];
   p.ontrack=e=>{const el=document.createElement(e.track.kind==="video"?"video":"audio");el.autoplay=true;el.playsInline=true;el.muted=e.track.kind==="video";el.srcObject=new MediaStream([e.track]);document.body.append(el);elements.push(el);void el.play().catch(()=>{});};
   const canvas=document.createElement("canvas");canvas.width=640;canvas.height=360;
   const ctx=canvas.getContext("2d");let frame=0;
   const paint=()=>{ctx.fillStyle=i?"#2c8d93":"#d85542";ctx.fillRect(0,0,640,360);ctx.fillStyle="#fff";ctx.fillRect((frame++*11)%580,100,60,100);};
   paint();timers.push(setInterval(paint,66));
   const v=canvas.captureStream(15);const video=v.getVideoTracks()[0];
   const osc=audio.createOscillator();osc.frequency.value=i?660:440;const gain=audio.createGain();gain.gain.value=.18;
   const dest=audio.createMediaStreamDestination();osc.connect(gain).connect(dest);osc.start();oscillators.push(osc);
   const stream=new MediaStream([video,...dest.stream.getAudioTracks()]);sources.push(stream);
   for(const track of stream.getTracks())p.addTrack(track,stream);
  }
  const [a,b]=peers;
  async function negotiate(restart=false){
   const old=a.localDescription?.sdp?.match(/^a=ice-ufrag:(.+)$/m)?.[1].trim();
   await a.setLocalDescription(await a.createOffer(restart?{iceRestart:true}:{}));await gather(a);
   if(restart&&a.localDescription.sdp.match(/^a=ice-ufrag:(.+)$/m)?.[1].trim()===old)throw Error("ICE credentials unchanged");
   if(!relayOnly(a.localDescription.sdp)||!/^m=audio /m.test(a.localDescription.sdp)||!/^m=video /m.test(a.localDescription.sdp))throw Error("Unexpected video offer");
   await b.setRemoteDescription(a.localDescription);await b.setLocalDescription(await b.createAnswer());await gather(b);
   if(!relayOnly(b.localDescription.sdp))throw Error("Non-relay video answer");
   await a.setRemoteDescription(b.localDescription);
  }
  async function snapshot(p){
   const s=await p.getStats(),all=[...s.values()],v=all.find(x=>x.type==="inbound-rtp"&&x.kind==="video"),au=all.find(x=>x.type==="inbound-rtp"&&x.kind==="audio");
   const t=all.find(x=>x.type==="transport"&&x.selectedCandidatePairId),pair=t&&s.get(t.selectedCandidatePairId);
   return {connected:p.connectionState==="connected",relay:!!pair&&s.get(pair.localCandidateId)?.candidateType==="relay"&&s.get(pair.remoteCandidateId)?.candidateType==="relay",frames:v?.framesDecoded??0,width:v?.frameWidth??0,height:v?.frameHeight??0,energy:au?.totalAudioEnergy??0,duration:au?.totalSamplesDuration??0};
  }
  async function waitFrames(before=[0,0]){
   const deadline=Date.now()+25000;
   while(Date.now()<deadline){const s=await Promise.all(peers.map(snapshot));if(s.every((x,i)=>x.connected&&x.relay&&x.frames>before[i]+10&&x.width>0&&x.height>0&&x.energy>0))return s;await sleep(250);}
   throw Error("No decoded two-way video and audio");
  }
  try{
   await negotiate();
   for(const p of peers){
    for(const sender of p.getSenders()){const params=sender.getParameters();if(!params.encodings.length)throw Error("No encoder");params.encodings.forEach(e=>{e.maxBitrate=sender.track.kind==="video"?220000:20000;});await sender.setParameters(params);}
   }
   const initial=await waitFrames();
   const videoSenders=peers.map(p=>p.getSenders().find(s=>s.track?.kind==="video"));
   const audioTracks=peers.map(p=>p.getSenders().find(s=>s.track?.kind==="audio").track);
   // Stop only video transmission, not audio. Require decoded audio energy to keep increasing.
   for(const sender of videoSenders)await sender.replaceTrack(null);
   await sleep(2000);const before=await Promise.all(peers.map(snapshot));await sleep(3000);
   const paused=await Promise.all(peers.map(snapshot));
   if(paused.some((x,i)=>!x.connected||!x.relay||x.duration-before[i].duration<1||x.energy<=before[i].energy||x.frames-before[i].frames>2))throw Error("Video pause did not preserve audio: "+JSON.stringify({before,paused}));
   for(let i=0;i<2;i++)await videoSenders[i].replaceTrack(sources[i].getVideoTracks()[0]);
   const resumed=await waitFrames(paused.map(x=>x.frames));
   await negotiate(true);const restarted=await waitFrames(resumed.map(x=>x.frames));
   if(peers.some((p,i)=>p.getSenders().find(s=>s.track?.kind==="audio").track!==audioTracks[i]))throw Error("Restart replaced audio tracks");
   return {initial,paused,resumed,restarted};
  }finally{peers.forEach(p=>p.close());sources.forEach(s=>s.getTracks().forEach(t=>t.stop()));timers.forEach(clearInterval);oscillators.forEach(o=>o.stop());elements.forEach(e=>{e.srcObject=null;e.remove();});await audio.close();}
 },{username,credential});
 console.log("PASS: decoded video, audio-only fallback, resume and ICE restart",JSON.stringify(result));
}finally{await browser.close();server.close();}
