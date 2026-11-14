"use client";
import {useState} from "react";
import {Bell,MessageCircle} from "lucide-react";
import {notificationText,type NotificationPreviewMode} from "@/lib/network/message-preview-model";
import styles from "./control-center.module.css";
import {controlText} from "@/lib/network/message-control-copy";
const copy={en:["Live notification preview","Example only","Hana","Your project looks great!","Now"],am:["የማሳወቂያ ቅድመ እይታ","ምሳሌ ብቻ","ሃና","ፕሮጀክትዎ በጣም ጥሩ ነው!","አሁን"],om:["Mul’ata beeksisaa","Fakkeenya qofa","Hanaa","Pirojektiin kee baay’ee gaarii dha!","Amma"]};
export function LiveNotificationPreview({initialMode,locale}:{initialMode:NotificationPreviewMode;locale:string}){
 const [mode,setMode]=useState(initialMode);
 const lang=locale.toLowerCase().split(/[-,;]/)[0];const [heading,example,sender,message,now]=copy[lang==="am"?"am":lang==="om"?"om":"en"];
 const tr=(value:string)=>notificationText(locale,value);
 return <><label className={styles.field}>{tr("Message previews")}<select name="previewMode" value={mode} onChange={e=>setMode(e.target.value as NotificationPreviewMode)}><option value="full">{tr("Show sender and message")} · {controlText(locale,"Default")}</option><option value="sender">{tr("Show sender only")}</option><option value="hidden">{tr("Hide sensitive content")}</option></select></label>
 <div className={styles.preview}><div className={styles.previewHeading}><span><Bell size={16}/>{heading}</span><small>{example}</small></div><div className={styles.previewCard} aria-live="polite"><span className={styles.previewAvatar} aria-hidden="true">{mode==="hidden"?<MessageCircle size={22}/>:sender.charAt(0)}</span><div className={styles.previewContent}><div><strong>{mode==="hidden"?"Sera":sender}</strong><small>{now}</small></div><p>{mode==="full"?message:mode==="sender"?tr("Sent you a message"):tr("You have a new private message.")}</p><small>Sera</small></div><span className={styles.activityDot} aria-hidden="true"/></div></div></>;
}
