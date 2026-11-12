export const notificationTabs=["all","unread","social","network","opportunities","collaboration"] as const;
export type NotificationTab=typeof notificationTabs[number];
export const notificationKinds={
 social:["like","comment","share","repost","reply"],
 network:["follow","connection_request","connection_accepted"],
 opportunities:["job_match","application_update","opportunity_match"],
 collaboration:["space_invitation","community_invitation","project_invitation"],
} as const;
export function notificationTab(value:unknown):NotificationTab{
 return typeof value==="string"&&notificationTabs.includes(value as NotificationTab)?value as NotificationTab:"all";
}
export function kindsForTab(tab:NotificationTab):readonly string[]|null{
 return tab==="all"||tab==="unread"?null:notificationKinds[tab];
}
export function notificationPage(value:unknown):number{
 const n=typeof value==="string"&&/^\d+$/.test(value)?Number(value):1;
 return Number.isSafeInteger(n)?Math.max(1,Math.min(100,n)):1;
}
export function notificationHref(tab:NotificationTab,page=1){
 const p=new URLSearchParams();if(tab!=="all")p.set("tab",tab);if(page>1)p.set("page",String(page));
 return "/notifications"+(p.size?"?"+p.toString():"");
}
export function notificationTimeGroup(created:string,now=Date.now()):"Today"|"Yesterday"|"Earlier"{
 const d=new Date(created);if(Number.isNaN(d.getTime()))return "Earlier";
 // Calendar dates in the platform's Ethiopia timezone, rather than elapsed 24-hour windows.
 const day=(t:number)=>new Intl.DateTimeFormat("en-CA",{timeZone:"Africa/Addis_Ababa",year:"numeric",month:"2-digit",day:"2-digit"}).format(new Date(t));
 return day(d.getTime())===day(now)?"Today":day(d.getTime())===day(now-86400000)?"Yesterday":"Earlier";
}

// Request-scoped clock for server-rendered notification date sections.
export function notificationRequestTime(){return Date.now();}

export type NotificationEvent={id:string;actor_id:string;kind:string;post_id:string|null;created_at:string;read_at:string|null};
export type NotificationGroup<T extends NotificationEvent>={key:string;latest:T;events:T[];actors:string[];unreadIds:string[]};
const groupable=new Set(["like","share","repost","comment"]);
// Consecutive related events only, same calendar day, at most a 6-hour span.
export function groupNotifications<T extends NotificationEvent>(events:readonly T[]):NotificationGroup<T>[]{
 const groups:NotificationGroup<T>[]=[];
 const seen=new Set<string>();
 const dateKey=(s:string)=>{const t=Date.parse(s);return Number.isFinite(t)?new Intl.DateTimeFormat("en-CA",{timeZone:"Africa/Addis_Ababa",year:"numeric",month:"2-digit",day:"2-digit"}).format(new Date(t)):"";};
 for(const event of events){
  if(seen.has(event.id))continue;seen.add(event.id);
  const prev=groups.at(-1);const latest=prev?.latest;
  const span=latest?Date.parse(latest.created_at)-Date.parse(event.created_at):NaN;
  const same=!!(prev&&latest&&event.post_id&&event.post_id===latest.post_id&&event.kind===latest.kind&&groupable.has(event.kind)&&span>=0&&span<=21600000&&dateKey(event.created_at)===dateKey(latest.created_at));
  if(same&&prev){prev.events.push(event);if(!prev.actors.includes(event.actor_id))prev.actors.push(event.actor_id);if(!event.read_at)prev.unreadIds.push(event.id);}
  else groups.push({key:event.id,latest:event,events:[event],actors:[event.actor_id],unreadIds:event.read_at?[]:[event.id]});
 }
 return groups;
}
export function notificationActorSummary(names:readonly string[]){
 if(names.length<=2)return names.join(" and ");
 return names.slice(0,2).join(", ")+" and "+(names.length-2)+" other"+(names.length===3?"":"s");
}
