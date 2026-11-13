"use client";
import {useCallback,useEffect,useLayoutEffect,useRef,useState,type ReactNode} from "react";
import {Phone,PhoneOff,Mic,MicOff,X,ChevronDown,History,Maximize2} from "lucide-react";
import {createPortal} from "react-dom";
import {MemberAvatar} from "@/components/activity/avatar";
import {getCallConfig,getCallStatus,startCall,changeCall,getCallHistory,type CallSession,type CallConfig,type CallHistoryEntry} from "@/lib/network/voice-calls";

const live=(s:CallSession)=>s.state==="ringing"||s.state==="accepted";
const hasRelay=(pc:RTCPeerConnection)=>/^a=candidate:.*\btyp relay\b/m.test(pc.localDescription?.sdp??"");
function CallTopLayer({children,label,onDismiss}:{children:ReactNode;label:string;onDismiss:()=>void}){
  const ref=useRef<HTMLDialogElement>(null);
  useLayoutEffect(()=>{const dialog=ref.current;if(!dialog)return;dialog.showModal();return()=>{if(dialog.open)dialog.close();};},[]);
  return <dialog ref={ref} aria-label={label} onCancel={e=>{e.preventDefault();onDismiss();}} style={{position:"fixed",inset:0,margin:0,width:"100%",height:"100%",maxWidth:"none",maxHeight:"none",padding:0,border:0,background:"transparent",overflow:"hidden"}}>{children}</dialog>;
}
async function gather(pc:RTCPeerConnection){
  if(pc.iceGatheringState==="complete"){if(!hasRelay(pc))throw new Error("No secure relay connection. Try another network.");return;}
  await new Promise<void>((resolve,reject)=>{
    const done=()=>{clearTimeout(timer);pc.removeEventListener("icegatheringstatechange",check);};
    const check=()=>{if(pc.iceGatheringState==="complete"){done();if(hasRelay(pc))resolve();else reject(new Error("No secure relay connection. Try another network."));}};
    const timer=setTimeout(()=>{done();if(hasRelay(pc))resolve();else reject(new Error("Secure relay timed out. Check your connection and try again."));},25000);
    pc.addEventListener("icegatheringstatechange",check);check();
  });
}
export function SeraVoiceCall({conversation,userId,name,photo,onHistory}:{conversation:string;userId:string;name:string;photo?:string|null;onHistory:(events:CallHistoryEntry[])=>void}){
  const [config,setConfig]=useState<CallConfig>({enabled:false}),[session,setSession]=useState<CallSession|null>(null),[phase,setPhase]=useState(""),[muted,setMuted]=useState(false),[seconds,setSeconds]=useState(0),[error,setError]=useState(""),[history,setHistory]=useState<CallHistoryEntry[]|null>(null),[audioBlocked,setAudioBlocked]=useState(false),[minimized,setMinimized]=useState(false);
  const cancelRequested=useRef(false),operation=useRef<"dial"|"accept"|null>(null),polling=useRef(false);
  const pc=useRef<RTCPeerConnection|null>(null),stream=useRef<MediaStream|null>(null),audio=useRef<HTMLAudioElement>(null),current=useRef<CallSession|null>(null),busy=useRef(false),mounted=useRef(true),started=useRef(0),lastRemote=useRef(""),failTimer=useRef<ReturnType<typeof setTimeout>|null>(null),pollErrors=useRef(0),missingSince=useRef(0);
  const update=useCallback((s:CallSession|null)=>{current.current=s;if(mounted.current)setSession(s);},[]);
  const cleanup=useCallback(()=>{if(failTimer.current)clearTimeout(failTimer.current);failTimer.current=null;const connection=pc.current;pc.current=null;if(connection){connection.onconnectionstatechange=null;connection.ontrack=null;connection.close();}stream.current?.getTracks().forEach(t=>t.stop());stream.current=null;if(audio.current)audio.current.srcObject=null;lastRemote.current="";started.current=0;pollErrors.current=0;missingSince.current=0;if(mounted.current){setMuted(false);setSeconds(0);setAudioBlocked(false);setMinimized(false);}},[]);
  const terminate=useCallback(async(action:"end"|"decline"|"fail",message="")=>{const s=current.current;cleanup();update(null);setPhase("");if(message)setError(message);if(s&&live(s)){try{await changeCall(conversation,s.id,action);}catch{if(mounted.current)setError(message||"Call closed locally. Remote status will expire.");}}},[cleanup,conversation,update]);
  useEffect(()=>{mounted.current=true;void getCallConfig(conversation).then(c=>{if(mounted.current)setConfig(c);});return()=>{mounted.current=false;const s=current.current;cleanup();if(s&&live(s))void changeCall(conversation,s.id,"end").catch(()=>{});};},[conversation,cleanup]);
  useEffect(()=>{if(!config.enabled)return;let stopped=false;const poll=async()=>{if(stopped||busy.current||polling.current)return;polling.current=true;try{
    const prior=current.current;const s=await getCallStatus(conversation,prior?.id??null);if(stopped||busy.current||prior!==current.current)return;pollErrors.current=0;
    if(!s){if(prior){if(!missingSince.current)missingSince.current=Date.now();if(Date.now()-missingSince.current>=15000){cleanup();update(null);setPhase("");setError("Call is no longer available.");}}return;}missingSince.current=0;
    if(!live(s)){if(prior){cleanup();update(null);setPhase("");setError(s.state==="missed"?"No answer":s.state==="declined"?"Call declined":s.state==="failed"?"Call connection failed":"Call ended");}return;}
    if(s.state==="accepted"&&!pc.current){if(prior){update(null);setPhase("");setError("Call answered on another device.");}return;}
    if(!prior&&s.caller===userId)return;
    if(!prior){setMinimized(false);setHistory(null);setError("");}
    update(s);
    if(s.state==="accepted"&&s.caller===userId&&s.answer&&pc.current&&lastRemote.current!==s.answer){await pc.current.setRemoteDescription({type:"answer",sdp:s.answer});lastRemote.current=s.answer;if(mounted.current&&!started.current)setPhase("Connecting…");}
  }catch{if(!busy.current&&current.current){if(!pollErrors.current)pollErrors.current=Date.now();if(Date.now()-pollErrors.current>=30000){await terminate("fail","Call signaling connection lost. Try again.");}}}finally{polling.current=false;}};
  void poll();const timer=setInterval(()=>void poll(),1500);return()=>{stopped=true;clearInterval(timer);};},[config.enabled,conversation,userId,cleanup,update,terminate]);
  useEffect(()=>{if(!config.enabled)return;let stopped=false;const refresh=()=>void getCallHistory(conversation).then(events=>{if(!stopped)onHistory(events);}).catch(()=>{});refresh();const timer=setInterval(refresh,10000);return()=>{stopped=true;clearInterval(timer);};},[config.enabled,conversation,onHistory]);
  useEffect(()=>{const timer=setInterval(()=>{if(started.current)setSeconds(Math.floor((Date.now()-started.current)/1000));},1000);return()=>clearInterval(timer);},[]);
  async function createPeer(){
    const fresh=await getCallConfig(conversation,true);if(!fresh.enabled||!fresh.iceServers?.length)throw new Error("Secure call relay unavailable. Please try again shortly.");
    if(!navigator.mediaDevices?.getUserMedia||!window.RTCPeerConnection)throw new Error("This browser does not support voice calling.");
    let local:MediaStream;try{local=await navigator.mediaDevices.getUserMedia({audio:{echoCancellation:true,noiseSuppression:true},video:false});}catch(e){if(e instanceof DOMException&&e.name==="NotAllowedError")throw new Error("Allow microphone access in your browser to call.");throw e;}
    if(!mounted.current||cancelRequested.current){local.getTracks().forEach(t=>t.stop());throw new Error("Conversation closed.");}
    stream.current=local;const connection=new RTCPeerConnection({iceServers:fresh.iceServers,iceTransportPolicy:"relay"});pc.current=connection;
    local.getAudioTracks().forEach(track=>connection.addTrack(track,local));
    connection.ontrack=e=>{if(audio.current){audio.current.srcObject=e.streams[0]??new MediaStream([e.track]);void audio.current.play().catch(()=>setAudioBlocked(true));}};
    connection.onconnectionstatechange=()=>{if(pc.current!==connection)return;const state=connection.connectionState;
      if(state==="connected"){if(cancelRequested.current)return;if(failTimer.current)clearTimeout(failTimer.current);failTimer.current=null;if(!started.current)started.current=Date.now();setPhase("Connected");}
      else if(state==="disconnected"||state==="connecting"){if(cancelRequested.current)return;if(current.current?.state==="accepted"||operation.current==="accept"){setPhase(state==="disconnected"?"Reconnecting…":"Connecting…");if(!failTimer.current)failTimer.current=setTimeout(()=>{if(pc.current===connection&&connection.connectionState!=="connected")void terminate("fail","Could not establish the call.");},45000);}}
      else if(state==="failed"){if(operation.current){if(!failTimer.current)failTimer.current=setTimeout(()=>{if(pc.current===connection&&connection.connectionState==="failed")void terminate("fail","Call connection failed. Try another network.");},45000);}else void terminate("fail","Call connection failed. Try another network.");}};
    return connection;
  }
  async function dial(){
    if(busy.current||current.current)return;busy.current=true;operation.current="dial";cancelRequested.current=false;setMinimized(false);setError("");setPhase("Preparing call…");
    try{const peer=await createPeer();await peer.setLocalDescription(await peer.createOffer());await gather(peer);if(!mounted.current||pc.current!==peer||cancelRequested.current)throw new Error("Call cancelled.");const s=await startCall(conversation,crypto.randomUUID(),peer.localDescription!.sdp);if(!mounted.current||cancelRequested.current){void changeCall(conversation,s.id,"end").catch(()=>{});cleanup();if(mounted.current)setPhase("");return;}update(s);setPhase("Calling…");}
    catch(e){cleanup();setPhase("");if(!cancelRequested.current)setError(e instanceof Error?e.message:"Could not start call.");}finally{busy.current=false;operation.current=null;}
  }
  async function accept(){
    const s=current.current;if(!s?.offer||busy.current)return;busy.current=true;operation.current="accept";cancelRequested.current=false;setMinimized(false);setError("");setPhase("Preparing call…");
    try{const peer=await createPeer();await peer.setRemoteDescription({type:"offer",sdp:s.offer});await peer.setLocalDescription(await peer.createAnswer());await gather(peer);if(!mounted.current||pc.current!==peer||cancelRequested.current)throw new Error("Call cancelled.");const next=await changeCall(conversation,s.id,"accept",peer.localDescription!.sdp);if(!live(next))throw new Error("Call ended.");if(!mounted.current){void changeCall(conversation,next.id,"end").catch(()=>{});return;}update(next);if(cancelRequested.current){await terminate("end");return;}if(!started.current)setPhase("Connecting…");}
    catch(e){await terminate(cancelRequested.current?"decline":"fail",cancelRequested.current?"":e instanceof Error?e.message:"Could not accept call.");}finally{busy.current=false;operation.current=null;}
  }

  if(!config.enabled)return null;
  const incoming=session?.state==="ringing"&&session.callee===userId&&!phase;
  const duration=String(Math.floor(seconds/60)).padStart(2,"0")+":"+String(seconds%60).padStart(2,"0");
  const active=Boolean(session||phase);
  const end=()=>{if(operation.current){cancelRequested.current=true;setPhase("Cancelling…");return;}if(cancelRequested.current&&phase==="Cancelling…")return;void terminate("end");};
  return <><button type="button" className="sera-chat-icon" aria-label={active?"Return to call":"Voice call"} title={active?"Return to call":"Voice call"} onClick={()=>active?setMinimized(false):void dial()}><Phone size={21}/></button>
  <button type="button" className="sera-chat-icon" aria-label="Recent calls" title="Recent calls" disabled={active} onClick={()=>{setHistory([]);void getCallHistory(conversation).then(events=>setHistory(previous=>previous===null?null:events)).catch(()=>{setHistory(null);setError("Call history unavailable.");});}}><History size={19}/></button>
  <audio ref={audio} autoPlay playsInline/>
  {active&&typeof document!=="undefined"&&createPortal(minimized&&!incoming?<div className="sera-aura-mini" style={{zIndex:2147483000}}><button type="button" onClick={()=>setMinimized(false)}><MemberAvatar name={name} photo={photo} size={32}/><span><strong>{name}</strong><small>{phase==="Connected"?duration:phase||"Calling…"}</small></span><Maximize2 size={17}/></button><button type="button" className="sera-aura-hang" aria-label="End call" onClick={end}><PhoneOff size={19}/></button></div>:<CallTopLayer label={incoming?"Incoming voice call":"Voice call"} onDismiss={()=>{if(!incoming)setMinimized(true);}}><div className="sera-call-overlay sera-aura-overlay"><div className="sera-call-panel sera-aura-panel">
    <header className="sera-aura-header">{!incoming?<button type="button" aria-label="Minimize call" onClick={()=>setMinimized(true)}><ChevronDown/></button>:<span/>}<span>Sera voice</span><span/></header>
    <div className="sera-aura-identity"><div className="sera-aura-avatar"><MemberAvatar name={name} photo={photo} size={104}/></div><h2>{name}</h2><p role="status">{incoming?"Incoming voice call":phase||"Calling…"}</p>{(phase==="Connected"||seconds>0)&&<strong className="sera-aura-duration">{duration}</strong>}</div>
    {audioBlocked&&<button className="sera-aura-audio" type="button" onClick={()=>void audio.current?.play().then(()=>setAudioBlocked(false)).catch(()=>{})}>Enable call audio</button>}
    <div className="sera-aura-controls">{incoming?<><button type="button" className="sera-aura-control" onClick={()=>void terminate("decline")}><span className="sera-aura-hang"><PhoneOff/></span>Decline</button><button type="button" className="sera-aura-control" onClick={()=>void accept()}><span className="sera-aura-answer"><Phone/></span>Answer</button></>:<>{session?.state==="accepted"&&<button type="button" aria-pressed={muted} className="sera-aura-control" onClick={()=>{const next=!muted;stream.current?.getAudioTracks().forEach(t=>{t.enabled=!next;});setMuted(next);}}><span className={muted?"sera-aura-answer":""}>{muted?<MicOff/>:<Mic/>}</span>{muted?"Unmute":"Mute"}</button>}<button type="button" className="sera-aura-control" onClick={end}><span className="sera-aura-hang"><PhoneOff/></span>{phase==="Cancelling…"?"Cancelling…":"End call"}</button></>}</div>
  </div></div></CallTopLayer>,document.body)}
  {history&&!active&&typeof document!=="undefined"&&createPortal(<CallTopLayer label="Call history" onDismiss={()=>setHistory(null)}><div className="sera-call-overlay sera-aura-overlay"><div className="sera-call-panel sera-aura-history"><header className="sera-aura-header"><span/><strong>Recent calls</strong><button type="button" onClick={()=>setHistory(null)} aria-label="Close call history"><X/></button></header><MemberAvatar name={name} photo={photo} size={56}/><h2>{name}</h2>{history.length?history.map(s=><p key={s.id}>{s.caller===userId?"Outgoing":"Incoming"} · {s.state} · {new Date(s.created_at).toLocaleString()}</p>):<p>No calls yet</p>}<button type="button" className="sera-aura-audio" onClick={()=>{setHistory(null);void dial();}}><Phone size={18}/> Call</button></div></div></CallTopLayer>,document.body)}
  {error&&typeof document!=="undefined"&&createPortal(<div className="sera-call-toast" style={{zIndex:2147483001}} role="status">{error}<button type="button" onClick={()=>setError("")} aria-label="Dismiss call status"><X size={16}/></button></div>,document.body)}</>;
}
