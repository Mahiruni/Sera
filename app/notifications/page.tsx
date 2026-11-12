import Link from "next/link";
import { PageIntro } from "@/components/page-intro";
import { Bell, Heart, MessageCircle, Share2, UserPlus, CheckCheck } from "lucide-react";
import { requiredProfile } from "@/lib/auth/profile";
import { serverClient } from "@/lib/supabase/server";
import { memberCards } from "@/lib/activity/members";
import { MemberAvatar } from "@/components/activity/avatar";
import { markNotificationRead, markAllNotificationsRead, professionalUnreadCount } from "@/lib/network/notifications";
import { notificationTabs, notificationTab, kindsForTab, notificationPage, notificationHref, notificationTimeGroup, notificationRequestTime } from "@/lib/network/notification-model";
import { NotificationRefresh } from "@/components/network/notification-refresh";
export const metadata={title:"Notifications | SERA.et",robots:{index:false,follow:false}};
const descriptions:Record<string,string>={
 like:"liked your post",comment:"commented on your post",share:"shared your post",repost:"reposted your post",reply:"replied to your comment",
 follow:"started following you",connection_request:"sent you a connection invitation",
 connection_accepted:"accepted your connection invitation"
};
const labels={all:"All activity",unread:"Unread",social:"Social",network:"Network",opportunities:"Opportunities",collaboration:"Collaboration"};
export default async function NotificationsPage({searchParams}:{searchParams:Promise<{tab?:string;page?:string}>}){
 const {user}=await requiredProfile("/notifications");
 const db=await serverClient();
 const params=await searchParams;const tab=notificationTab(params.tab);const page=notificationPage(params.page);const kinds=kindsForTab(tab);
 let query=db.from("professional_notifications").select("id,actor_id,kind,post_id,created_at,read_at").eq("recipient_id",user.id);
 if(tab==="unread")query=query.is("read_at",null);
 if(kinds)query=query.in("kind",[...kinds]);
 const start=(page-1)*30;
 const {data:rows,error}=await query.order("created_at",{ascending:false}).order("id",{ascending:false}).range(start,start+30);
 const notifications=(rows??[]).slice(0,30);const hasMore=(rows?.length??0)>30;const now=notificationRequestTime();
 const people=await memberCards(notifications.map(n=>n.actor_id));
 const unread=await professionalUnreadCount().catch(()=>notifications.filter(n=>!n.read_at).length);
 return <div className="sera-network network-app sera-notifications"><NotificationRefresh />
  <PageIntro icon={Bell} title="Notifications" description="Keep up with the people and ideas that matter to you.">
    <div className="sera-notifications-tools"><span className="sera-notifications-count" aria-live="polite"><Bell size={16}/>{unread} unread</span>{unread>0&&<form action={markAllNotificationsRead}><button className="sera-notifications-mark-all" type="submit"><CheckCheck size={16}/> Mark all read</button></form>}</div>
  </PageIntro>
  {error&&<p role="alert">Notifications are temporarily unavailable. Please try again.</p>}
  <nav className="sera-notification-tabs" aria-label="Notification categories">{notificationTabs.map(t=><Link key={t} href={notificationHref(t)} aria-current={tab===t?"page":undefined}>{labels[t]}{t==="unread"&&unread>0?" · "+unread:""}</Link>)}</nav>
  <div className="sera-notifications-list">
  {notifications.map((n,index)=>{
   const group=notificationTimeGroup(n.created_at,now);const previous=index?notificationTimeGroup(notifications[index-1].created_at,now):null;
   const person=people.get(n.actor_id);
   const href=n.post_id?"/posts/"+n.post_id:n.kind.startsWith("connection")?"/connections":"/members/"+n.actor_id;
   const Icon=n.kind==="like"?Heart:n.kind==="comment"?MessageCircle:n.kind==="share"?Share2:UserPlus;
   return <div key={n.id} className="sera-notification-entry">{group!==previous&&<h2 className="sera-notification-date">{group}</h2>}<article className={"sera-notification"+(!n.read_at?" is-unread":"")}>
    <Link href={href} className="sera-notification-link">
     <span className="sera-notification-avatar"><MemberAvatar name={person?.name??"SERA member"} photo={person?.photo} size={48}/><span className="sera-notification-icon"><Icon size={13}/></span></span>
     <span className="sera-notification-copy"><strong>{person?.name??"SERA member"}</strong> {descriptions[n.kind]??"interacted with you"}<small>{new Date(n.created_at).toLocaleString()}</small></span>
    </Link>
    {!n.read_at&&<form action={markNotificationRead}><input type="hidden" name="id" value={n.id}/><button type="submit" className="sera-notification-read" aria-label={"Mark notification from "+(person?.name??"member")+" as read"} title="Mark as read"><CheckCheck size={17}/></button></form>}
   </article></div>;
  })}
  {!error&&!notifications.length&&<div className="sera-notifications-empty"><Bell size={32}/><h2>{tab==="all"?"You’re all caught up":tab==="unread"?"No unread notifications":"No "+labels[tab].toLowerCase()+" activity yet"}</h2><p>{tab==="all"?"New professional activity will appear here.":"New activity in this category will appear here."}</p><Link href="/discover?tab=people">Discover professionals</Link></div>}
  </div>
  {(page>1||hasMore)&&<nav className="sera-notification-pagination" aria-label="Notification pages">{page>1&&<Link href={notificationHref(tab,page-1)}>Previous</Link>}<span>Page {page}</span>{hasMore&&<Link href={notificationHref(tab,page+1)}>Next</Link>}</nav>}
 </div>;
}
