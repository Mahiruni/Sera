"use client";
import {useEffect,useRef,useState} from "react";
import {Mic,Square,X,Pause,Play} from "lucide-react";
import {CHAT_MIME_EXT,FILE_LIMIT,VOICE_LIMIT_MS,isChatMime,type AttachmentMeta} from "@/lib/network/message-contract";
export function VoiceRecorder({disabled,onFile,onError}:{disabled:boolean;onFile:(file:File,meta:AttachmentMeta)=>void;onError:(message:string)=>void}){
  const recorder=useRef<MediaRecorder|null>(null),stream=useRef<MediaStream|null>(null),timer=useRef<ReturnType<typeof setInterval>|null>(null),cancelled=useRef(false),mounted=useRef(true),started=useRef(0),pausedAt=useRef(0),pausedTotal=useRef(0);
  const meter=useRef<{context:AudioContext;analyser:AnalyserNode;frame:number}|null>(null);
  const [levels,setLevels]=useState<number[]>(Array(18).fill(0));
  function startMeter(audio:MediaStream){try{const context=new AudioContext();const analyser=context.createAnalyser();analyser.fftSize=256;context.createMediaStreamSource(audio).connect(analyser);const samples=new Uint8Array(analyser.fftSize);const m={context,analyser,frame:0};meter.current=m;let last=0;const frame=(now:number)=>{if(meter.current!==m)return;if(now-last>=100&&recorder.current?.state==="recording"){analyser.getByteTimeDomainData(samples);const rms=Math.sqrt(samples.reduce((sum,v)=>sum+Math.pow((v-128)/128,2),0)/samples.length);setLevels(v=>[...v.slice(1),Math.min(1,rms*4)]);last=now;}m.frame=requestAnimationFrame(frame);};m.frame=requestAnimationFrame(frame);}catch{/* Recording still works without waveform support. */}}
  const [recording,setRecording]=useState(false),[requesting,setRequesting]=useState(false),[seconds,setSeconds]=useState(0),[paused,setPaused]=useState(false);
  const elapsed=()=>Math.max(0,(pausedAt.current||Date.now())-started.current-pausedTotal.current);
  const togglePause=()=>{const r=recorder.current;if(!r)return;try{if(r.state==="paused"){r.resume();pausedTotal.current+=Date.now()-pausedAt.current;pausedAt.current=0;setPaused(false);}else if(r.state==="recording"){r.pause();pausedAt.current=Date.now();setPaused(true);}}catch{onError("Could not change recording state.");}};
  const cleanup=()=>{const m=meter.current;meter.current=null;if(m){cancelAnimationFrame(m.frame);void m.context.close().catch(()=>{});}if(timer.current)clearInterval(timer.current);timer.current=null;stream.current?.getTracks().forEach(t=>t.stop());stream.current=null;recorder.current=null;};
  useEffect(()=>{mounted.current=true;return()=>{mounted.current=false;cancelled.current=true;try{if(recorder.current&&recorder.current.state!=="inactive")recorder.current.stop();}catch{}cleanup();};},[]);
  const stop=(cancel=false)=>{cancelled.current=cancel;try{if(recorder.current&&recorder.current.state!=="inactive")recorder.current.stop();}catch{cleanup();setRecording(false);onError("Recording could not be saved.");}};
  const start=async()=>{
    if(disabled||recording||requesting)return;
    if(!navigator.mediaDevices?.getUserMedia||typeof MediaRecorder==="undefined"){onError("Voice recording is unavailable in this browser. You can attach an audio file instead.");return;}
    setRequesting(true);
    try{
      const audio=await navigator.mediaDevices.getUserMedia({audio:true});
      if(!mounted.current){audio.getTracks().forEach(t=>t.stop());return;}
      stream.current=audio;cancelled.current=false;
      const type=["audio/webm;codecs=opus","audio/mp4","audio/ogg;codecs=opus"].find(t=>MediaRecorder.isTypeSupported(t));
      const rec=type?new MediaRecorder(audio,{mimeType:type,audioBitsPerSecond:64000}):new MediaRecorder(audio);
      recorder.current=rec;const chunks:Blob[]=[];let bytes=0;
      rec.ondataavailable=e=>{if(e.data.size){chunks.push(e.data);bytes+=e.data.size;if(bytes>=FILE_LIMIT&&rec.state==="recording")rec.stop();}};
      rec.onerror=()=>{cancelled.current=true;cleanup();if(mounted.current){setRecording(false);onError("Microphone recording failed.");}};
      rec.onstop=()=>{
        const duration=Math.min(VOICE_LIMIT_MS,Math.max(1,elapsed()));
        const mime=rec.mimeType.split(";")[0]||chunks[0]?.type.split(";")[0]||"audio/webm";
        cleanup();if(!mounted.current)return;setRecording(false);
        if(cancelled.current)return;
        if(!isChatMime(mime)||!mime.startsWith("audio/")){onError("This recording format is not supported.");return;}
        const file=new File(chunks,"voice-message."+CHAT_MIME_EXT[mime],{type:mime});
        if(!file.size||file.size>FILE_LIMIT){onError("Record a shorter voice message (maximum 5 MB).");return;}
        onFile(file,{name:file.name,mime,size:file.size,durationMs:duration});
      };
      rec.start(500);setLevels(Array(18).fill(0));startMeter(audio);started.current=Date.now();pausedAt.current=0;pausedTotal.current=0;setPaused(false);setSeconds(0);setRecording(true);
      timer.current=setInterval(()=>{const ms=Date.now()-started.current;setSeconds(Math.floor(ms/1000));if(ms>=VOICE_LIMIT_MS)stop();},250);
    }catch{cleanup();onError("Microphone access was denied or is unavailable.");}finally{if(mounted.current)setRequesting(false);}
  };
  if(recording)return <div className="sera-chat-recording" role="status"><span className="sera-chat-record-dot"/><span className="sera-chat-record-label">{paused?"Recording paused":"Recording voice message"}<small>{Math.floor(seconds/60)}:{String(seconds%60).padStart(2,"0")} / 2:00</small><span className="sera-voice-live-wave" aria-hidden="true">{levels.map((v,i)=><i key={i} style={{height:Math.max(3,Math.round(v*22))+"px"}}/>)}</span></span><button type="button" className="sera-chat-icon" onClick={()=>stop(true)} aria-label="Discard recording"><X size={18}/></button><button type="button" className="sera-chat-icon" onClick={togglePause} aria-label={paused?"Resume recording":"Pause recording"}>{paused?<Play size={18}/>:<Pause size={18}/>}</button><button type="button" className="sera-chat-icon sera-chat-record-stop" onClick={()=>stop()} aria-label="Stop recording"><Square size={18}/></button></div>;
  return <button type="button" className="sera-chat-icon" disabled={disabled||requesting} onClick={()=>void start()} aria-label="Record voice message" title="Record voice message"><Mic size={21}/></button>;
}
