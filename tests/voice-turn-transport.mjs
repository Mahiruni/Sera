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
  const context=new AudioContext({sampleRate:48000});await context.resume();
  const tones=[],receivers=new Map(),sources=new Map();
  function listen(pc){pc.ontrack=e=>{
    // A real playing media element pulls decoded audio from the receiver, like the app.
    const element=document.createElement("audio");element.autoplay=true;element.playsInline=true;
    // Use unity playback gain: the receiver energy metric can include output gain.
    element.volume=1;element.srcObject=new MediaStream([e.track]);document.body.append(element);
    const playing=element.play();playing.catch(()=>{});receivers.set(pc,{element,track:e.track,playing});
  };}
  listen(a);listen(b);
  function attach(pc,hz){
    const osc=context.createOscillator();osc.frequency.value=hz;
    const gain=context.createGain();gain.gain.value=.18;
    const dest=context.createMediaStreamDestination();dest.channelCount=1;
    osc.connect(gain).connect(dest);osc.start();tones.push(osc);
    sources.set(pc,{osc,gain,dest});dest.stream.getAudioTracks().forEach(t=>pc.addTrack(t,dest.stream));
  }
  function gather(pc){return new Promise((resolve,reject)=>{const ready=()=>pc.iceGatheringState==="complete"&&relayOnly(pc.localDescription?.sdp??"");const done=()=>{clearInterval(poll);clearTimeout(timer);};const poll=setInterval(()=>{if(ready()){done();resolve();}},100);const timer=setTimeout(()=>{done();reject(Error("ICE gathering timeout: "+pc.iceGatheringState));},15000);if(ready()){done();resolve();}});}
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
      // Use the inbound RTP receiver's cumulative decoded audio energy, independent
      // of the headless speaker/analyser output path. Verify actual playback starts too.
      for(const peer of [a,b]){
        const receiver=receivers.get(peer);
        if(!receiver)throw Error("Missing remote audio track.");
        await receiver.playing;
        if(receiver.element.paused||receiver.track.readyState!=="live")throw Error("Remote audio playback did not start.");
      }
      async function snapshot(peer){
        const stats=await peer.getStats();
        const audio=[...stats.values()].find(x=>x.type==="inbound-rtp"&&(x.kind==="audio"||x.mediaType==="audio"));
        if(!audio||!Number.isFinite(audio.totalAudioEnergy)||!Number.isFinite(audio.totalSamplesDuration))throw Error("Decoded audio energy metrics unavailable.");
        return {energy:audio.totalAudioEnergy,duration:audio.totalSamplesDuration,packets:audio.packetsReceived||0};
      }
      const sleep=ms=>new Promise(resolve=>setTimeout(resolve,ms));
      async function measure(label,aGain,bGain){
        // Distinct sender patterns must be reflected at the opposite receiver.
        sources.get(a).gain.gain.value=aGain;sources.get(b).gain.gain.value=bGain;
        await sleep(1800); // exclude jitter-buffer and packet-loss-concealment tail
        const [a0,b0]=await Promise.all([snapshot(a),snapshot(b)]);
        await sleep(2500);
        const [a1,b1]=await Promise.all([snapshot(a),snapshot(b)]);
        const rms=(before,after)=>{
          const dt=after.duration-before.duration,energy=after.energy-before.energy;
          if(dt<1||energy<0||!Number.isFinite(energy))throw Error("Insufficient decoded audio samples: "+JSON.stringify({label,before,after}));
          return Math.sqrt(energy/dt);
        };
        return {label,aRms:rms(a0,a1),bRms:rms(b0,b1),aPackets:a1.packets-a0.packets,bPackets:b1.packets-b0.packets};
      }
      const both=await measure("both speaking",.18,.18);
      // Actual ICE restart on the same peers and media tracks through the impaired relay.
      const beforeRestart=a.localDescription.sdp.match(/^a=ice-ufrag:(.+)$/m)?.[1].trim();
      const trackA=a.getSenders().find(s=>s.track?.kind==="audio").track;
      const trackB=b.getSenders().find(s=>s.track?.kind==="audio").track;
      await a.setLocalDescription(await a.createOffer({iceRestart:true}));await gather(a);
      const afterRestart=a.localDescription.sdp.match(/^a=ice-ufrag:(.+)$/m)?.[1].trim();
      if(!afterRestart||beforeRestart===afterRestart)throw Error("ICE restart did not rotate credentials.");
      if(!relayOnly(a.localDescription.sdp))throw Error("ICE restart did not preserve relay-only policy.");
      await b.setRemoteDescription(a.localDescription);await b.setLocalDescription(await b.createAnswer());await gather(b);
      if(!relayOnly(b.localDescription.sdp))throw Error("Restart answer is not relay-only.");
      await a.setRemoteDescription(b.localDescription);
      const recoveryDeadline=Date.now()+20000;
      while(Date.now()<recoveryDeadline&&(a.connectionState!=="connected"||b.connectionState!=="connected"))await sleep(250);
      if(a.connectionState!=="connected"||b.connectionState!=="connected"||a.getSenders().find(s=>s.track?.kind==="audio").track!==trackA||b.getSenders().find(s=>s.track?.kind==="audio").track!==trackB)throw Error("ICE restart did not retain the existing media session.");
      const afterRecovery=await measure("both speaking after ICE restart",.18,.18);
      const onlyA=await measure("A speaks",.18,0);
      const onlyB=await measure("B speaks",0,.18);
      const aReceived=received(await a.getStats())-initialA,bReceived=received(await b.getStats())-initialB;
      // Require non-silent decoded audio in both directions and direction-specific
      // energy contrast. Packet counts alone can never satisfy this test.
      const audible=x=>Number.isFinite(x)&&x>.008;
      const contrast=(voice,silence)=>audible(voice)&&voice>Math.max(.004,silence)*3;
      const results={aReceived,bReceived,both,afterRecovery,onlyA,onlyB,payloadCapBps:12000,iceRestart:true};
      console.log("Decoded voice measurements",JSON.stringify(results));
      if(a.connectionState!=="connected"||b.connectionState!=="connected"||aReceived<30||bReceived<30||
         !audible(both.aRms)||!audible(both.bRms)||!audible(afterRecovery.aRms)||!audible(afterRecovery.bRms)||!contrast(onlyB.aRms,onlyA.aRms)||!contrast(onlyA.bRms,onlyB.bRms))
         throw Error("Weak-link decoded audio verification failed: "+JSON.stringify(results));
      return {...results,relayOnly:true};
    }
    await new Promise(resolve=>setTimeout(resolve,500));
   }
   throw Error("Two-way audio RTP did not connect over TURN.");
  }finally{a.close();b.close();for(const {element} of receivers.values()){element.pause();element.srcObject=null;element.remove();}tones.forEach(x=>x.stop());await context.close();}
 },{username,credential,weak:process.env.SERA_TEST_WEAK_NETWORK==="1"});
 console.log("PASS: actual bidirectional synthetic audio RTP over local authenticated TURN",result);
}finally{await browser.close();server.close();}
