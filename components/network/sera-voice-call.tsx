"use client";
import {useCallback,useEffect,useRef,useState} from "react";
import {Phone,PhoneOff,Mic,MicOff,X} from "lucide-react";
import {MemberAvatar} from "@/components/activity/avatar";
import {getCallConfig,getCallStatus,startCall,changeCall,getCallHistory,type CallSession,type CallConfig} from "@/lib/network/voice-calls";

const live=(s:CallSession)=>s.state==="ringing"||s.state==="accepted";
async function gather(pc:RTCPeerConnection){
  if(pc.iceGatheringState==="complete")return;
  await new Promise<void>((resolve,reject)=>{
    const done=()=>{clearTimeout(timer);pc.removeEventListener("icegatheringstatechange",check);};
    const check=()=>{if(pc.iceGatheringState==="complete"){done();resolve();}};
    const timer=setTimeout(()=>{done();reject(new Error("Could not prepare the call connection."));},12000);
    pc.addEventListener("icegatheringstatechange",check);check();
  });
}
export function SeraVoiceCall({conversation,userId,name,photo}:{conversation:string;userId:string;name:string;photo?:string|null}){
  const [config,setConfig]=useState<CallConfig>({enabled:false}),[session,setSession]=useState<CallSession|null>(null),[phase,setPhase]=useState(""),[muted,setMuted]=useState(false),[seconds,setSeconds]=useState(0),[error,setError]=useState(""),[history,setHistory]=useState<CallSession[]|null>(null),[audioBlocked,setAudioBlocked]=useState(false);
  const pc=useRef<RTCPeerConnection|null>(null),stream=useRef<MediaStream|null>(null),audio=useRef<HTMLAudioElement>(null),current=useRef<CallSession|null>(null),busy=useRef(false),mounted=useRef(true),started=useRef(0),lastRemote=useRef(""),failTimer=useRef<ReturnType<typeof setTimeout>|null>(null),pollErrors=useRef(0);
  const update=useCallback((s:CallSession|null)=>{current.current=s;if(mounted.current)setSession(s);},[]);
  const cleanup=useCallback(()=>{if(failTimer.current)clearTimeout(failTimer.current);failTimer.current=null;const connection=pc.current;pc.current=null;if(connection){connection.onconnectionstatechange=null;connection.ontrack=null;connection.close();}stream.current?.getTracks().forEach(t=>t.stop());stream.current=null;if(audio.current)audio.current.srcObject=null;lastRemote.current="";started.current=0;if(mounted.current){setMuted(false);setSeconds(0);setAudioBlocked(false);}},[]);
  const terminate=useCallback(async(action:"end"|"decline"|"fail",message="")=>{const s=current.current;cleanup();update(null);setPhase("");if(message)setError(message);if(s&&live(s)){try{await changeCall(conversation,s.id,action);}catch{if(mounted.current)setError("Call closed locally. Remote status will expire.");}}},[cleanup,conversation,update]);
  useEffect(()=>{mounted.current=true;void getCallConfig(conversation).then(c=>{if(mounted.current)setConfig(c);});return()=>{mounted.current=false;const s=current.current;cleanup();if(s&&live(s))void changeCall(conversation,s.id,"end").catch(()=>{});};},[conversation,cleanup]);
  useEffect(()=>{if(!config.enabled)return;let stopped=false;const poll=async()=>{if(stopped||busy.current)return;busy.current=true;try{
    const prior=current.current;const s=await getCallStatus(conversation,prior?.id??null);if(stopped)return;pollErrors.current=0;
    if(!s){if(prior){cleanup();update(null);setPhase("");setError("Call is no longer available.");}return;}
    if(!live(s)){if(prior){cleanup();update(null);setPhase("");setError(s.state==="missed"?"No answer":s.state==="declined"?"Call declined":s.state==="failed"?"Call connection failed":"Call ended");}return;}
    if(s.state==="accepted"&&!pc.current){if(prior){update(null);setPhase("");setError("Call answered on another device.");}return;}
    if(!prior&&s.caller===userId)return;
    update(s);
    if(s.state==="accepted"&&s.caller===userId&&s.answer&&pc.current&&lastRemote.current!==s.answer){lastRemote.current=s.answer;await pc.current.setRemoteDescription({type:"answer",sdp:s.answer});if(mounted.current&&!started.current)setPhase("Connecting…");}
  }catch{if(++pollErrors.current>=3&&current.current){await terminate("fail","Call connection lost.");}}finally{busy.current=false;}};
  void poll();const timer=setInterval(()=>void poll(),1500);return()=>{stopped=true;clearInterval(timer);};},[config.enabled,conversation,userId,cleanup,update,terminate]);
  useEffect(()=>{const timer=setInterval(()=>{if(started.current)setSeconds(Math.floor((Date.now()-started.current)/1000));},1000);return()=>clearInterval(timer);},[]);
  async function createPeer(){
    const fresh=await getCallConfig(conversation);if(!fresh.enabled||!fresh.iceServers?.length)throw new Error("Calling is not available.");
    if(!navigator.mediaDevices?.getUserMedia||!window.RTCPeerConnection)throw new Error("This browser does not support voice calling.");
    const local=await navigator.mediaDevices.getUserMedia({audio:{echoCancellation:true,noiseSuppression:true},video:false});
    if(!mounted.current){local.getTracks().forEach(t=>t.stop());throw new Error("Conversation closed.");}
    stream.current=local;const connection=new RTCPeerConnection({iceServers:fresh.iceServers,iceTransportPolicy:"relay"});pc.current=connection;
    local.getAudioTracks().forEach(track=>connection.addTrack(track,local));
    connection.ontrack=e=>{if(audio.current){audio.current.srcObject=e.streams[0]??new MediaStream([e.track]);void audio.current.play().catch(()=>setAudioBlocked(true));}};
    connection.onconnectionstatechange=()=>{if(pc.current!==connection)return;const state=connection.connectionState;
      if(state==="connected"){if(failTimer.current)clearTimeout(failTimer.current);failTimer.current=null;if(!started.current)started.current=Date.now();setPhase("Connected");}
      else if(state==="disconnected"||state==="connecting"){setPhase(state==="disconnected"?"Reconnecting…":"Connecting…");if(!failTimer.current)failTimer.current=setTimeout(()=>void terminate("fail","Could not establish the call."),25000);}
      else if(state==="failed")void terminate("fail","Call connection failed.");};
    return connection;
  }
  async function dial(){
    if(busy.current||current.current)return;busy.current=true;setError("");setPhase("Preparing call…");
    try{const peer=await createPeer();await peer.setLocalDescription(await peer.createOffer());await gather(peer);if(!mounted.current||pc.current!==peer)throw new Error("Call cancelled.");const s=await startCall(conversation,crypto.randomUUID(),peer.localDescription!.sdp);if(!mounted.current){void changeCall(conversation,s.id,"end").catch(()=>{});return;}update(s);setPhase("Ringing…");}
    catch(e){cleanup();setPhase("");setError(e instanceof Error?e.message:"Could not start call.");}finally{busy.current=false;}
  }
  async function accept(){
    const s=current.current;if(!s?.offer||busy.current)return;busy.current=true;setError("");setPhase("Preparing call…");
    try{const peer=await createPeer();await peer.setRemoteDescription({type:"offer",sdp:s.offer});await peer.setLocalDescription(await peer.createAnswer());await gather(peer);if(!mounted.current||pc.current!==peer)throw new Error("Call cancelled.");const next=await changeCall(conversation,s.id,"accept",peer.localDescription!.sdp);if(!live(next))throw new Error("Call ended.");update(next);if(!started.current)setPhase("Connecting…");}
    catch(e){await terminate("fail",e instanceof Error?e.message:"Could not accept call.");}finally{busy.current=false;}
  }
  useEffect(()=>{if(session)setHistory(null);},[session]);
  if(!config.enabled)return null;
  const incoming=session?.state==="ringing"&&session.callee===userId&&!phase;
  const duration=String(Math.floor(seconds/60)).padStart(2,"0")+":"+String(seconds%60).padStart(2,"0");
  return <><button type="button" className="sera-chat-icon" aria-label="Voice call" title="Voice call" disabled={Boolean(session||phase)} onClick={()=>{setHistory([]);void getCallHistory(conversation).then(setHistory).catch(()=>setError("Call history unavailable."));}}><Phone size={21}/></button>
  <audio ref={audio} autoPlay playsInline/>
  {(session||phase)&&<div className="sera-call-overlay" role="dialog" aria-modal="true" aria-label={incoming?"Incoming voice call":"Voice call"}><div className="sera-call-panel"><span className="sera-call-brand">SERA · Voice call</span><MemberAvatar name={name} photo={photo} size={88}/><h2>{name}</h2><p role="status">{incoming?"Incoming voice call":phase||"Ringing…"}{phase==="Connected"?" · "+duration:""}</p>{audioBlocked&&<button type="button" onClick={()=>void audio.current?.play().then(()=>setAudioBlocked(false)).catch(()=>{})}>Enable call audio</button>}<div className="sera-call-controls">{incoming?<><button type="button" className="sera-call-accept" onClick={()=>void accept()}><Phone/> Accept</button><button type="button" className="sera-call-end" onClick={()=>void terminate("decline")}><PhoneOff/> Decline</button></>:<><button type="button" onClick={()=>{const next=!muted;stream.current?.getAudioTracks().forEach(t=>{t.enabled=!next;});setMuted(next);}} disabled={!session||session.state!=="accepted"}>{muted?<MicOff/>:<Mic/>}{muted?"Unmute":"Mute"}</button><button type="button" className="sera-call-end" disabled={!session} onClick={()=>void terminate("end")}><PhoneOff/> End call</button></>}</div></div></div>}
  {history&&<div className="sera-call-overlay" role="dialog" aria-modal="true" aria-label="Call history"><div className="sera-call-panel"><button type="button" className="sera-chat-icon" onClick={()=>setHistory(null)} aria-label="Close call history"><X/></button><h2>Voice call</h2><p>Call {name}</p><div className="sera-call-controls"><button type="button" className="sera-call-accept" disabled={Boolean(session||phase)} onClick={()=>{setHistory(null);void dial();}}><Phone/> Call</button></div><h3>Recent calls</h3>{history.length?history.map(s=><p key={s.id}>{s.caller===userId?"Outgoing":"Incoming"} · {s.state} · {new Date(s.created_at).toLocaleString()}</p>):<p>No calls yet</p>}</div></div>}
  {error&&<div className="sera-call-toast" role="status">{error}<button type="button" onClick={()=>setError("")} aria-label="Dismiss call status"><X size={16}/></button></div>}</>;
}
