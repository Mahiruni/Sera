export const speechProfiles={clear:32000,balanced:20000,resilient:12000} as const;
export type SpeechProfile=keyof typeof speechProfiles;
export type SpeechLink={loss?:number;rttSeconds?:number;outgoingBps?:number};
export class SpeechAdaptation {
 profile:SpeechProfile="balanced";
 private good=0;
 update(link:SpeechLink):SpeechProfile{
  const loss=typeof link.loss==="number"&&Number.isFinite(link.loss)&&link.loss>=0&&link.loss<=1?link.loss:undefined;
  const rtt=typeof link.rttSeconds==="number"&&Number.isFinite(link.rttSeconds)&&link.rttSeconds>=0?link.rttSeconds:undefined;
  const bandwidth=typeof link.outgoingBps==="number"&&Number.isFinite(link.outgoingBps)&&link.outgoingBps>0?link.outgoingBps:undefined;
  const severe=(loss!==undefined&&loss>=.12)||(rtt!==undefined&&rtt>=.8)||(bandwidth!==undefined&&bandwidth<40000);
  const stressed=(loss!==undefined&&loss>=.05)||(rtt!==undefined&&rtt>=.4)||(bandwidth!==undefined&&bandwidth<75000);
  const healthy=loss!==undefined&&loss<.025&&rtt!==undefined&&rtt<.3&&(bandwidth===undefined||bandwidth>=90000);
  if(severe){this.profile="resilient";this.good=0;}
  else if(stressed){if(this.profile==="clear")this.profile="balanced";this.good=0;}
  else if(healthy){if(++this.good>=3){this.profile=this.profile==="resilient"?"balanced":"clear";this.good=0;}}
  else this.good=0;
  return this.profile;
 }
}
type VoiceStat={id:string;type:string;kind?:string;mediaType?:string;timestamp?:number;selectedCandidatePairId?:string;fractionLost?:number;roundTripTime?:number;currentRoundTripTime?:number;availableOutgoingBitrate?:number};
export function readSpeechLink(stats:Iterable<VoiceStat>):{link:SpeechLink;remoteTimestamp?:number}{
 const values=Array.from(stats),remote=values.find(s=>s.type==="remote-inbound-rtp"&&(s.kind==="audio"||s.mediaType==="audio"));
 const selected=values.find(s=>s.type==="transport")?.selectedCandidatePairId,pair=values.find(s=>s.id===selected&&s.type==="candidate-pair");
 return {link:{loss:remote?.fractionLost,rttSeconds:remote?.roundTripTime??pair?.currentRoundTripTime,outgoingBps:pair?.availableOutgoingBitrate},remoteTimestamp:remote?.timestamp};
}

/** A small receive-side buffering hint; the browser remains responsible for actual playout. */
export function speechJitterTargetMs(jitterSeconds:number|undefined):number|undefined{
 if(typeof jitterSeconds!=="number"||!Number.isFinite(jitterSeconds)||jitterSeconds<0)return undefined;
 return Math.min(120,Math.max(40,Math.round(jitterSeconds*2000/20)*20));
}
export function speechQualityWarning(loss:number|undefined,jitterSeconds:number|undefined,rttSeconds:number|undefined):boolean{
 return (typeof loss==="number"&&Number.isFinite(loss)&&loss>=.08&&loss<=1)||(typeof jitterSeconds==="number"&&Number.isFinite(jitterSeconds)&&jitterSeconds>=.06)||(typeof rttSeconds==="number"&&Number.isFinite(rttSeconds)&&rttSeconds>=.8);
}
