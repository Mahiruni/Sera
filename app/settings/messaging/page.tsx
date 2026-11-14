import Link from "next/link";
import {headers} from "next/headers";
import {ArrowLeft,ArrowUpRight,Archive,Bell,CheckCheck,Clock,Eye,MessageCircle,ShieldCheck,SlidersHorizontal} from "lucide-react";
import type {ReactNode} from "react";
import {notificationText} from "@/lib/network/message-preview-model";
import {requiredProfile} from "@/lib/auth/profile";
import {serverClient} from "@/lib/supabase/server";
import {saveMessagingPreferences,saveTypingVisibility,saveMessagingActivity,saveReadReceiptVisibility,saveNotificationPreviews} from "@/lib/network/message-control-actions";
import {clockValue,defaultMessagePreferences} from "@/lib/network/message-control-model";
import styles from "./control-center.module.css";
import {controlText} from "@/lib/network/message-control-copy";
import {SaveControlButton} from "./save-control-button";
import {LiveNotificationPreview} from "./live-notification-preview";
import {notificationPreviewMode} from "@/lib/network/message-preview-model";
export const metadata={title:"Messaging control center | Sera",robots:{index:false,follow:false}};
function Toggle({name,checked,title,description}:{name:string;checked:boolean;title:string;description:string}){
 return <label className={styles.toggle}><span><strong>{title}</strong><small>{description}</small></span><input type="checkbox" name={name} defaultChecked={checked} className={styles.switch}/></label>;
}
function Panel({title,description,icon,children,id}:{title:string;description:string;icon:ReactNode;children:ReactNode;id:string}){
 return <section id={id} className={styles.panel}><header className={styles.sectionHeader}><span className={styles.icon}>{icon}</span><div><h2>{title}</h2><p>{description}</p></div></header>{children}</section>;
}
export default async function MessagingSettings(){
 const locale=(await headers()).get("x-sera-language")??"en";const tr=(value:string)=>notificationText(locale,controlText(locale,value));
 const {user}=await requiredProfile("/settings/messaging");const db=await serverClient();
 const {data,error}=await db.from("sera_message_preferences").select("notifications_enabled,quiet_enabled,quiet_start,quiet_end,quiet_timezone,typing_visible,read_receipts_visible,notification_previews,notification_preview_mode").eq("user_id",user.id).maybeSingle();
 const p=data??defaultMessagePreferences;
 const {data:presence,error:presenceError}=await db.from("professional_presence").select("visibility").eq("member_id",user.id).maybeSingle();
 return <main className={styles.root}>
 <Link href="/settings" className={styles.back}><ArrowLeft size={18}/> {tr("Back to settings")}</Link>
 <header className={styles.hero}><div><span className={styles.eyebrow}><SlidersHorizontal size={15}/> Sera</span><h1>{tr("Messaging control center")}</h1><p>{tr("Your conversations. Your rules. Create a messaging experience that works for you.")}</p></div><span className={styles.heroIcon}><ShieldCheck size={42} strokeWidth={1.5}/></span></header>
 <nav className={styles.nav} aria-label={tr("Messaging control center")}><a href="#notifications"><Bell size={16}/> {tr("Alerts")}</a><a href="#privacy"><ShieldCheck size={16}/> {tr("Privacy")}</a><a href="#previews"><Eye size={16}/> {tr("Previews")}</a><a href="#organization"><Archive size={16}/> {tr("Organization")}</a></nav>
 <div className={styles.grid}>
 <Panel id="notifications" title={tr("Notifications")} description={tr("Choose how and when message alerts arrive.")} icon={<Bell size={21}/>}>
 {error?<p role="alert" className={styles.error}>{tr("Preferences unavailable. Try again.")}</p>:<form action={saveMessagingPreferences} className={styles.form}>
 <Toggle name="enabled" checked={p.notifications_enabled} title={tr("Message notifications")} description={tr("Receive new message push alerts on your devices.")}/>
 <Toggle name="quiet" checked={p.quiet_enabled} title={tr("Quiet hours")} description={tr("Pause alerts without missing any messages.")}/>
 <div className={styles.schedule}><div className={styles.scheduleTitle}><Clock size={17}/> {tr("Quiet hours schedule")}</div><div className={styles.fields}><label>{tr("Start")}<input type="time" name="start" required defaultValue={clockValue(p.quiet_start)}/></label><label>{tr("End")}<input type="time" name="end" required defaultValue={clockValue(p.quiet_end)}/></label></div><label>{tr("Time zone")} (IANA)<input name="zone" required maxLength={80} defaultValue={p.quiet_timezone}/></label><small>{tr("Overnight hours are supported. Messages still arrive in your inbox.")}</small></div>
 <SaveControlButton pendingLabel={tr("Saving…")}>{tr("Save preferences")}</SaveControlButton></form>}
 </Panel>
 <Panel id="privacy" title={tr("Privacy & activity")} description={tr("Control what your connections can see.")} icon={<ShieldCheck size={21}/>}>
 {presenceError?<p role="alert" className={styles.error}>{tr("Activity settings unavailable.")}</p>:<form action={saveMessagingActivity} className={styles.form}><Toggle name="activity" checked={presence?.visibility!=="hidden"} title={tr("Activity & last seen")} description={tr("Share your activity with accepted connections.")}/><SaveControlButton pendingLabel={tr("Saving…")}>{tr("Save activity privacy")}</SaveControlButton></form>}
 {!error&&<><form action={saveTypingVisibility} className={styles.form}><Toggle name="typing" checked={data?.typing_visible??true} title={tr("Typing indicator")} description={tr("Let others see when you're typing. Turning off clears current signals.")}/><SaveControlButton pendingLabel={tr("Saving…")}>{tr("Save typing privacy")}</SaveControlButton></form><form action={saveReadReceiptVisibility} className={styles.form}><Toggle name="receipts" checked={data?.read_receipts_visible??true} title={tr("Read receipts")} description={tr("Let others see when you read their messages. Earlier shared receipts remain visible.")}/><SaveControlButton pendingLabel={tr("Saving…")}>{tr("Save read privacy")}</SaveControlButton></form></>}
 </Panel>
 <Panel id="previews" title={tr("Notifications & privacy")} description={tr("Decide what appears on your device.")} icon={<Eye size={21}/>}>
 <div className={styles.device}><strong>{tr("Browser notifications")}</strong><p id="sera-browser-notification-status" role="status">{tr("Allow notifications in this browser to receive message alerts.")}</p><div className={styles.actions}><button type="button" className={styles.secondary} data-sera-web-push="enable">{tr("Enable browser notifications")}</button><button type="button" className={styles.secondary} data-sera-web-push="disable">{tr("Turn off browser notifications")}</button></div></div>
 {!error&&<form action={saveNotificationPreviews} className={styles.form}><LiveNotificationPreview initialMode={notificationPreviewMode(data?.notification_preview_mode,data?.notification_previews)} locale={locale}/><div className={styles.note}><MessageCircle size={18}/><span>{tr("Android’s public lock-screen version remains generic. Clear existing alerts after changing privacy.")}</span></div><SaveControlButton pendingLabel={tr("Saving…")}>{tr("Save preview preference")}</SaveControlButton></form>}
 </Panel>
 <Panel id="organization" title={tr("Conversation organization")} description={tr("Keep important conversations within reach.")} icon={<Archive size={21}/>}>
 <div className={styles.organize}><p><CheckCheck size={18}/> {tr("Pin important conversations")}</p><p><Bell size={18}/> {tr("Mute distracting alerts")}</p><p><Archive size={18}/> {tr("Archive or mark unread for follow-up")}</p></div><p className={styles.note}>{tr("Manage each conversation from its settings menu. Your existing read receipts stay unchanged.")}</p><Link href="/network/messages" className={styles.open}>Open messages <ArrowUpRight size={18}/></Link>
 </Panel>
 </div></main>;
}
