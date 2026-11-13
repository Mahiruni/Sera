export type VideoQuality="standard"|"hd"|"super_hd";
export type VideoLevel="paused"|"data_saver"|"standard"|"hd"|"super_hd";
export const videoProfiles={
 paused:{width:0,height:0,fps:0,bitrate:0},
 data_saver:{width:640,height:360,fps:15,bitrate:220000},
 standard:{width:854,height:480,fps:24,bitrate:500000},
 hd:{width:1280,height:720,fps:24,bitrate:1200000},
 super_hd:{width:1920,height:1080,fps:30,bitrate:2500000},
} as const;
const levels:VideoLevel[]=["paused","data_saver","standard","hd","super_hd"];
export function authorizedVideoQuality(request:VideoQuality,premium:boolean):VideoQuality {
 return premium&&["hd","super_hd"].includes(request)?request:"standard";
}
export function videoConstraints(level:VideoQuality):MediaTrackConstraints{
 const p=videoProfiles[level];
 return {width:{ideal:p.width,max:p.width},height:{ideal:p.height,max:p.height},frameRate:{ideal:p.fps,max:p.fps},facingMode:"user"};
}
export class VideoAdaptation {
 level:VideoLevel="data_saver";
 private good=0;
 update(cap:VideoQuality,link:{outgoingBps?:number;loss?:number;rttSeconds?:number},dataSaver=false):VideoLevel{
  const max=levels.indexOf(dataSaver?"data_saver":cap);
  const b=link.outgoingBps,l=link.loss,r=link.rttSeconds;
  const severe=(b!==undefined&&b<140000)||(l!==undefined&&l>=.15)||(r!==undefined&&r>=1);
  const poor=(b!==undefined&&b<350000)||(l!==undefined&&l>=.07)||(r!==undefined&&r>=.6);
  const healthy=b!==undefined&&b>0&&l!==undefined&&l<.035&&r!==undefined&&r<.35;
  let n=Math.min(levels.indexOf(this.level),max);
  if(severe){n=0;this.good=0;}else if(poor){n=Math.min(n,1);this.good=0;}
  else if(healthy){
   const next=Math.min(n+1,max),p=videoProfiles[levels[next]];
   if(b!>p.bitrate*1.35+80000){if(++this.good>=3){n=next;this.good=0;}}else this.good=0;
  }else this.good=0;
  this.level=levels[n];return this.level;
 }
}
