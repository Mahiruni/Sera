"use client";
import {createContext,useContext,useCallback,useEffect,useLayoutEffect,useMemo,useRef,useState,type ReactNode} from "react";
import {Phone,PhoneOff,Mic,MicOff,X,ChevronDown,History,Maximize2,Video,VideoOff,Lock} from "lucide-react";
import {createPortal} from "react-dom";
import {useSera} from "@/components/store";
import {voiceText} from "@/lib/network/voice-i18n";
import {SpeechAdaptation,speechProfiles,readSpeechLink,speechJitterTargetMs,speechQualityWarning} from "@/lib/network/voice-network-policy";
import {VideoAdaptation,videoProfiles,videoConstraints,type VideoQuality,type VideoLevel} from "@/lib/network/video-policy";
import {secureRelayAttempts} from "@/lib/network/relay-recovery";
import {MemberAvatar} from "@/components/activity/avatar";
import {getCallConfig,getCallStatus,getPendingIncomingCall,startCall,changeCall,getCallHistory,confirmCallMedia,restartCallMedia,getVideoAccess,markVideoReady,startVideoCall,setVideoQuality,type VideoAccess,type CallSession,type CallConfig,type CallHistoryEntry} from "@/lib/network/voice-calls";

function VideoView({stream,muted=true,label}:{stream:MediaStream|null;muted?:boolean;label:string}){
 const ref=useRef<HTMLVideoElement>(null);
 useEffect(()=>{const el=ref.current;if(!el)return;el.srcObject=stream;void el.play().catch(()=>{});return()=>{el.srcObject=null;};},[stream]);
 return <video ref={ref} autoPlay playsInline muted={muted} aria-label={label} style={{width:"100%",height:"100%",objectFit:"cover",display:"block"}}/>;
}
const qualityLabels:Record<VideoQuality,string>={standard:"Standard · 480p",hd:"HD · 720p",super_hd:"Super HD · 1080p"};
type VoiceSummary={title:string;duration:number|null;direction:"Incoming"|"Outgoing";endedAt:number;detail?:string};
const formatDuration=(seconds:number)=>{const s=Math.max(0,Math.floor(seconds));const mm=String(Math.floor(s/60)%60).padStart(2,"0"),ss=String(s%60).padStart(2,"0");return s>=3600?`${Math.floor(s/3600)}:${mm}:${ss}`:`${mm}:${ss}`;};
const outcome=(s:CallHistoryEntry,userId:string)=>s.state==="missed"?(s.caller===userId?"No answer":"Missed call"):s.state==="declined"?"Call declined":s.state==="failed"?"Connection lost":s.state==="cancelled"?"Call cancelled":"Call ended";
const live=(s:CallSession)=>s.state==="ringing"||s.state==="accepted";
const hasRelay=(pc:RTCPeerConnection)=>/^a=candidate:.*\btyp relay\b/m.test(pc.localDescription?.sdp??"");
function CallTopLayer({children,label,onDismiss}:{children:ReactNode;label:string;onDismiss:()=>void}){
  const ref=useRef<HTMLDialogElement>(null);
  useLayoutEffect(()=>{const dialog=ref.current;if(!dialog)return;dialog.showModal();return()=>{if(dialog.open)dialog.close();};},[]);
  return <dialog ref={ref} aria-label={label} onCancel={e=>{e.preventDefault();onDismiss();}} style={{position:"fixed",inset:0,margin:0,width:"100%",height:"100%",maxWidth:"none",maxHeight:"none",padding:0,border:0,background:"transparent",overflow:"hidden"}}>{children}</dialog>;
}
class RelayUnavailable extends Error {}
class MediaPermissionRequired extends Error {}
function abortError(){return new DOMException("Call cancelled.","AbortError");}
function gather(pc:RTCPeerConnection,budgetMs:number,signal:AbortSignal,complete=false):Promise<void>{
  return new Promise((resolve,reject)=>{
    const done=(error?:Error)=>{clearTimeout(timer);pc.removeEventListener("icegatheringstatechange",check);pc.removeEventListener("icecandidate",check);signal.removeEventListener("abort",abort);error?reject(error):resolve();};
    const check=()=>{if(signal.aborted)done(abortError());else if(hasRelay(pc)&&(!complete||pc.iceGatheringState==="complete"))done();else if(pc.iceGatheringState==="complete"&&!complete)done(new RelayUnavailable("No secure relay connection."));};
    const abort=()=>done(abortError());
    const timer=setTimeout(()=>done(hasRelay(pc)?undefined:new RelayUnavailable("Secure relay timed out.")),budgetMs);
    pc.addEventListener("icegatheringstatechange",check);pc.addEventListener("icecandidate",check);signal.addEventListener("abort",abort,{once:true});check();
  });
}
type VoiceEntry={conversation:string;userId:string;name:string;photo?:string|null;onHistory:(events:CallHistoryEntry[])=>void};
type VoiceTarget=VoiceEntry&{node:HTMLElement|null;initialIncomingId?:string;initialIncomingExpiresAt?:string};
type VoiceHostState={selected:VoiceTarget|null;target:VoiceTarget|null;pinned:boolean};
const VoiceHost=createContext<{register:(target:VoiceTarget)=>void;unregister:(node:HTMLElement)=>void}|null>(null);
/** Root-layout host survives client navigation; only the chat-header controls are portaled. */
export function SeraVoiceProvider({userId,children}:{userId:string;children:ReactNode}){
 const [state,setState]=useState<VoiceHostState>({selected:null,target:null,pinned:false});
 const register=useCallback((target:VoiceTarget)=>setState(s=>({...s,selected:target,target:s.pinned?s.target:target})),[]);
 const unregister=useCallback((node:HTMLElement)=>setState(s=>s.selected?.node!==node?s:{...s,selected:null,target:s.pinned?s.target:null}),[]);
 const report=useCallback((id:string,pinned:boolean)=>setState(s=>s.target?.conversation!==id||s.pinned===pinned?s:{...s,pinned,target:pinned?s.target:s.selected}),[]);
 const stateRef=useRef(state);useLayoutEffect(()=>{stateRef.current=state;},[state]);
 const history=useCallback((events:CallHistoryEntry[])=>{const s=stateRef.current;if(s.selected?.conversation===s.target?.conversation)s.selected?.onHistory(events);},[]);
 const api=useMemo(()=>({register,unregister}),[register,unregister]);
 const target=state.target;
 useEffect(()=>{if(!userId)return;const ready=()=>{if(!document.hidden&&typeof navigator.mediaDevices?.getUserMedia==="function"&&typeof RTCPeerConnection!=="undefined")void markVideoReady().catch(()=>{});};ready();const timer=setInterval(ready,25000);document.addEventListener("visibilitychange",ready);return()=>{clearInterval(timer);document.removeEventListener("visibilitychange",ready);};},[userId]);
 useEffect(()=>{
  if(!userId||state.pinned)return;let stopped=false,inFlight=false;
  const discover=async()=>{
   if(stopped||inFlight||document.hidden)return;inFlight=true;
   try{const call=await getPendingIncomingCall();if(stopped||!call)return;
    setState(s=>s.pinned||s.target?.conversation===call.conversation?s:{...s,pinned:true,target:{conversation:call.conversation,userId,name:call.name,photo:call.photo,node:null,onHistory:history,initialIncomingId:call.id,initialIncomingExpiresAt:call.expiresAt}});
   }catch{/* The current thread and existing call remain unaffected by a discovery failure. */}finally{inFlight=false;}
  };
  void discover();const timer=setInterval(()=>void discover(),6000);const visible=()=>{if(!document.hidden)void discover();};document.addEventListener("visibilitychange",visible);
  return()=>{stopped=true;clearInterval(timer);document.removeEventListener("visibilitychange",visible);};
 },[userId,state.pinned,history]);
 const selected=state.selected?.conversation===target?.conversation?state.selected:null;
 return <VoiceHost.Provider value={api}>{children}{userId&&target?.userId===userId&&<VoiceEngine key={userId+":"+target.conversation} {...target} onHistory={history} controlRoot={selected?.node??null} global selected={Boolean(selected)} report={report}/>}</VoiceHost.Provider>;
}
export function SeraVoiceCall(props:VoiceEntry){
 const host=useContext(VoiceHost),node=useRef<HTMLSpanElement>(null),historyRef=useRef(props.onHistory);
 useLayoutEffect(()=>{historyRef.current=props.onHistory;},[props.onHistory]);
 useLayoutEffect(()=>{if(!host||!node.current)return;const element=node.current;
  host.register({...props,node:element,onHistory:events=>historyRef.current(events)});
  return()=>host.unregister(element);
 },[host,props.conversation,props.userId,props.name,props.photo]);
 return host?<span ref={node} style={{display:"contents"}}/>:<VoiceEngine {...props}/>;
}
function VoiceEngine({conversation,userId,name,photo,onHistory,controlRoot,global=false,selected=true,report,initialIncomingId,initialIncomingExpiresAt}:VoiceEntry&{controlRoot?:HTMLElement|null;global?:boolean;selected?:boolean;report?:(id:string,pinned:boolean)=>void;initialIncomingId?:string;initialIncomingExpiresAt?:string}){
  const {locale}=useSera();
  const tr=(value:string)=>voiceText(locale,value);
  const [config,setConfig]=useState<CallConfig>({enabled:false}),[session,setSession]=useState<CallSession|null>(null),[phase,setPhase]=useState(""),[muted,setMuted]=useState(false),[seconds,setSeconds]=useState(0),[error,setError]=useState(""),[history,setHistory]=useState<CallHistoryEntry[]|null>(null),[audioBlocked,setAudioBlocked]=useState(false),[minimized,setMinimized]=useState(false);
  const [summary,setSummary]=useState<VoiceSummary|null>(null);
  const [pendingDiscovery,setPendingDiscovery]=useState(Boolean(initialIncomingId));
  const incomingProbe=useRef(initialIncomingId??null);
  const [quality,setQuality]=useState(false);
  const [videoAccess,setVideoAccess]=useState<VideoAccess>({enabled:false,premium:false,peer_ready:false});
  const [videoPicker,setVideoPicker]=useState(false),[preparingVideo,setPreparingVideo]=useState(false),[cameraOn,setCameraOn]=useState(false),[localVideo,setLocalVideo]=useState<MediaStream|null>(null),[remoteVideo,setRemoteVideo]=useState<MediaStream|null>(null),[videoLevel,setVideoLevel]=useState<VideoLevel>("data_saver"),[dataSaver,setDataSaver]=useState(false);
  const videoControl=useRef({cap:"standard" as VideoQuality,saver:false,camera:false,changing:false});
  useEffect(()=>{let stopped=false;const refresh=()=>void getVideoAccess(conversation).then(a=>{if(!stopped)setVideoAccess(a);});refresh();const timer=setInterval(()=>{if(!document.hidden)refresh();},20000);return()=>{stopped=true;clearInterval(timer);};},[conversation]);
  const preparationAbort=useRef<AbortController|null>(null);
  const restart=useRef({applied:0,pendingGeneration:0,pendingSdp:"",attemptAt:0,unhealthySince:0,networkChanged:false});
  const restartAbort=useRef<AbortController|null>(null);
  const cancelRequested=useRef(false),operation=useRef<"dial"|"accept"|null>(null),polling=useRef(false);
  const pc=useRef<RTCPeerConnection|null>(null),stream=useRef<MediaStream|null>(null),audio=useRef<HTMLAudioElement>(null),current=useRef<CallSession|null>(null),busy=useRef(false),mounted=useRef(true),started=useRef(0),lastRemote=useRef(""),failTimer=useRef<ReturnType<typeof setTimeout>|null>(null),pollErrors=useRef(0),missingSince=useRef(0);
  const update=useCallback((s:CallSession|null)=>{current.current=s;if(mounted.current)setSession(s);},[]);
  const cleanup=useCallback(()=>{restartAbort.current?.abort();restartAbort.current=null;restart.current={applied:0,pendingGeneration:0,pendingSdp:"",attemptAt:0,unhealthySince:0,networkChanged:false};preparationAbort.current?.abort();preparationAbort.current=null;if(failTimer.current)clearTimeout(failTimer.current);failTimer.current=null;const connection=pc.current;pc.current=null;if(connection){connection.onconnectionstatechange=null;connection.ontrack=null;connection.close();}stream.current?.getTracks().forEach(t=>t.stop());stream.current=null;if(audio.current)audio.current.srcObject=null;lastRemote.current="";started.current=0;pollErrors.current=0;missingSince.current=0;if(mounted.current){setMuted(false);setSeconds(0);setAudioBlocked(false);setMinimized(false);setQuality(false);setPreparingVideo(false);setCameraOn(false);setLocalVideo(null);setRemoteVideo(null);setVideoLevel("data_saver");setDataSaver(false);videoControl.current={cap:"standard",saver:false,camera:false,changing:false};}},[]);
  const captureSummary=useCallback((title:string,s:CallSession|null,detail?:string)=>{if(mounted.current)setSummary({title,duration:started.current?Math.floor((performance.now()-started.current)/1000):null,direction:s?.callee===userId?"Incoming":"Outgoing",endedAt:Date.now(),detail});},[userId]);
  const terminate=useCallback(async(action:"end"|"decline"|"fail",message="")=>{const s=current.current;captureSummary(action==="decline"?"Call declined":action==="fail"?"Connection lost":started.current?"Call ended":"Call cancelled",s,message);cleanup();update(null);setPhase("");if(s&&live(s)){try{await changeCall(conversation,s.id,action);}catch{if(mounted.current)setError(message||"Call closed locally. Remote status will expire.");}}},[captureSummary,cleanup,conversation,update]);
  useEffect(()=>{mounted.current=true;void getCallConfig(conversation).then(c=>{if(mounted.current){setConfig(c);if(!c.enabled){incomingProbe.current=null;setPendingDiscovery(false);}}});return()=>{mounted.current=false;const s=current.current;cleanup();if(s&&live(s))void changeCall(conversation,s.id,"end").catch(()=>{});};},[conversation,cleanup]);
  async function recoverMedia(call:CallSession){
    const connection=pc.current;if(!connection||call.state!=="accepted")return;
    const peer:RTCPeerConnection=connection;
    const caller=call.caller===userId,generation=call.restart_generation??0,r=restart.current;
    if(caller&&!lastRemote.current)return;
    const signal=(action:"ready"|"request"|"offer"|"answer",rev=generation,sdp?:string)=>restartCallMedia(conversation,call.id,action,rev,sdp);
    if(!(caller?call.caller_restart_ready:call.callee_restart_ready)){await signal("ready");return;}
    if(!call.caller_restart_ready||!call.callee_restart_ready)return;
    const stillCurrent=()=>pc.current===peer&&current.current?.id===call.id&&mounted.current;
    if(r.pendingSdp){const result=await signal(caller?"offer":"answer",r.pendingGeneration,r.pendingSdp);if(result&&stillCurrent()){r.pendingSdp="";if(!caller)r.applied=r.pendingGeneration;}return;}
    if(caller&&generation>r.applied&&call.restart_answer){await peer.setRemoteDescription({type:"answer",sdp:call.restart_answer});if(stillCurrent())r.applied=generation;return;}
    async function prepare(offer?:string){
      const controller=new AbortController();restartAbort.current=controller;
      try{
        if(offer)await peer.setRemoteDescription({type:"offer",sdp:offer});
        const previous=peer.localDescription?.sdp?.match(/^a=ice-ufrag:(.+)$/m)?.[1].trim();
        const desc=offer?await peer.createAnswer():await peer.createOffer({iceRestart:true});
        if(!offer){const next=desc.sdp?.match(/^a=ice-ufrag:(.+)$/m)?.[1].trim();if(!next||next===previous)throw Error("ICE credentials did not rotate.");}
        if(!stillCurrent())throw abortError();await peer.setLocalDescription(desc);await gather(peer,12000,controller.signal,true);
        if(!stillCurrent())throw abortError();return peer.localDescription!.sdp;
      }finally{if(restartAbort.current===controller)restartAbort.current=null;}
    }
    if(!caller&&generation>r.applied&&call.restart_offer&&!call.restart_answer){r.pendingGeneration=generation;r.pendingSdp=await prepare(call.restart_offer);const result=await signal("answer",generation,r.pendingSdp);if(result&&stillCurrent()){r.applied=generation;r.pendingSdp="";}return;}
    const now=performance.now();if(peer.connectionState==="connected")r.unhealthySince=0;else if(!r.unhealthySince)r.unhealthySince=now;
    if(caller&&call.restart_requested&&generation<3&&generation===r.applied&&peer.signalingState==="stable"){r.attemptAt=now;r.pendingGeneration=generation+1;r.pendingSdp=await prepare();if(await signal("offer",r.pendingGeneration,r.pendingSdp))r.pendingSdp="";return;}
    if((r.networkChanged&&started.current>0||r.unhealthySince&&now-r.unhealthySince>=3000)&&now-r.attemptAt>=8000&&generation<3&&generation===r.applied&&!call.restart_requested){r.attemptAt=now;const result=await signal("request");if(result?.restart_requested)r.networkChanged=false;}
  }
  useEffect(()=>{if(session?.state!=="accepted")return;const changed=()=>{restart.current.networkChanged=true;};const network=(navigator as Navigator&{connection?:EventTarget}).connection;window.addEventListener("online",changed);network?.addEventListener("change",changed);return()=>{window.removeEventListener("online",changed);network?.removeEventListener("change",changed);};},[session?.id,session?.state]);
  useEffect(()=>{if(!config.enabled)return;let stopped=false;let lastIdlePoll=0;const poll=async()=>{if(stopped||busy.current||polling.current)return;if(!current.current){if(document.hidden||Date.now()-lastIdlePoll<4000)return;lastIdlePoll=Date.now();}polling.current=true;try{
    const prior=current.current;const s=await getCallStatus(conversation,prior?.id??incomingProbe.current);if(stopped||busy.current||prior!==current.current)return;pollErrors.current=0;
    if(incomingProbe.current){incomingProbe.current=null;setPendingDiscovery(false);}
    if(!s){if(prior){if(!missingSince.current)missingSince.current=Date.now();if(Date.now()-missingSince.current>=15000){cleanup();update(null);setPhase("");setError("Call is no longer available.");}}return;}missingSince.current=0;
    if(!live(s)){if(prior){captureSummary(outcome(s,userId),s);cleanup();update(null);setPhase("");}return;}
    if(s.state==="accepted"&&!pc.current){if(prior){update(null);setPhase("");setError("Call answered on another device.");}return;}
    if(!prior&&s.caller===userId)return;
    if(!prior){setMinimized(false);setHistory(null);setError("");setSummary(null);}
    update(s);
    if(s.media_mode==="video")videoControl.current.cap=(s.caller===userId?s.caller_video_quality:s.callee_video_quality)??"standard";
    if(s.state==="accepted"&&s.caller===userId&&s.answer&&pc.current&&lastRemote.current!==s.answer){await pc.current.setRemoteDescription({type:"answer",sdp:s.answer});lastRemote.current=s.answer;if(mounted.current&&!started.current)setPhase("Connecting…");}
    if(s.state==="accepted")await recoverMedia(s);
  }catch{if(!busy.current&&current.current){if(!pollErrors.current)pollErrors.current=Date.now();if(Date.now()-pollErrors.current>=45000){await terminate("fail","Call signaling connection lost. Try again.");}}}finally{polling.current=false;}};
  void poll();const timer=setInterval(()=>void poll(),1500);const visible=()=>{if(!document.hidden){lastIdlePoll=0;void poll();}};document.addEventListener("visibilitychange",visible);return()=>{stopped=true;clearInterval(timer);document.removeEventListener("visibilitychange",visible);};},[config.enabled,conversation,userId,cleanup,update,terminate,captureSummary]);
  useEffect(()=>{if(!config.enabled)return;let stopped=false;const refresh=()=>void getCallHistory(conversation).then(events=>{if(!stopped)onHistory(events);}).catch(()=>{});refresh();const timer=setInterval(()=>{if(!document.hidden)refresh();},20000);return()=>{stopped=true;clearInterval(timer);};},[config.enabled,conversation,onHistory]);
  useEffect(()=>{const timer=setInterval(()=>{if(started.current)setSeconds(Math.floor((performance.now()-started.current)/1000));},1000);return()=>clearInterval(timer);},[]);
  useEffect(()=>{if(session?.state!=="accepted"||!pc.current||pc.current.connectionState==="connected"||failTimer.current)return;const peer=pc.current,id=session.id;failTimer.current=setTimeout(()=>{if(pc.current===peer&&current.current?.id===id&&peer.connectionState!=="connected")void terminate("fail",started.current?"Call connection lost. Try again.":"Could not establish the call.");},45000);},[session?.id,session?.state,phase,terminate]);
  useEffect(()=>{if(session?.state!=="accepted"||!started.current)return;const id=session.id;let stopped=false,confirmed=false,inFlight=false;const report=async()=>{if(stopped||confirmed||inFlight||pc.current?.connectionState!=="connected")return;inFlight=true;try{confirmed=await confirmCallMedia(conversation,id);}catch{}finally{inFlight=false;}};void report();const timer=setInterval(()=>void report(),4000);return()=>{stopped=true;clearInterval(timer);};},[conversation,session?.id,session?.state,phase]);
  useEffect(()=>{
    if(session?.state!=="accepted"||!pc.current)return;
    const peer=pc.current,adaptation=new SpeechAdaptation();let stopped=false,inFlight=false,applied:number|undefined,lastReport:number|undefined,jitterApplied:number|undefined;
    const adjust=async()=>{
      if(stopped||inFlight||pc.current!==peer||peer.connectionState!=="connected")return;
      inFlight=true;
      try{
        const stats=await peer.getStats();if(stopped||pc.current!==peer)return;
        const {link,remoteTimestamp}=readSpeechLink(stats.values());
        const inbound=Array.from(stats.values()).find(s=>s.type==="inbound-rtp"&&(s.kind==="audio"||s.mediaType==="audio"));
        const jitter=typeof inbound?.jitter==="number"?inbound.jitter:undefined;
        const target=speechJitterTargetMs(jitter);
        if(target!==undefined&&target!==jitterApplied){
          for(const receiver of peer.getReceivers())if(receiver.track.kind==="audio"&&"jitterBufferTarget" in receiver){try{(receiver as RTCRtpReceiver&{jitterBufferTarget:number|null}).jitterBufferTarget=target;jitterApplied=target;}catch{/* keep browser default */}}
        }
        const fresh=remoteTimestamp!==undefined&&remoteTimestamp!==lastReport;
        if(fresh)lastReport=remoteTimestamp;
        setQuality(speechQualityWarning(fresh?link.loss:undefined,jitter,link.rttSeconds));
        const bitrate=speechProfiles[adaptation.update(fresh?link:{...link,loss:undefined})];
        if(applied!==bitrate){
          const sender=peer.getSenders().find(s=>s.track?.kind==="audio");
          if(sender){const params=sender.getParameters();if(params.encodings.length){params.encodings.forEach(e=>{e.maxBitrate=bitrate;});await sender.setParameters(params);applied=bitrate;}}
        }
      }catch{/* Preserve the engine's default adaptation if stats or sender controls are unsupported. */}
      finally{inFlight=false;}
    };
    void adjust();const timer=setInterval(()=>void adjust(),4000);
    return()=>{stopped=true;clearInterval(timer);};
  },[session?.id,session?.state]);
  async function prepareMedia(video=false,camera=true){
    if(!mounted.current||cancelRequested.current)throw abortError();
    if(!navigator.mediaDevices?.getUserMedia||!window.RTCPeerConnection)throw new Error("This browser does not support voice calling.");
    // Request audio directly from the Call/Answer gesture, before any network wait.
    let local:MediaStream;try{local=await navigator.mediaDevices.getUserMedia({audio:{echoCancellation:true,noiseSuppression:true,autoGainControl:true,channelCount:{ideal:1}},video:video&&camera?videoConstraints("data_saver"):false});}catch(e){if(e instanceof DOMException&&e.name==="NotAllowedError")throw new MediaPermissionRequired(video&&camera?"Allow camera and microphone access in your browser.":"Allow microphone access in your browser to call.");throw e;}
    if(!mounted.current||cancelRequested.current){local.getTracks().forEach(t=>t.stop());throw abortError();}
    stream.current=local;
    const fresh=await getCallConfig(conversation,true);if(!fresh.enabled||!fresh.iceServers?.length)throw new Error("Secure calling access unavailable. Try again later.");
    if(!mounted.current||cancelRequested.current)throw abortError();
    const tracks=local.getVideoTracks();videoControl.current.camera=tracks.length>0;setCameraOn(tracks.length>0);setLocalVideo(tracks.length?new MediaStream(tracks):null);return {local,servers:fresh.iceServers};
  }
  function playCallAudio(){
    const el=audio.current,source=el?.srcObject;
    if(!el||!source)return;
    el.muted=false;
    void el.play().then(()=>{if(mounted.current&&audio.current===el&&el.srcObject===source)setAudioBlocked(false);}).catch(()=>{if(mounted.current&&audio.current===el&&el.srcObject===source)setAudioBlocked(true);});
  }
  function createPeer(local:MediaStream,servers:RTCIceServer[],video=false){
    const connection=new RTCPeerConnection({iceServers:servers,iceTransportPolicy:"relay"});pc.current=connection;
    local.getTracks().forEach(track=>connection.addTrack(track,local));
    if(video&&!local.getVideoTracks().length)connection.addTransceiver("video",{direction:"sendrecv"});
    connection.ontrack=e=>{if(pc.current!==connection)return;if(e.track.kind==="video"){setRemoteVideo(new MediaStream([e.track]));return;}if(audio.current){audio.current.srcObject=new MediaStream([e.track]);playCallAudio();e.track.addEventListener("unmute",()=>{if(pc.current===connection)playCallAudio();},{once:true});}};
    connection.onconnectionstatechange=()=>{if(pc.current!==connection)return;const state=connection.connectionState;
      if(state==="connected"){if(cancelRequested.current)return;if(failTimer.current)clearTimeout(failTimer.current);failTimer.current=null;if(!started.current)started.current=performance.now();setPhase("Connected");playCallAudio();}
      else if(state==="disconnected"||state==="connecting"||state==="failed"){if(cancelRequested.current)return;if(current.current?.state==="accepted"){setPhase(started.current?"Reconnecting…":"Connecting…");if(!failTimer.current)failTimer.current=setTimeout(()=>{if(pc.current===connection&&connection.connectionState!=="connected")void terminate("fail",started.current?"Call connection lost. Try again.":"Could not establish the call.");},45000);}}};
    return connection;
  }
  async function prepareWithFallback(offer?:string,expiresAt?:string,video=false,camera=true){
    const controller=new AbortController();preparationAbort.current=controller;
    const {local,servers}=await prepareMedia(video,camera);
    for(const attempt of secureRelayAttempts(servers)){
      if(controller.signal.aborted||!mounted.current||cancelRequested.current)throw abortError();
      const remaining=expiresAt?Date.parse(expiresAt)-Date.now()-4000:attempt.budgetMs;
      const budget=Math.min(attempt.budgetMs,remaining);
      if(!Number.isFinite(budget)||budget<2500)break;
      setPhase(attempt.label);
      const peer=createPeer(local,attempt.servers,video);
      const deadline=performance.now()+budget;
      let timer:ReturnType<typeof setTimeout>|undefined;
      let onAbort:()=>void=()=>{};
      try{
        // Bound SDP preparation as well as candidate gathering.
        const prepare=async()=>{if(offer)await peer.setRemoteDescription({type:"offer",sdp:offer});await peer.setLocalDescription(offer?await peer.createAnswer():await peer.createOffer());await gather(peer,Math.max(0,deadline-performance.now()),controller.signal);};
        await Promise.race([prepare(),new Promise<never>((_,reject)=>{timer=setTimeout(()=>reject(new RelayUnavailable("Secure relay timed out.")),budget);onAbort=()=>reject(abortError());controller.signal.addEventListener("abort",onAbort,{once:true});if(controller.signal.aborted)onAbort();})]);
        if(controller.signal.aborted||pc.current!==peer||!mounted.current||cancelRequested.current)throw abortError();
        return peer;
      }catch(e){
        if(pc.current===peer)pc.current=null;
        peer.ontrack=null;peer.onconnectionstatechange=null;peer.close();
        if(!(e instanceof RelayUnavailable))throw e;
      }finally{if(timer)clearTimeout(timer);controller.signal.removeEventListener("abort",onAbort);}
    }
    throw new RelayUnavailable("No secure call relay found. Switch Wi-Fi or mobile data and try again.");
  }
  async function dial(videoRequest?:VideoQuality){
    if(busy.current||current.current)return;if(videoRequest&&(!videoAccess.enabled||!videoAccess.peer_ready||videoRequest!=="standard"&&!videoAccess.premium)){setError("Video unavailable. Check recipient availability or subscription.");return;}setVideoPicker(false);setPreparingVideo(Boolean(videoRequest));videoControl.current.cap=videoRequest??"standard";report?.(conversation,true);busy.current=true;operation.current="dial";cancelRequested.current=false;setSummary(null);setMinimized(false);setError("");setPhase("Preparing call…");
    try{const peer=await prepareWithFallback(undefined,undefined,Boolean(videoRequest));if(!mounted.current||pc.current!==peer||cancelRequested.current)throw new Error("Call cancelled.");const s=videoRequest?await startVideoCall(conversation,crypto.randomUUID(),peer.localDescription!.sdp,videoRequest):await startCall(conversation,crypto.randomUUID(),peer.localDescription!.sdp);if(!mounted.current||cancelRequested.current){void changeCall(conversation,s.id,"end").catch(()=>{});cleanup();if(mounted.current)setPhase("");return;}update(s);setPhase("Calling…");}
    catch(e){captureSummary(cancelRequested.current?"Call cancelled":e instanceof RelayUnavailable?"Unable to connect":"Call unavailable",null,cancelRequested.current?undefined:e instanceof Error?e.message:"Could not start call.");cleanup();setPhase("");}finally{busy.current=false;operation.current=null;}
  }
  async function accept(camera=true){
    const s=current.current;if(!s?.offer||busy.current)return;setPreparingVideo(s.media_mode==="video");busy.current=true;operation.current="accept";cancelRequested.current=false;setMinimized(false);setError("");setPhase("Preparing call…");
    try{const peer=await prepareWithFallback(s.offer,s.expires_at??new Date(Date.parse(s.created_at)+45000).toISOString(),s.media_mode==="video",camera);if(!mounted.current||pc.current!==peer||cancelRequested.current)throw new Error("Call cancelled.");const next=await changeCall(conversation,s.id,"accept",peer.localDescription!.sdp);if(!live(next))throw new Error("Call ended.");if(!mounted.current){void changeCall(conversation,next.id,"end").catch(()=>{});return;}update(next);if(cancelRequested.current){await terminate("end");return;}if(!started.current)setPhase("Connecting…");}
    catch(e){if(e instanceof MediaPermissionRequired&&!cancelRequested.current&&current.current?.id===s.id){cleanup();setPhase("");setError(e.message);}else await terminate(cancelRequested.current?"decline":"fail",cancelRequested.current?"":e instanceof Error?e.message:"Could not accept call.");}finally{busy.current=false;operation.current=null;}
  }

  async function toggleCamera(){
    const peer=pc.current,call=current.current,control=videoControl.current;
    if(!peer||call?.media_mode!=="video"||call.state!=="accepted"||control.changing)return;
    const sender=peer.getTransceivers().find(t=>t.receiver.track.kind==="video")?.sender;
    if(!sender)return;control.changing=true;
    try{
      if(control.camera){await sender.replaceTrack(null);stream.current?.getVideoTracks().forEach(t=>{t.stop();stream.current?.removeTrack(t);});control.camera=false;setCameraOn(false);setLocalVideo(null);}
      else{
        const capture=await navigator.mediaDevices.getUserMedia({video:videoConstraints("data_saver"),audio:false});
        const track=capture.getVideoTracks()[0];
        if(!track||pc.current!==peer||current.current?.id!==call.id){capture.getTracks().forEach(t=>t.stop());return;}
        try{await sender.replaceTrack(track);}catch(e){track.stop();throw e;}
        if(pc.current!==peer){track.stop();return;}
        stream.current?.addTrack(track);control.camera=true;setCameraOn(true);setLocalVideo(new MediaStream([track]));
      }
    }catch{setError("Camera unavailable. Your audio call continues.");}finally{control.changing=false;}
  }
  async function chooseQuality(next:VideoQuality){
    const call=current.current;if(!call||call.media_mode!=="video")return;
    if(next!=="standard"&&!videoAccess.premium){setError("Subscription required");return;}
    try{const updated=await setVideoQuality(conversation,call.id,next);if(!updated||current.current?.id!==call.id){setError("Subscription required");return;}videoControl.current.cap=next;update(updated);}catch{setError("Video quality unavailable");}
  }
  useEffect(()=>{
    if(session?.state!=="accepted"||session.media_mode!=="video"||!pc.current)return;
    const peer=pc.current,adaptation=new VideoAdaptation();let stopped=false,inFlight=false,applied="",lastReport:number|undefined;
    const adjust=async()=>{
      if(stopped||inFlight||pc.current!==peer||peer.connectionState!=="connected"||videoControl.current.changing)return;inFlight=true;
      try{
        const stats=await peer.getStats();if(stopped||pc.current!==peer)return;
        const {link,remoteTimestamp}=readSpeechLink(stats.values()),fresh=remoteTimestamp!==undefined&&remoteTimestamp!==lastReport;if(fresh)lastReport=remoteTimestamp;
        const control=videoControl.current,level=adaptation.update(control.cap,fresh?link:{...link,loss:undefined},control.saver);
        setVideoLevel(level);
        const sender=peer.getTransceivers().find(t=>t.receiver.track.kind==="video")?.sender,track=sender?.track;
        if(!sender||!track||!control.camera||control.changing)return;
        const key=track.id+":"+level;if(key===applied)return;
        const p=videoProfiles[level],params=sender.getParameters();
        if(level!=="paused")await track.applyConstraints(videoConstraints(level)).catch(()=>{});
        if(stopped||pc.current!==peer||sender.track!==track||control.changing)return;
        if(params.encodings.length){params.encodings.forEach(e=>{e.active=level!=="paused";e.maxBitrate=Math.max(1000,p.bitrate);if(level!=="paused")e.maxFramerate=p.fps;});await sender.setParameters(params);}
        track.enabled=level!=="paused";applied=key;
      }catch{/* Keep the existing call alive when a browser cannot apply a camera setting. */}finally{inFlight=false;}
    };
    void adjust();const timer=setInterval(()=>void adjust(),4000);return()=>{stopped=true;clearInterval(timer);};
  },[session?.id,session?.state,session?.media_mode]);
  useEffect(()=>{
    if(!pendingDiscovery||!initialIncomingExpiresAt)return;
    const timer=setTimeout(()=>{incomingProbe.current=null;setPendingDiscovery(false);},Math.max(0,Date.parse(initialIncomingExpiresAt)-Date.now()));
    return()=>clearTimeout(timer);
  },[pendingDiscovery,initialIncomingExpiresAt]);
  const held=Boolean(session||phase||summary||pendingDiscovery);
  useLayoutEffect(()=>{report?.(conversation,held);},[report,conversation,held]);
  useEffect(()=>{if(!selected&&session?.state!=="ringing"){const frame=requestAnimationFrame(()=>setMinimized(true));return()=>cancelAnimationFrame(frame);}},[selected,session?.state]);
  if(!config.enabled)return null;
  const incoming=session?.state==="ringing"&&session.callee===userId&&!phase;
  const isVideo=preparingVideo||session?.media_mode==="video";
  const duration=formatDuration(seconds);
  const active=Boolean(session||phase);
  const end=()=>{if(operation.current){cancelRequested.current=true;preparationAbort.current?.abort();stream.current?.getTracks().forEach(t=>t.stop());const peer=pc.current;pc.current=null;if(peer){peer.ontrack=null;peer.onconnectionstatechange=null;peer.close();}setPhase("Cancelling…");return;}if(cancelRequested.current&&phase==="Cancelling…")return;void terminate("end");};
  const controls=<><button type="button" className="sera-chat-icon" aria-label={tr(active?"Return to call":"Voice call")} title={tr(active?"Return to call":"Voice call")} onClick={()=>active?setMinimized(false):void dial()}><Phone size={21}/></button>
  {videoAccess.enabled&&<button type="button" className="sera-chat-icon" aria-label={tr("Video call")} title={tr("Video call")} onClick={()=>active?setMinimized(false):setVideoPicker(true)}><Video size={21}/></button>}
  <button type="button" className="sera-chat-icon" aria-label={tr("Recent calls")} title={tr("Recent calls")} disabled={active} onClick={()=>{setHistory([]);void getCallHistory(conversation).then(events=>setHistory(previous=>previous===null?null:events)).catch(()=>{setHistory(null);setError("Call history unavailable.");});}}><History size={19}/></button></>;
  return <>{global?(controlRoot?createPortal(controls,controlRoot):null):controls}
  <audio ref={audio} autoPlay playsInline onPlaying={()=>setAudioBlocked(false)}/>
  {videoPicker&&!active&&typeof document!=="undefined"&&createPortal(<CallTopLayer label={tr("Video quality")} onDismiss={()=>setVideoPicker(false)}><div className="sera-call-overlay sera-aura-overlay"><div className="sera-call-panel sera-aura-panel"><header className="sera-aura-header"><span/><strong>{tr("Video quality")}</strong><button type="button" aria-label={tr("Close")} onClick={()=>setVideoPicker(false)}><X/></button></header><MemberAvatar name={name} photo={photo} size={64}/><h2>{name}</h2><p>{tr("Video adapts to your network. Voice always comes first.")}</p>{!videoAccess.peer_ready&&<p role="status">{tr("Recipient must open Sera Web to receive video calls.")}</p>}{(["standard","hd","super_hd"] as const).map(q=><button key={q} type="button" className="sera-aura-audio" disabled={!videoAccess.peer_ready||q!=="standard"&&!videoAccess.premium} onClick={()=>void dial(q)}>{q!=="standard"&&!videoAccess.premium?<Lock size={18}/>:<Video size={18}/>} {qualityLabels[q]} · {tr(q==="standard"?"Free":"Premium")}</button>)}{!videoAccess.premium&&<p>{tr("HD and Super HD require an active subscription.")}</p>}</div></div></CallTopLayer>,document.body)}
  {active&&typeof document!=="undefined"&&createPortal(minimized&&!incoming?<div className="sera-aura-mini" style={{zIndex:2147483000}}><button type="button" onClick={()=>{setMinimized(false);if(audioBlocked)playCallAudio();}}><MemberAvatar name={name} photo={photo} size={32}/><span><strong>{name}</strong><small>{phase==="Connected"?duration:tr(phase||"Calling…")}</small></span><Maximize2 size={17}/></button>{session?.state==="accepted"&&<button type="button" aria-label={tr(muted?"Unmute":"Mute")} aria-pressed={muted} onClick={()=>{const next=!muted;stream.current?.getAudioTracks().forEach(t=>{t.enabled=!next;});setMuted(next);}}>{muted?<MicOff size={18}/>:<Mic size={18}/>}</button>}<button type="button" className="sera-aura-hang" aria-label={tr("End call")} onClick={end}><PhoneOff size={19}/></button></div>:<CallTopLayer label={tr(incoming?(isVideo?"Incoming video call":"Incoming voice call"):(isVideo?"Video call":"Voice call"))} onDismiss={()=>{if(!incoming)setMinimized(true);}}><div className="sera-call-overlay sera-aura-overlay"><div className="sera-call-panel sera-aura-panel">
    <header className="sera-aura-header">{!incoming?<button type="button" aria-label={tr("Minimize call")} onClick={()=>setMinimized(true)}><ChevronDown/></button>:<span/>}<span>{isVideo?"Sera Video":"Sera Voice"}</span><span/></header>
    <div className="sera-aura-identity">{isVideo&&remoteVideo&&!incoming?<div style={{position:"relative",width:"min(100%, 360px)",aspectRatio:"3/4",maxHeight:"42vh",overflow:"hidden",borderRadius:24,background:"#102326"}}><VideoView stream={remoteVideo} label={tr("Remote video")}/>{localVideo&&<div style={{position:"absolute",right:12,bottom:12,width:"28%",aspectRatio:"3/4",overflow:"hidden",borderRadius:12,border:"2px solid white"}}><VideoView stream={localVideo} label={tr("Your camera")}/></div>}</div>:isVideo&&localVideo?<div style={{width:100,height:130,borderRadius:18,overflow:"hidden",background:"#102326"}}><VideoView stream={localVideo} label={tr("Your camera")}/></div>:<div className="sera-aura-avatar"><MemberAvatar name={name} photo={photo} size={104}/></div>}<h2>{name}</h2><p role="status">{tr(incoming?(isVideo?"Incoming video call":"Incoming voice call"):phase||"Calling…")}</p>{(phase==="Connected"||seconds>0)&&<strong className="sera-aura-duration">{duration}</strong>}{quality&&phase==="Connected"&&<p role="status">{tr("Unstable connection")}</p>}{isVideo&&session?.state==="accepted"&&<p role="status">{!cameraOn?tr("Camera off"):videoLevel==="paused"?tr("Video paused · audio continues"):videoLevel==="data_saver"?tr("Data Saver · 360p"):qualityLabels[videoLevel]}</p>}</div>
    {(phase==="Finding secure connection…"||phase==="Trying alternative secure connection…"||phase==="Preparing call…")&&<progress aria-label={tr("Preparing secure connection")} style={{width:"65%",accentColor:"var(--sera-teal, #087E83)"}}/>}
    {incoming&&<p role="status" style={{fontSize:13,opacity:.8}}>{tr("Allow microphone access when you answer, if prompted.")}</p>}
    {active&&error&&<p role="alert" style={{fontSize:13}}>{tr(error)}</p>}
    {audioBlocked&&<button className="sera-aura-audio" type="button" onClick={playCallAudio}>{tr("Enable call audio")}</button>}
    <div className="sera-aura-controls">{incoming?<><button type="button" className="sera-aura-control" onClick={()=>void terminate("decline")}><span className="sera-aura-hang"><PhoneOff/></span>{tr("Decline")}</button><button type="button" className="sera-aura-control" onClick={()=>void accept()}><span className="sera-aura-answer"><Phone/></span>{tr(isVideo?"Answer with video":"Answer")}</button>{isVideo&&<button type="button" className="sera-aura-control" onClick={()=>void accept(false)}><span><Mic/></span>{tr("Audio only")}</button>}</>:<>{session?.state==="accepted"&&<button type="button" aria-pressed={muted} className="sera-aura-control" onClick={()=>{const next=!muted;stream.current?.getAudioTracks().forEach(t=>{t.enabled=!next;});setMuted(next);}}><span className={muted?"sera-aura-answer":""}>{muted?<MicOff/>:<Mic/>}</span>{tr(muted?"Unmute":"Mute")}</button>}{isVideo&&session?.state==="accepted"&&<button type="button" className="sera-aura-control" aria-pressed={!cameraOn} onClick={()=>void toggleCamera()}><span>{cameraOn?<Video/>:<VideoOff/>}</span>{tr(cameraOn?"Camera off":"Camera on")}</button>}<button type="button" className="sera-aura-control" onClick={end}><span className="sera-aura-hang"><PhoneOff/></span>{tr(phase==="Cancelling…"?"Cancelling…":"End call")}</button></>}</div>
    {isVideo&&session?.state==="accepted"&&<div style={{display:"flex",gap:10,alignItems:"center",flexWrap:"wrap",justifyContent:"center"}}><button type="button" className="sera-aura-audio" aria-pressed={dataSaver} onClick={()=>{videoControl.current.saver=!dataSaver;setDataSaver(!dataSaver);}}>{tr("Data Saver")}: {tr(dataSaver?"On":"Off")}</button><select aria-label={tr("Video quality")} value={(session.caller===userId?session.caller_video_quality:session.callee_video_quality)??"standard"} onChange={e=>void chooseQuality(e.target.value as VideoQuality)}>{(["standard","hd","super_hd"] as const).map(q=><option key={q} value={q} disabled={q!=="standard"&&!videoAccess.premium}>{qualityLabels[q]}{q!=="standard"&&!videoAccess.premium?" · Premium":""}</option>)}</select></div>}
  </div></div></CallTopLayer>,document.body)}
  {history&&!active&&typeof document!=="undefined"&&createPortal(<CallTopLayer label={tr("Call history")} onDismiss={()=>setHistory(null)}><div className="sera-call-overlay sera-aura-overlay"><div className="sera-call-panel sera-aura-history"><header className="sera-aura-header"><span/><strong>{tr("Recent calls")}</strong><button type="button" onClick={()=>setHistory(null)} aria-label={tr("Close call history")}><X/></button></header><MemberAvatar name={name} photo={photo} size={56}/><h2>{name}</h2>{history.length?history.map(s=><p key={s.id}>{tr(s.caller===userId?"Outgoing":"Incoming")} · {tr(outcome(s,userId))} · {new Date(s.created_at).toLocaleString(locale)}{s.connected_seconds!=null?` · ${formatDuration(s.connected_seconds)}`:""}</p>):<p>{tr("No calls yet")}</p>}<button type="button" className="sera-aura-audio" onClick={()=>{setHistory(null);void dial();}}><Phone size={18}/> {tr("Call")}</button></div></div></CallTopLayer>,document.body)}
  {summary&&!active&&typeof document!=="undefined"&&createPortal(<CallTopLayer label={tr("Call summary")} onDismiss={()=>setSummary(null)}><div className="sera-call-overlay sera-aura-overlay"><div className="sera-call-panel sera-aura-panel"><header className="sera-aura-header"><span/><strong>Sera Voice</strong><button type="button" aria-label={tr("Close call summary")} onClick={()=>setSummary(null)}><X/></button></header><div className="sera-aura-identity"><div className="sera-aura-avatar"><MemberAvatar name={name} photo={photo} size={104}/></div><h2>{tr(summary.title)}</h2><p>{name}</p>{summary.duration!==null&&<strong className="sera-aura-duration">{formatDuration(summary.duration)}</strong>}<p>{tr(summary.direction)} · {new Date(summary.endedAt).toLocaleString(locale)}</p>{summary.detail&&<p role="status">{tr(summary.detail)}</p>}</div><div className="sera-aura-controls"><button type="button" className="sera-aura-control" onClick={()=>{setSummary(null);void dial();}}><span className="sera-aura-answer"><Phone/></span>{tr("Call again")}</button><button type="button" className="sera-aura-control" onClick={()=>setSummary(null)}><span><X/></span>{tr("Back to messages")}</button></div></div></div></CallTopLayer>,document.body)}
  {error&&!active&&typeof document!=="undefined"&&createPortal(<div className="sera-call-toast" style={{zIndex:2147483001}} role="status">{tr(error)}<button type="button" onClick={()=>setError("")} aria-label={tr("Dismiss call status")}><X size={16}/></button></div>,document.body)}</>;
}
