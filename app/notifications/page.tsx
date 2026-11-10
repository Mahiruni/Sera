import Link from "next/link";
import { Bell, Heart, MessageCircle, Share2, UserPlus, CheckCheck } from "lucide-react";
import { requiredProfile } from "@/lib/auth/profile";
import { serverClient } from "@/lib/supabase/server";
import { memberCards } from "@/lib/activity/members";
import { MemberAvatar } from "@/components/activity/avatar";
import { markNotificationRead, markAllNotificationsRead } from "@/lib/network/notifications";
export const metadata={title:"Notifications | SERA.et",robots:{index:false,follow:false}};
const descriptions:Record<string,string>={
 like:"liked your post",comment:"commented on your post",share:"shared your post",
 follow:"started following you",connection_request:"sent you a connection invitation",
 connection_accepted:"accepted your connection invitation"
};
export default async function NotificationsPage(){
 const {user}=await requiredProfile("/notifications");
 const db=await serverClient();
 const {data:rows,error}=await db.from("professional_notifications")
 .select("id,actor_id,kind,post_id,created_at,read_at")
 .eq("recipient_id",user.id).order("created_at",{ascending:false}).limit(80);
 const notifications=rows??[];
 const people=await memberCards(notifications.map(n=>n.actor_id));
 const {count:totalUnread}=await db.from("professional_notifications").select("id",{count:"exact",head:true}).eq("recipient_id",user.id).is("read_at",null);
 const unread=totalUnread??notifications.filter(n=>!n.read_at).length;
 return <main className="sera-network network-app sera-notifications">
  <div className="sera-notifications-heading"><div><p className="network-kicker">YOUR PROFESSIONAL ACTIVITY</p><h1>Notifications</h1><p>Keep up with the people and ideas that matter to you.</p></div><div className="sera-notifications-tools"><span className="sera-notifications-count" aria-live="polite"><Bell size={16}/>{unread} unread</span>{unread>0&&<form action={markAllNotificationsRead}><button className="sera-notifications-mark-all" type="submit"><CheckCheck size={16}/> Mark all read</button></form>}</div></div>
  {error&&<p role="alert">Notifications are temporarily unavailable. Please try again.</p>}
  <div className="sera-notifications-list">
  {notifications.map(n=>{
   const person=people.get(n.actor_id);
   const href=n.post_id?"/posts/"+n.post_id:n.kind.startsWith("connection")?"/connections":"/members/"+n.actor_id;
   const Icon=n.kind==="like"?Heart:n.kind==="comment"?MessageCircle:n.kind==="share"?Share2:UserPlus;
   return <article key={n.id} className={"sera-notification"+(!n.read_at?" is-unread":"")}>
    <Link href={href} className="sera-notification-link">
     <span className="sera-notification-avatar"><MemberAvatar name={person?.name??"SERA member"} photo={person?.photo} size={48}/><span className="sera-notification-icon"><Icon size={13}/></span></span>
     <span className="sera-notification-copy"><strong>{person?.name??"SERA member"}</strong> {descriptions[n.kind]??"interacted with you"}<small>{new Date(n.created_at).toLocaleString()}</small></span>
    </Link>
    {!n.read_at&&<form action={markNotificationRead}><input type="hidden" name="id" value={n.id}/><button type="submit" className="sera-notification-read" aria-label={"Mark notification from "+(person?.name??"member")+" as read"} title="Mark as read"><CheckCheck size={17}/></button></form>}
   </article>;
  })}
  {!error&&!notifications.length&&<div className="sera-notifications-empty"><Bell size={32}/><h2>You&apos;re all caught up</h2><p>New likes, comments, shares, follows and connection invitations will appear here.</p><Link href="/discover?tab=people">Discover professionals</Link></div>}
  </div>
 </main>;
}
