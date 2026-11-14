import Link from "next/link";
import { PageIntro } from "@/components/page-intro";
import { Bell, Heart, MessageCircle, Share2, UserPlus, CheckCheck, BriefcaseBusiness, PhoneMissed } from "lucide-react";
import { requiredProfile } from "@/lib/auth/profile";
import { serverClient } from "@/lib/supabase/server";
import { memberCards } from "@/lib/activity/members";
import { MemberAvatar } from "@/components/activity/avatar";
import { markNotificationGroupRead, markAllNotificationsRead, professionalUnreadCount, refreshJobAlerts, setAutomaticJobAlerts } from "@/lib/network/notifications";
import { notificationTabs, notificationTab, kindsForTab, notificationPage, notificationHref, notificationTimeGroup, notificationRequestTime, groupNotifications, notificationActorSummary, communicationNotificationHref } from "@/lib/network/notification-model";
import { NotificationRefresh } from "@/components/network/notification-refresh";
export const metadata={title:"Notifications | Sera",robots:{index:false,follow:false}};
const descriptions:Record<string,string>={
 like:"liked your post",comment:"commented on your post",share:"shared your post",repost:"reposted your post",reply:"replied to your comment",
 follow:"started following you",connection_request:"sent you a connection invitation",
 connection_accepted:"accepted your connection invitation",space_message:"sent a message in your space",missed_call:"called you while you were away"
};
const labels={all:"All activity",unread:"Unread",social:"Social",network:"Network",opportunities:"Opportunities",collaboration:"Collaboration",calls:"Calls"};
export default async function NotificationsPage({searchParams}:{searchParams:Promise<{tab?:string;page?:string}>}){
 const {user}=await requiredProfile("/notifications");
 const db=await serverClient();
 const params=await searchParams;const tab=notificationTab(params.tab);const page=notificationPage(params.page);const kinds=kindsForTab(tab);
 let query=db.from("professional_notifications").select("id,actor_id,kind,post_id,job_id,match_reasons,created_at,read_at,space_id,conversation_id").eq("recipient_id",user.id);
 if(tab==="unread")query=query.is("read_at",null);
 if(kinds)query=query.in("kind",[...kinds]);
 const start=(page-1)*30;
 const {data:rows,error}=await query.order("created_at",{ascending:false}).order("id",{ascending:false}).range(start,start+30);
 const notifications=(rows??[]).slice(0,30);const hasMore=(rows?.length??0)>30;const now=notificationRequestTime();
 const grouped=groupNotifications(notifications);
 const jobIds=notifications.filter(n=>n.kind==="job_match"&&n.job_id).map(n=>n.job_id);
 const jobResult=jobIds.length?await db.from("jobs").select("id,title,company").in("id",jobIds).eq("status","open"):{data:[]};
 const jobs=new Map((jobResult.data??[]).map(j=>[j.id,j]));
 const people=await memberCards(notifications.filter(n=>n.kind!=="job_match").map(n=>n.actor_id));
 const preference=tab==="opportunities"?await db.from("sera_job_alert_preferences").select("enabled").eq("user_id",user.id).maybeSingle():null;
 const automatic=preference?.data?.enabled===true;
 const unread=await professionalUnreadCount().catch(()=>notifications.filter(n=>!n.read_at).length);
 return <div className="sera-network network-app sera-notifications"><NotificationRefresh />
  <PageIntro icon={Bell} title="Notifications" description="Keep up with the people and ideas that matter to you.">
    <div className="sera-notifications-tools"><span className="sera-notifications-count" aria-live="polite"><Bell size={16}/>{unread} unread</span>{unread>0&&<form action={markAllNotificationsRead}><button className="sera-notifications-mark-all" type="submit"><CheckCheck size={16}/> Mark all read</button></form>}</div>
  </PageIntro>
  {error&&<p role="alert">Notifications are temporarily unavailable. Please try again.</p>}
  <nav className="sera-notification-tabs" aria-label="Notification categories">{notificationTabs.map(t=><Link key={t} href={notificationHref(t)} aria-current={tab===t?"page":undefined}>{labels[t]}{t==="unread"&&unread>0?" · "+unread:""}</Link>)}</nav>
  {tab==="opportunities"&&<section className="network-universal-section"><h2>Career alerts</h2><p>Get relevant new jobs in your Sera inbox. Up to three matches per day.</p>{preference?.error?<p role="status">Alert preferences temporarily unavailable.</p>:<form action={setAutomaticJobAlerts}><input type="hidden" name="enabled" value={automatic?"false":"true"}/><button className="network-button" type="submit">{automatic?"Turn off automatic alerts":"Turn on automatic alerts"}</button><p className="network-search-caption">{automatic?"Automatic alerts enabled":"Automatic alerts are off"}</p></form>}<form action={refreshJobAlerts}><button type="submit" className="network-button">Refresh job matches</button></form></section>}
  <div className="sera-notifications-list">
  {grouped.map((entry,index)=>{
   const n=entry.latest;const isUnread=entry.unreadIds.length>0;
   const group=notificationTimeGroup(n.created_at,now);const previous=index?notificationTimeGroup(grouped[index-1].latest.created_at,now):null;
   const person=people.get(n.actor_id);const names=entry.actors.map(id=>people.get(id)?.name??"Sera member");const multiple=entry.events.length>1;
   const isJob=n.kind==="job_match";const job=isJob?jobs.get(n.job_id):undefined;
   const communicationHref=communicationNotificationHref(n);
   const href=communicationHref??(isJob?(job?"/jobs/"+job.id:"/jobs"):n.post_id?"/posts/"+n.post_id:n.kind.startsWith("connection")?"/connections":"/members/"+n.actor_id);
   const Icon=n.kind==="missed_call"?PhoneMissed:n.kind==="space_message"?MessageCircle:isJob?BriefcaseBusiness:n.kind==="like"?Heart:n.kind==="comment"?MessageCircle:n.kind==="share"?Share2:UserPlus;
   return <div key={n.id} className="sera-notification-entry">{group!==previous&&<h2 className="sera-notification-date">{group}</h2>}<article className={"sera-notification"+(isUnread?" is-unread":"")}>
    <Link href={href} className="sera-notification-link">
     <span className="sera-notification-avatar">{isJob?<span className="network-universal-icon"><BriefcaseBusiness size={22}/></span>:multiple?<span className="sera-notification-avatar-stack">{entry.actors.slice(0,2).map(id=><MemberAvatar key={id} name={people.get(id)?.name??"Sera member"} photo={people.get(id)?.photo} size={34}/>)}</span>:<MemberAvatar name={person?.name??"Sera member"} photo={person?.photo} size={48}/>}<span className="sera-notification-icon"><Icon size={13}/></span></span>
     <span className="sera-notification-copy">{isJob?<><strong>{job?job.title:"Opportunity no longer available"}</strong>{job&&<><span> · {job.company}</span><small>Matches: {(n.match_reasons??[]).join(", ")}</small></>}</>:<><strong>{notificationActorSummary(names)}</strong> {descriptions[n.kind]??"interacted with you"}{n.kind==="missed_call"&&<small>Open conversation to call back</small>}{n.kind==="space_message"&&<small>Open team conversation</small>}{multiple&&<small>{entry.events.length} activities{isUnread?" · "+entry.unreadIds.length+" unread":""}</small>}</>}<small>{new Date(n.created_at).toLocaleString()}</small></span>
    </Link>
    {isUnread&&<form action={markNotificationGroupRead}>{entry.unreadIds.map(id=><input key={id} type="hidden" name="ids" value={id}/>)}<button type="submit" className="sera-notification-read" aria-label={multiple?"Mark "+entry.unreadIds.length+" activities as read":isJob?"Mark opportunity alert as read":"Mark notification from "+(person?.name??"member")+" as read"} title="Mark as read"><CheckCheck size={17}/></button></form>}
   </article></div>;
  })}
  {!error&&!notifications.length&&<div className="sera-notifications-empty"><Bell size={32}/><h2>{tab==="all"?"You’re all caught up":tab==="unread"?"No unread notifications":"No "+labels[tab].toLowerCase()+" activity yet"}</h2><p>{tab==="all"?"New professional activity will appear here.":"New activity in this category will appear here."}</p><Link href="/discover?tab=people">Discover professionals</Link></div>}
  </div>
  {(page>1||hasMore)&&<nav className="sera-notification-pagination" aria-label="Notification pages">{page>1&&<Link href={notificationHref(tab,page-1)}>Previous</Link>}<span>Page {page}</span>{hasMore&&<Link href={notificationHref(tab,page+1)}>Next</Link>}</nav>}
 </div>;
}
