export type MessagePreferences={notifications_enabled:boolean;quiet_enabled:boolean;quiet_start:number;quiet_end:number;quiet_timezone:string};
export const defaultMessagePreferences:MessagePreferences={notifications_enabled:true,quiet_enabled:false,quiet_start:1320,quiet_end:420,quiet_timezone:"Africa/Addis_Ababa"};
export function shouldSuppressMessage(p:MessagePreferences|null,mutedUntil:string|null,now:Date):boolean{
 if(mutedUntil==="infinity"||mutedUntil&&Date.parse(mutedUntil)>now.getTime())return true;
 if(!p)return false;
 if(!p.notifications_enabled)return true;
 if(!p.quiet_enabled)return false;
 try{const parts=new Intl.DateTimeFormat("en-GB",{timeZone:p.quiet_timezone,hour:"2-digit",minute:"2-digit",hourCycle:"h23"}).formatToParts(now);
 const minute=Number(parts.find(x=>x.type==="hour")?.value)*60+Number(parts.find(x=>x.type==="minute")?.value);
 if(!Number.isFinite(minute))return true;
 return p.quiet_start<p.quiet_end?minute>=p.quiet_start&&minute<p.quiet_end:minute>=p.quiet_start||minute<p.quiet_end;
 }catch{return true;}
}
export function parseClock(v:unknown):number|null{if(typeof v!=="string"||!/^([01]\d|2[0-3]):[0-5]\d$/.test(v))return null;const [h,m]=v.split(":").map(Number);return h*60+m;}
export function clockValue(v:number){return String(Math.floor(v/60)).padStart(2,"0")+":"+String(v%60).padStart(2,"0");}
