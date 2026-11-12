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
