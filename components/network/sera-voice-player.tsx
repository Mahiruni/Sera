"use client";
import {useRef,useState} from "react";
import {Play,Pause,RotateCcw,LoaderCircle} from "lucide-react";
const clock=(n:number)=>Math.floor(Math.max(0,n)/60)+":"+String(Math.floor(Math.max(0,n))%60).padStart(2,"0");
export function SeraVoicePlayer({src,durationMs,label="Voice message"}:{src:string;durationMs?:number|null;label?:string}){
 const audio=useRef<HTMLAudioElement>(null);
 const [playing,setPlaying]=useState(false),[waiting,setWaiting]=useState(false),[failed,setFailed]=useState(false),[position,setPosition]=useState(0),[duration,setDuration]=useState((durationMs??0)/1000);
 const [speed,setSpeed]=useState(1);
 const cycle=()=>{const next=speed===1?1.5:speed===1.5?2:1;setSpeed(next);if(audio.current)audio.current.playbackRate=next;};
 const toggle=async()=>{const a=audio.current;if(!a)return;if(!a.paused){a.pause();return;}try{setFailed(false);setWaiting(true);if(a.error)a.load();if(a.ended)a.currentTime=0;await a.play();}catch{setFailed(true);setWaiting(false);}};
 const sync=()=>{const a=audio.current;if(a){setPosition(a.currentTime||0);if(Number.isFinite(a.duration)&&a.duration>0)setDuration(a.duration);}};
 return <div className="sera-voice-player" aria-label={label}>
  <audio ref={audio} data-sera-voice="true" src={src} preload="none" onLoadedMetadata={sync} onDurationChange={sync} onTimeUpdate={sync} onPlay={e=>{const current=e.currentTarget;document.querySelectorAll<HTMLAudioElement>("audio[data-sera-voice]").forEach(a=>{if(a!==current)a.pause();});setPlaying(true);setWaiting(false);}} onPause={()=>{setPlaying(false);setWaiting(false);}} onEnded={()=>setPlaying(false)} onWaiting={()=>setWaiting(true)} onCanPlay={()=>setWaiting(false)} onError={()=>{setFailed(true);setWaiting(false);setPlaying(false);}}/>
  <button className="sera-voice-play" type="button" aria-label={failed?"Retry voice message":playing?"Pause voice message":"Play voice message"} onClick={()=>void toggle()}>{waiting?<LoaderCircle size={19} className="sera-voice-spin"/>:failed?<RotateCcw size={19}/>:playing?<Pause size={20}/>:<Play size={20}/>}</button>
  <div className="sera-voice-track"><span className="sera-voice-title">{label}</span><input type="range" aria-label="Voice playback position" min={0} max={duration||1} step={0.1} value={Math.min(position,duration||1)} disabled={!duration||failed} onChange={e=>{const next=Number(e.target.value);if(audio.current){try{audio.current.currentTime=next;setPosition(next);}catch{}}}}/><span className="sera-voice-time">{failed?"Audio unavailable · retry":clock(position)+" / "+clock(duration)}</span></div>
  <button type="button" className="sera-voice-speed" aria-label="Change playback speed" onClick={cycle}>{speed}×</button>
 </div>;
}
