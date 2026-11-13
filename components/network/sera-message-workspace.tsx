"use client";
import {useCallback,useEffect,useLayoutEffect,useMemo,useRef,useState} from "react";
import {professionalShare,locationShare,messageShareCards,SERA_ORIGIN,contactShare,messageContact} from "@/lib/network/share-card";
import Link from "next/link";
import {useRouter} from "next/navigation";
import {ArrowLeft,ArrowDown,Check,CheckCheck,Copy,FileText,Forward,MessageCircle,MoreHorizontal,Paperclip,Plus,Reply,Search,Send,Smile,Trash2,Volume2,VolumeX,X,Clock,AlertCircle,ImagePlus,Camera,Headphones,Phone,Video} from "lucide-react";
import {MemberAvatar} from "@/components/activity/avatar";
import {ProfessionalPresence} from "@/components/network/professional-presence";
import {InboxRefresh} from "@/components/network/inbox-refresh";
import {VoiceRecorder} from "@/components/network/sera-voice-recorder";
import type {CallHistoryEntry} from "@/lib/network/voice-calls";
import {SeraVoiceCall} from "@/components/network/sera-voice-call";
import {SeraVoicePlayer} from "@/components/network/sera-voice-player";
import {setConversationControl,conversationSafety} from "@/lib/network/message-control-actions";
import {openProfessionalConversation} from "@/lib/network/messaging";
import {prepareMessageUpload,sendMessageV2,sendPhotoAlbum,setMessageReaction,unsendMessage,forwardMessage,recordMessageReceipts,loadEarlierMessages,refreshOlderMessages} from "@/lib/network/messaging-excellence";
import {REACTIONS,FILE_LIMIT,MESSAGE_LIMIT,isChatMime,validAttachment,hasFileSignature,type AttachmentMeta,type MessageView,type ThreadPreview} from "@/lib/network/message-contract";
import {meetingProposalText,parseMeetingProposal} from "@/lib/network/meeting-proposal";
import {saveChatDraft,readChatDraft,saveOutbox,readOutbox,removeOutbox,type OutboxMessage,type OutboxPhoto} from "@/lib/network/message-outbox";
type Candidate={id:string;name:string;photo?:string|null};
type Pending=OutboxMessage&{status:"queued"|"sending"|"failed"|"sent";error?:string;progress?:number};
const time=(v:string|null)=>v?new Date(v).toLocaleTimeString("en-GB",{timeZone:"Africa/Addis_Ababa",hour:"2-digit",minute:"2-digit"}):"";
const day=(v:string)=>new Date(v).toLocaleDateString("en-GB",{timeZone:"Africa/Addis_Ababa",day:"numeric",month:"short",year:"numeric"});
const preview=(m:MessageView)=>m.unsent?"Message unsent":m.body.slice(0,140);
function useChatSound(){
  const [enabled,setEnabled]=useState(false);const context=useRef<AudioContext|null>(null);
  useEffect(()=>{const timer=setTimeout(()=>{try{setEnabled(localStorage.getItem("sera-chat-sound")==="on");}catch{}},0);return()=>{clearTimeout(timer);void context.current?.close().catch(()=>{});};},[]);
  const play=useCallback((incoming=false)=>{
    if(!enabled||document.visibilityState!=="visible")return;
    try{
      const ctx=context.current??(context.current=new AudioContext());
      if(ctx.state!=="running"){void ctx.resume().catch(()=>{});return;}
      const osc=ctx.createOscillator(),gain=ctx.createGain();osc.type="sine";osc.frequency.setValueAtTime(incoming?660:520,ctx.currentTime);osc.frequency.exponentialRampToValueAtTime(incoming?880:720,ctx.currentTime+.09);
      gain.gain.setValueAtTime(.035,ctx.currentTime);gain.gain.exponentialRampToValueAtTime(.001,ctx.currentTime+.16);osc.connect(gain);gain.connect(ctx.destination);osc.start();osc.stop(ctx.currentTime+.18);
    }catch{}
  },[enabled]);
  const toggle=()=>{const next=!enabled;setEnabled(next);try{localStorage.setItem("sera-chat-sound",next?"on":"off");if(next){context.current??=new AudioContext();void context.current.resume();}}catch{}};
  return {enabled,play,toggle};
}
function DeliverySync({userId,threads}:{userId:string;threads:ThreadPreview[]}){
  const router=useRouter(),done=useRef(new Set<string>());
  useEffect(()=>{
    let active=true;
    const sync=async()=>{
      if(document.visibilityState!=="visible"||!navigator.onLine)return;
      const todo=threads.filter(t=>t.lastMessageId&&t.lastSenderId!==userId&&!t.lastDeliveredAt&&!done.current.has(t.lastMessageId));
      for(const t of todo){const id=t.lastMessageId!;done.current.add(id);try{if(!await recordMessageReceipts(t.id,[id],false))done.current.delete(id);}catch{done.current.delete(id);}}
      if(active&&todo.length)router.refresh();
    };
    void sync();document.addEventListener("visibilitychange",sync);window.addEventListener("online",sync);
    return()=>{active=false;document.removeEventListener("visibilitychange",sync);window.removeEventListener("online",sync);};
  },[threads,userId,router]);
  return null;
}
export function MessageWorkspace({userId,threads,selected,messages,hasMore,search,loadError,candidates}:{userId:string;threads:ThreadPreview[];selected:ThreadPreview|null;messages:MessageView[];hasMore:boolean;search:string;loadError:string;candidates:Candidate[]}){
  const workspace=useRef<HTMLDivElement>(null);
  const [sheetFraction,setSheetFraction]=useState(.9);
  const [sheetDragging,setSheetDragging]=useState(false);
  const sheetDrag=useRef<{id:number;y:number;start:number;current:number;moved:boolean}|null>(null);
  const sheetPosition=useRef(.9);
  const resizeSheet=(value:number)=>{sheetPosition.current=value;setSheetFraction(value);workspace.current?.style.setProperty("--sera-chat-sheet-fraction",String(value));};
  const finishSheetDrag=(id:number,cancelled=false)=>{const drag=sheetDrag.current;if(!drag||drag.id!==id)return;sheetDrag.current=null;setSheetDragging(false);resizeSheet(cancelled?drag.start:drag.moved?[.6,.9,1].reduce((best,next)=>Math.abs(next-drag.current)<Math.abs(best-drag.current)?next:best,.9):drag.start);};
  useEffect(()=>{
    const el=workspace.current;if(!el)return;
    const sync=()=>{
      const vv=window.visualViewport;
      if(window.matchMedia("(max-width: 700px)").matches&&vv){
        el.style.setProperty("--sera-chat-vv-height",vv.height+"px");
        el.style.setProperty("--sera-chat-vv-top",vv.offsetTop+"px");
        const active=document.activeElement;
        const editing=active instanceof HTMLElement&&el.contains(active)&&active.matches("textarea,input,[contenteditable=true]");
        el.dataset.keyboard=String(editing&&vv.scale<1.1&&window.innerHeight-vv.height>100);
      }else{
        el.style.removeProperty("--sera-chat-vv-height");
        el.style.removeProperty("--sera-chat-vv-top");
        delete el.dataset.keyboard;
      }
    };
    sync();
    window.visualViewport?.addEventListener("resize",sync);window.visualViewport?.addEventListener("scroll",sync);
    window.addEventListener("resize",sync);document.addEventListener("focusin",sync);document.addEventListener("focusout",sync);
    return()=>{window.visualViewport?.removeEventListener("resize",sync);window.visualViewport?.removeEventListener("scroll",sync);window.removeEventListener("resize",sync);document.removeEventListener("focusin",sync);document.removeEventListener("focusout",sync);};
  },[]);
  const [query,setQuery]=useState("");
  const [filter,setFilter]=useState<"all"|"unread"|"archived">("all");
  const [revealed,setRevealed]=useState<string|null>(null),[swipeError,setSwipeError]=useState(""),[quickBusy,setQuickBusy]=useState(false);
  const inboxSwipe=useRef<{id:string;x:number;y:number}|null>(null);const router=useRouter();
  async function quick(t:ThreadPreview,action:"pin"|"archive"|"unread"|"mute",value:boolean,minutes=0){if(quickBusy)return;setQuickBusy(true);setSwipeError("");try{const result=await setConversationControl(t.id,action,value,minutes);if(result.ok){setRevealed(null);router.refresh();}else setSwipeError(result.error);}catch{setSwipeError("Unable to save. Try again.");}finally{setQuickBusy(false);}}
  const filtered=threads.filter(t=>(filter==="archived"?t.archived:!t.archived&&(filter!=="unread"||t.unread>0||t.markedUnread))&&(t.name.toLowerCase().includes(query.trim().toLowerCase())||(t.lastBody??"").toLowerCase().includes(query.trim().toLowerCase()))).sort((a,b)=>Number(!!b.pinned)-Number(!!a.pinned));

  return <div ref={workspace} className={"sera-chat-workspace sera-chat-wrapper-90"+(selected?" has-thread":"")} data-dragging={sheetDragging}>
    <button type="button" className="sera-chat-resize-handle" aria-label="Resize messages" aria-expanded={sheetFraction>=.95} onPointerDown={e=>{if(!e.isPrimary||e.button!==0)return;sheetDrag.current={id:e.pointerId,y:e.clientY,start:sheetPosition.current,current:sheetPosition.current,moved:false};e.currentTarget.setPointerCapture(e.pointerId);}} onPointerMove={e=>{const drag=sheetDrag.current;if(!drag||drag.id!==e.pointerId)return;const delta=e.clientY-drag.y;if(!drag.moved&&Math.abs(delta)<5)return;drag.moved=true;setSheetDragging(true);drag.current=Math.max(.6,Math.min(1,drag.start-delta/(window.visualViewport?.height||window.innerHeight)));resizeSheet(drag.current);}} onPointerUp={e=>{const drag=sheetDrag.current;const moved=drag?.moved;finishSheetDrag(e.pointerId);if(!moved&&drag)resizeSheet(sheetPosition.current>=.95?.9:1);}} onPointerCancel={e=>finishSheetDrag(e.pointerId,true)} onLostPointerCapture={e=>finishSheetDrag(e.pointerId,true)} onClick={e=>{if(e.detail===0)resizeSheet(sheetPosition.current>=.95?.9:1);}} onKeyDown={e=>{if(e.key==="ArrowUp"||e.key==="ArrowDown"){e.preventDefault();const anchors=[.6,.9,1];const next=e.key==="ArrowUp"?anchors.find(v=>v>sheetPosition.current+.005):[...anchors].reverse().find(v=>v<sheetPosition.current-.005);if(next!==undefined)resizeSheet(next);}}}><span/></button>
    <InboxRefresh/><DeliverySync userId={userId} threads={threads}/>
    <aside className="sera-chat-sidebar" aria-label="Conversations">
      <div className="sera-chat-inbox-head"><div className="sera-chat-inbox-title"><Link href="/feed" className="sera-chat-icon sera-chat-inbox-back" aria-label="Back to feed"><ArrowLeft size={23}/></Link><h1>Messages</h1></div><details className="sera-chat-new"><summary className="sera-chat-icon" aria-label="New conversation"><Plus size={22}/></summary><div className="sera-chat-new-panel"><h3>New conversation</h3>{candidates.length?candidates.map(c=><form key={c.id} action={openProfessionalConversation}><input type="hidden" name="recipient" value={c.id}/><MemberAvatar name={c.name} photo={c.photo}/><span>{c.name}</span><button type="submit" className="sera-chat-icon" aria-label={"Message "+c.name}><MessageCircle size={19}/></button></form>):<p>Connect with a professional to start a conversation.</p>}<Link href="/connections">Discover connections</Link></div></details></div>
      <label className="sera-chat-list-search"><Search size={19}/><input value={query} onChange={e=>setQuery(e.target.value)} placeholder="Search conversations" aria-label="Search conversations"/></label>
      <div className="sera-chat-filters" role="group" aria-label="Filter conversations">{(["all","unread","archived"] as const).map(f=><button key={f} type="button" aria-pressed={filter===f} onClick={()=>setFilter(f)}>{f==="all"?"All":f==="unread"?"Unread":"Archived"}</button>)}<Link href="/settings/messaging">Settings</Link></div>
      {swipeError&&<p role="alert" className="sera-chat-offline">{swipeError}</p>}
      <div className="sera-chat-list">{filtered.map(t=><div key={t.id} className="sera-chat-inbox-group"><Link href={"/network/messages?thread="+t.id} className={"sera-chat-list-item"+(selected?.id===t.id?" is-selected":"")+(t.unread>0||t.markedUnread?" is-unread":"")} aria-current={selected?.id===t.id?"page":undefined} onTouchStart={e=>{if(e.touches.length===1){const x=e.touches[0];inboxSwipe.current={id:t.id,x:x.clientX,y:x.clientY};}}} onTouchCancel={()=>{inboxSwipe.current=null;}} onTouchEnd={e=>{const start=inboxSwipe.current;inboxSwipe.current=null;const end=e.changedTouches[0];if(start?.id===t.id&&end&&Math.abs(end.clientX-start.x)>52&&Math.abs(end.clientX-start.x)>1.6*Math.abs(end.clientY-start.y)){setRevealed(v=>v===t.id?null:t.id);}}} onClick={e=>{if(revealed===t.id){e.preventDefault();setRevealed(null);}}}>
        <MemberAvatar name={t.name} photo={t.photo} size={48}/><span className="sera-chat-list-text"><strong>{t.pinned?"⌖ ":""}{t.name}</strong><small>{t.lastSenderId===userId?"You: ":""}{t.lastBody||"Start a conversation"}</small></span><span className="sera-chat-list-meta"><time>{time(t.lastAt)}</time>{(t.unread>0||t.markedUnread)&&<span className="sera-chat-unread">{t.unread>99?"99+":t.unread||"•"}</span>}</span>
      </Link><button className="sera-inbox-quick-toggle" type="button" aria-label={"Quick actions for "+t.name} aria-expanded={revealed===t.id} onClick={()=>setRevealed(v=>v===t.id?null:t.id)}><MoreHorizontal size={16}/></button>{revealed===t.id&&<div className="sera-inbox-quick-actions"><button disabled={quickBusy} onClick={()=>void quick(t,"pin",!t.pinned)}>{t.pinned?"Unpin":"Pin"}</button><button disabled={quickBusy} onClick={()=>void quick(t,"archive",!t.archived)}>{t.archived?"Restore":"Archive"}</button><button disabled={quickBusy} onClick={()=>void quick(t,"unread",!t.markedUnread)}>{t.markedUnread?"Clear":"Unread"}</button><button disabled={quickBusy} onClick={()=>void quick(t,"mute",true,t.mutedUntil?0:60)}>{t.mutedUntil?"Unmute":"Mute 1h"}</button></div>}</div>)}{!filtered.length&&<div className="sera-chat-empty"><MessageCircle size={30}/><strong>{query?"No matching conversations":"Your inbox is ready"}</strong><p>{query?"Try another name or message.":"Start a conversation with a connection."}</p></div>}</div>
    </aside>
    {selected?<ChatThread key={selected.id+":"+search} userId={userId} thread={selected} threads={threads} messages={messages} hasMore={hasMore} search={search} loadError={loadError}/>:<section className="sera-chat-welcome"><div><MessageCircle size={40}/><h2>Conversations that move work forward.</h2><p>Select a conversation or start a new one with your professional network.</p></div></section>}
  </div>;
}
function ChatThread({userId,thread,threads,messages,hasMore,search,loadError}:{userId:string;thread:ThreadPreview;threads:ThreadPreview[];messages:MessageView[];hasMore:boolean;search:string;loadError:string}){
  const router=useRouter(),sound=useChatSound();
  const [meetingReply,setMeetingReply]=useState<MessageView|null>(null);
  const [meetingOpen,setMeetingOpen]=useState(false),[meetingTitle,setMeetingTitle]=useState(""),[meetingAt,setMeetingAt]=useState(""),[meetingMinutes,setMeetingMinutes]=useState(30);
  const draftEdited=useRef(false),nearLatest=useRef(true);
  const [jumpVisible,setJumpVisible]=useState(false);
  const [shareMode,setShareMode]=useState<"professional"|"location"|"contact"|null>(null);const [shareValue,setShareValue]=useState(""),[shareLng,setShareLng]=useState("");
  const [album,setAlbum]=useState<OutboxPhoto[]>([]);const [callEvents,setCallEvents]=useState<CallHistoryEntry[]>([]);const [studio,setStudio]=useState(false),[checkingAttachment,setCheckingAttachment]=useState(false);const studioDialog=useRef<HTMLDialogElement>(null);const viewerDialog=useRef<HTMLDialogElement>(null);const [viewer,setViewer]=useState<{id:string;url:string;name:string}|null>(null);const [zoom,setZoom]=useState(false);const activeUploads=useRef(new Map<string,XMLHttpRequest>());const [cancellable,setCancellable]=useState<string[]>([]);const [draft,setDraft]=useState(""),[attachment,setAttachment]=useState<{file:File;meta:AttachmentMeta}|null>(null),[reply,setReply]=useState<MessageView|null>(null),[pending,setPending]=useState<Pending[]>([]);
  const [online,setOnline]=useState(true),[error,setError]=useState(loadError),[menu,setMenu]=useState<string|null>(null),[reactions,setReactions]=useState<string|null>(null),[confirm,setConfirm]=useState<MessageView|null>(null),[forward,setForward]=useState<MessageView|null>(null),[forwardTo,setForwardTo]=useState(""),[actionBusy,setActionBusy]=useState(false),[showSearch,setShowSearch]=useState(Boolean(search)),[settings,setSettings]=useState(false);
  const [older,setOlder]=useState<MessageView[]>([]),[more,setMore]=useState(hasMore),[loadingOlder,setLoadingOlder]=useState(false),[typing,setTyping]=useState(false),[fileUrl,setFileUrl]=useState<string|null>(null);
  const [focusedMessage,setFocusedMessage]=useState<string|null>(null);
  useEffect(()=>{if(!focusedMessage)return;const timer=setTimeout(()=>setFocusedMessage(null),1800);return()=>clearTimeout(timer);},[focusedMessage]);
  function focusMessage(id:string){
    const target=document.getElementById("message-"+id);
    if(!target)return;
    setFocusedMessage(id);
    target.scrollIntoView({block:"center",behavior:window.matchMedia("(prefers-reduced-motion: reduce)").matches?"instant":"smooth"});
  }
  const swipeStart=useRef<{id:string;x:number;y:number}|null>(null);
  const panel=useRef<HTMLElement|null>(null),scroll=useRef<HTMLDivElement>(null),input=useRef<HTMLTextAreaElement>(null),fileInput=useRef<HTMLInputElement>(null),working=useRef(new Set<string>()),mounted=useRef(true),typingTimer=useRef<ReturnType<typeof setTimeout>|null>(null),lastTyping=useRef(0),forwardId=useRef(""),receiptSeen=useRef(new Set<string>()),deliveredSeen=useRef(new Set<string>()),lastNew=useRef<string|null>(null),olderRef=useRef(older),scrollAnchor=useRef<number|null>(null),initialScroll=useRef(false);
  olderRef.current=older;
  const all=useMemo(()=>{const map=new Map<string,MessageView>();for(const m of older)map.set(m.id,m);for(const m of messages)map.set(m.id,m);return [...map.values()].sort((a,b)=>a.createdAt.localeCompare(b.createdAt)||a.id.localeCompare(b.id));},[older,messages]);
  const sentIds=useMemo(()=>new Set(all.flatMap(m=>m.clientId?[m.clientId]:[])),[all]);
  const local=pending.filter(p=>!sentIds.has(p.clientId));
  const announce=useCallback((active:boolean)=>{void fetch("/api/network/messages/typing",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({conversation:thread.id,typing:active}),keepalive:!active}).catch(()=>{});},[thread.id]);
  useEffect(()=>{mounted.current=true;setOnline(navigator.onLine);const up=()=>{setOnline(true);setPending(ps=>ps.map(p=>p.status==="failed"?{...p,status:"queued"}:p));};const down=()=>setOnline(false);
    window.addEventListener("online",up);window.addEventListener("offline",down);
    void readChatDraft(userId,thread.id).then(text=>{if(mounted.current&&!draftEdited.current)setDraft(text);}).catch(()=>{});
    void readOutbox(userId,thread.id).then(items=>{if(mounted.current)setPending(ps=>{const ids=new Set(ps.map(p=>p.clientId));return [...ps,...items.filter(p=>!ids.has(p.clientId)).map(p=>({...p,status:"queued" as const}))];});}).catch(()=>{});
    return()=>{mounted.current=false;window.removeEventListener("online",up);window.removeEventListener("offline",down);if(typingTimer.current)clearTimeout(typingTimer.current);announce(false);};
  },[userId,thread.id,announce]);
  useEffect(()=>{const el=panel.current;if(!el)return;const sync=()=>{if(window.matchMedia("(max-width: 700px)").matches&&window.visualViewport){el.style.setProperty("--sera-chat-vv-height",window.visualViewport.height+"px");el.style.setProperty("--sera-chat-vv-top",window.visualViewport.offsetTop+"px");}else{el.style.removeProperty("--sera-chat-vv-height");el.style.removeProperty("--sera-chat-vv-top");}};sync();window.visualViewport?.addEventListener("resize",sync);window.visualViewport?.addEventListener("scroll",sync);window.addEventListener("resize",sync);return()=>{window.visualViewport?.removeEventListener("resize",sync);window.visualViewport?.removeEventListener("scroll",sync);window.removeEventListener("resize",sync);};},[]);
  useEffect(()=>{if(!attachment){setFileUrl(null);return;}const url=URL.createObjectURL(attachment.file);setFileUrl(url);return()=>URL.revokeObjectURL(url);},[attachment]);
  useEffect(()=>{let active=true;const poll=async()=>{if(document.visibilityState!=="visible")return;try{const r=await fetch("/api/network/messages/typing?conversation="+encodeURIComponent(thread.id),{cache:"no-store"});if(active&&r.ok)setTyping(Boolean((await r.json()).typing));}catch{if(active)setTyping(false);}};void poll();const id=setInterval(poll,2500);return()=>{active=false;clearInterval(id);};},[thread.id]);
  useEffect(()=>{if(!olderRef.current.length)return;let active=true;void refreshOlderMessages(thread.id,olderRef.current.map(m=>m.id).slice(0,600)).then(result=>{if(active&&result)setOlder(result);}).catch(()=>{});return()=>{active=false;};},[messages,thread.id]);
  useEffect(()=>{for(const p of pending)if(sentIds.has(p.clientId))void removeOutbox(userId,thread.id,p.clientId).catch(()=>{});setPending(ps=>ps.some(p=>sentIds.has(p.clientId))?ps.filter(p=>!sentIds.has(p.clientId)):ps);},[sentIds,userId,thread.id,pending]);
  const attempt=useCallback(async(p:Pending)=>{
    if(working.current.has(p.clientId)||!navigator.onLine)return;
    working.current.add(p.clientId);setPending(ps=>ps.map(x=>x.clientId===p.clientId?{...x,status:"sending",error:undefined,progress:p.file||p.album?.length?0:undefined}:x));
    try{
      let ack:string|undefined;
      async function uploadOne(id:string,file:File,meta:AttachmentMeta,offset:number,total:number){
        const prep=await prepareMessageUpload(thread.id,id,meta);
        if(!prep.ok)throw new Error(prep.error);
        if(prep.url&&!prep.alreadySent){
          const upload=await new Promise<{ok:boolean}>((resolve,reject)=>{
            const xhr=new XMLHttpRequest();activeUploads.current.set(p.clientId,xhr);setCancellable(ids=>[...ids,p.clientId]);
            const finish=()=>{activeUploads.current.delete(p.clientId);if(mounted.current)setCancellable(ids=>ids.filter(id=>id!==p.clientId));};
            xhr.open("PUT",prep.url!);xhr.setRequestHeader("Content-Type",meta.mime);xhr.setRequestHeader("x-upsert","false");xhr.timeout=90000;
            xhr.upload.onprogress=e=>{if(e.lengthComputable&&mounted.current)setPending(ps=>ps.map(x=>x.clientId===p.clientId?{...x,progress:Math.min(99,Math.round((offset+e.loaded)*100/total))}:x));};
            xhr.onload=()=>{finish();resolve({ok:xhr.status>=200&&xhr.status<300});};xhr.onerror=xhr.ontimeout=()=>{finish();reject(new Error("Upload interrupted. Retry."));};xhr.onabort=()=>{finish();reject(new Error("Upload cancelled. Retry or discard."));};xhr.send(file);
          });
          if(!upload.ok){const retry=await prepareMessageUpload(thread.id,id,meta);if(!retry.ok||retry.url)throw new Error("Private upload failed. Retry when connected.");return retry.alreadySent;}
        }
        return prep.alreadySent;
      }
      if(p.album?.length){
        const total=p.album.reduce((n,f)=>n+f.file.size,0);let offset=0;
        for(const photo of p.album){const prior=await uploadOne(photo.id,photo.file,photo.meta,offset,total);if(prior)throw new Error("Photo identifier already used.");offset+=photo.file.size;}
        if(mounted.current)setPending(ps=>ps.map(x=>x.clientId===p.clientId?{...x,progress:100}:x));
        const r=await sendPhotoAlbum({conversation:thread.id,clientId:p.clientId,body:p.body,replyTo:p.replyTo,photos:p.album.map(({id,meta})=>({id,meta}))});if(!r.ok)throw new Error(r.error);ack=r.id;
      }else{
        if(p.file&&p.attachment)ack=await uploadOne(p.clientId,p.file,p.attachment,0,p.file.size);
        if(p.file&&mounted.current)setPending(ps=>ps.map(x=>x.clientId===p.clientId?{...x,progress:100}:x));
        if(!ack){const r=await sendMessageV2({conversation:thread.id,clientId:p.clientId,body:p.body,replyTo:p.replyTo,attachment:p.attachment});if(!r.ok)throw new Error(r.error);ack=r.id;}
      }
      await removeOutbox(userId,thread.id,p.clientId).catch(()=>{});
      if(mounted.current){setPending(ps=>ps.map(x=>x.clientId===p.clientId?{...x,status:"sent"}:x));sound.play();router.refresh();}
    }catch(e){if(mounted.current)setPending(ps=>ps.map(x=>x.clientId===p.clientId?{...x,status:navigator.onLine?"failed":"queued",error:e instanceof Error?e.message:"Connection lost. Retry."}:x));}
    finally{working.current.delete(p.clientId);}
  },[thread.id,userId,router,sound.play]);
  useEffect(()=>{if(online)for(const p of pending)if(p.status==="queued")void attempt(p);},[online,pending,attempt]);
  useEffect(()=>{const latest=messages.at(-1);if(!latest)return;if(lastNew.current&&latest.id!==lastNew.current&&latest.senderId!==userId)sound.play(true);lastNew.current=latest.id;},[messages,userId,sound.play]);
  useLayoutEffect(()=>{const el=scroll.current;if(!el)return;if(scrollAnchor.current!==null){el.scrollTop=el.scrollHeight-scrollAnchor.current;scrollAnchor.current=null;return;}if(!initialScroll.current){el.scrollTop=el.scrollHeight;initialScroll.current=true;}else if(nearLatest.current||local.length)el.scrollTop=el.scrollHeight;},[all,local.length]);
  useEffect(()=>{
    const root=scroll.current;if(!root)return;let timer:ReturnType<typeof setTimeout>|null=null;const visible=new Set<string>();let disposed=false;
    const flush=async()=>{if(disposed||document.visibilityState!=="visible"||!document.hasFocus())return;const ids=[...visible].filter(id=>!receiptSeen.current.has(id)).slice(0,100);if(!ids.length)return;ids.forEach(id=>receiptSeen.current.add(id));try{if(await recordMessageReceipts(thread.id,ids,true)){if(!disposed)router.refresh();}else ids.forEach(id=>receiptSeen.current.delete(id));}catch{ids.forEach(id=>receiptSeen.current.delete(id));}};
    const schedule=()=>{if(timer)clearTimeout(timer);timer=setTimeout(()=>void flush(),650);};
    const observer=new IntersectionObserver(entries=>{for(const entry of entries){const id=(entry.target as HTMLElement).dataset.unreadId;if(id){if(entry.isIntersecting)visible.add(id);else visible.delete(id);}}schedule();},{root,threshold:.55});
    root.querySelectorAll("[data-unread-id]").forEach(el=>observer.observe(el));
    window.addEventListener("focus",schedule);document.addEventListener("visibilitychange",schedule);
    return()=>{disposed=true;observer.disconnect();if(timer)clearTimeout(timer);window.removeEventListener("focus",schedule);document.removeEventListener("visibilitychange",schedule);};
  },[all,thread.id,router]);
  useEffect(()=>{if(document.visibilityState!=="visible"||!online)return;const ids=all.filter(m=>m.senderId!==userId&&!m.deliveredAt&&!deliveredSeen.current.has(m.id)).map(m=>m.id);if(!ids.length)return;ids.forEach(id=>deliveredSeen.current.add(id));void(async()=>{let ok=true;for(let i=0;i<ids.length;i+=100){try{if(!await recordMessageReceipts(thread.id,ids.slice(i,i+100),false))ok=false;}catch{ok=false;}}if(ok)router.refresh();else ids.forEach(id=>deliveredSeen.current.delete(id));})();},[all,userId,thread.id,online,router]);
  useEffect(()=>{if(!confirm&&!forward&&!menu&&!reactions&&!settings)return;const close=(e:KeyboardEvent)=>{if(e.key==="Escape"){setConfirm(null);setForward(null);setMenu(null);setReactions(null);setSettings(false);}};window.addEventListener("keydown",close);return()=>window.removeEventListener("keydown",close);},[confirm,forward,menu,reactions,settings]);
  function changeDraft(value:string){draftEdited.current=true;setDraft(value.slice(0,MESSAGE_LIMIT));void saveChatDraft(userId,thread.id,value).catch(()=>{});if(Date.now()-lastTyping.current>3000){lastTyping.current=Date.now();announce(Boolean(value.trim()));}if(typingTimer.current)clearTimeout(typingTimer.current);typingTimer.current=setTimeout(()=>announce(false),3500);}
  useEffect(()=>{const dialog=viewerDialog.current;if(viewer&&dialog&&!dialog.open)dialog.showModal();if(!viewer&&dialog?.open)dialog.close();},[viewer]);
  useEffect(()=>{if(viewer&&!messages.some(m=>!m.unsent&&m.files.some(f=>f.id===viewer.id))&&!older.some(m=>!m.unsent&&m.files.some(f=>f.id===viewer.id)))setViewer(null);},[viewer,messages,older]);
  useEffect(()=>{const dialog=studioDialog.current;if(studio&&dialog&&!dialog.open)dialog.showModal();if(!studio&&dialog?.open)dialog.close();},[studio]);
  function openShare(mode:"professional"|"location"|"contact"){studioDialog.current?.close();setStudio(false);setShareValue("");setShareLng("");setShareMode(mode);}
  async function pickContact(){
    const api=(navigator as Navigator&{contacts?:{select:(fields:string[],options:{multiple:boolean})=>Promise<{name?:string[];tel?:string[]}[]>}}).contacts;
    if(!api){setError("Your browser does not support the contact picker. Enter the details manually.");return;}
    try{const contacts=await api.select(["name","tel"],{multiple:false});const chosen=contacts[0];if(chosen){setShareValue(chosen.name?.[0]??"");setShareLng(chosen.tel?.[0]??"");}}catch{/* User cancelled or picker unavailable. */}
  }
  function appendShare(url:string){const next=draft.trimEnd()+(draft.trim()?"\n":"")+url;if(next.length>MESSAGE_LIMIT){setError("Message is too long to add this link.");return;}changeDraft(next);setShareMode(null);setError("");}
  function pick(kind:"photo"|"camera"|"document"|"audio"|"video"){const el=fileInput.current;if(!el)return;el.accept=kind==="video"?"video/mp4":kind==="document"?"application/pdf":kind==="audio"?"audio/mp4,audio/webm,audio/ogg,audio/mpeg":"image/jpeg,image/png,image/webp";el.multiple=kind==="photo";if(kind==="camera")el.setAttribute("capture","environment");else el.removeAttribute("capture");studioDialog.current?.close();setStudio(false);el.click();}
  async function chooseProfileCv(){
    studioDialog.current?.close();setStudio(false);setCheckingAttachment(true);setError("");
    try{const r=await fetch("/api/network/messages/profile-cv",{cache:"no-store"});if(r.status===404){setError("No profile CV yet. Choose Document to upload a PDF, or add a CV in your account.");return;}if(!r.ok)throw new Error("Your profile CV is unavailable or exceeds 5 MB.");const blob=await r.blob();if(blob.size>FILE_LIMIT||!hasFileSignature(new Uint8Array(await blob.slice(0,5).arrayBuffer()),"application/pdf"))throw new Error("Invalid profile CV.");const name=decodeURIComponent(r.headers.get("X-Sera-Cv-Name")||"CV.pdf");if(mounted.current){setAlbum([]);setAttachment({file:new File([blob],name,{type:"application/pdf"}),meta:{name,mime:"application/pdf",size:blob.size}});}}catch(e){if(mounted.current)setError(e instanceof Error?e.message:"Could not load profile CV.");}finally{if(mounted.current)setCheckingAttachment(false);}
  }
  async function choose(files:File[]){if(!files.length)return;setCheckingAttachment(true);
    try{
      if(files.length>4)throw new Error("Choose up to four photos.");
      const chosen:OutboxPhoto[]=[];
      for(const file of files){
        const mime=file.type==="audio/x-m4a"?"audio/mp4":file.type;
        if(!isChatMime(mime)||file.size>FILE_LIMIT||!file.size||(files.length>1&&!mime.startsWith("image/")))throw new Error("Choose supported photos, MP4 videos, PDFs or audio files of 5 MB or less.");
        const normalized=file.type===mime?file:new File([file],file.name,{type:mime});
        const meta={name:file.name.slice(0,180)||"Attachment",mime,size:file.size};
        if(!validAttachment(meta)||!hasFileSignature(new Uint8Array(await normalized.slice(0,32).arrayBuffer()),mime))throw new Error("A file does not match its declared format.");
        chosen.push({id:crypto.randomUUID(),file:normalized,meta});
      }
      if(!mounted.current)return;
      if(chosen.length===1){setAttachment(chosen[0]);setAlbum([]);}else{setAlbum(chosen);setAttachment(null);}setError("");
    }catch(e){setError(e instanceof Error?e.message:"Could not read attachment.");}finally{setCheckingAttachment(false);}
  }
  async function send(){if(checkingAttachment||(!draft.trim()&&!attachment&&!album.length))return;const p:Pending={clientId:crypto.randomUUID(),conversation:thread.id,body:draft.trim(),replyTo:reply?.id??null,replyLabel:reply?preview(reply):null,createdAt:new Date().toISOString(),attachment:attachment?.meta??null,file:attachment?.file??null,album:album.length?album:undefined,status:"queued"};
    draftEdited.current=true;setDraft("");void saveChatDraft(userId,thread.id,"").catch(()=>{});setAttachment(null);setAlbum([]);setReply(null);setError("");announce(false);
    try{await saveOutbox(userId,p);}catch{if(!navigator.onLine)setError("Offline storage is unavailable. Keep this chat open to retry your message.");}
    if(mounted.current)setPending(ps=>[...ps,p]);
  }
  async function history(){if(loadingOlder||!all.length)return;setLoadingOlder(true);setError("");const first=all[0];try{const result=await loadEarlierMessages(thread.id,search,{id:first.id,createdAt:first.createdAt});if(result.error)throw new Error(result.error);const el=scroll.current;if(el)scrollAnchor.current=el.scrollHeight-el.scrollTop;setOlder(ps=>[...result.messages,...ps]);setMore(result.hasMore);}catch(e){setError(e instanceof Error?e.message:"Could not load older messages.");}finally{setLoadingOlder(false);}}
  async function react(m:MessageView,emoji:string){if(actionBusy)return;setActionBusy(true);setReactions(null);const own=m.reactions.find(r=>r.memberId===userId);try{const r=await setMessageReaction(m.id,own?.emoji===emoji?null:emoji);if(!r.ok)throw new Error(r.error);router.refresh();}catch(e){setError(e instanceof Error?e.message:"Reaction failed.");}finally{setActionBusy(false);}}
  async function remove(){if(!confirm||actionBusy)return;setActionBusy(true);try{const r=await unsendMessage(confirm.id);if(!r.ok)throw new Error(r.error);setConfirm(null);setError(r.warning||"");router.refresh();}catch(e){setError(e instanceof Error?e.message:"Could not unsend.");}finally{setActionBusy(false);}}
  async function doForward(){if(!forward||!forwardTo||actionBusy)return;setActionBusy(true);try{const r=await forwardMessage(forward.id,forwardTo,forwardId.current);if(!r.ok)throw new Error(r.error);setForward(null);setError("");router.refresh();}catch(e){setError(e instanceof Error?e.message:"Forward failed.");}finally{setActionBusy(false);}}
  useEffect(()=>{
    if(!confirm&&!forward)return;
    const root=panel.current?.querySelector<HTMLElement>(".sera-chat-dialog");if(!root)return;
    const previous=document.activeElement as HTMLElement|null;
    const focusable=()=>Array.from(root.querySelectorAll<HTMLElement>("button:not([disabled]),select:not([disabled]),input:not([disabled])"));
    focusable()[0]?.focus();
    const key=(e:KeyboardEvent)=>{if(e.key==="Escape"&&!actionBusy){setConfirm(null);setForward(null);return;}if(e.key!=="Tab")return;const items=focusable(),first=items[0],last=items.at(-1);if(e.shiftKey&&document.activeElement===first){e.preventDefault();last?.focus();}else if(!e.shiftKey&&document.activeElement===last){e.preventDefault();first?.focus();}};
    root.addEventListener("keydown",key);return()=>{root.removeEventListener("keydown",key);previous?.focus();};
  },[confirm,forward,actionBusy]);
  useEffect(()=>{if(!menu&&!reactions&&!settings)return;const dismiss=(e:PointerEvent)=>{const target=e.target as HTMLElement;if(!target.closest(".sera-chat-message-actions,.sera-chat-settings")){setMenu(null);setReactions(null);setSettings(false);}};const key=(e:KeyboardEvent)=>{if(e.key==="Escape"){setMenu(null);setReactions(null);setSettings(false);}};document.addEventListener("pointerdown",dismiss);document.addEventListener("keydown",key);return()=>{document.removeEventListener("pointerdown",dismiss);document.removeEventListener("keydown",key);};},[menu,reactions,settings]);
  return <section ref={panel} className="sera-chat-thread" aria-label={"Conversation with "+thread.name}>
    <header className="sera-chat-header"><Link href="/network/messages" className="sera-chat-icon sera-chat-back" aria-label="Back to inbox"><ArrowLeft size={23}/></Link><Link href={"/members/"+thread.peerId} className="sera-chat-identity"><MemberAvatar name={thread.name} photo={thread.photo} size={44}/><span><strong>{thread.name}</strong><small>{typing?<span className="sera-chat-typing">Typing…</span>:<ProfessionalPresence member={thread.peerId} compact/>}</small></span></Link><SeraVoiceCall key={thread.id} conversation={thread.id} userId={userId} name={thread.name} photo={thread.photo} onHistory={setCallEvents}/><button className="sera-chat-icon" type="button" aria-label="Search conversation" onClick={()=>setShowSearch(v=>!v)}><Search size={21}/></button><div className="sera-chat-settings"><button className="sera-chat-icon" type="button" aria-label="Conversation settings" aria-expanded={settings} onClick={()=>setSettings(v=>!v)}><MoreHorizontal size={23}/></button>{settings&&<div className="sera-chat-menu"><button type="button" onClick={sound.toggle}>{sound.enabled?<Volume2 size={18}/>:<VolumeX size={18}/>}Message sounds {sound.enabled?"on":"off"}</button><div className="sera-chat-presence-settings"><ProfessionalPresence member={thread.peerId}/></div><ConversationControls thread={thread}/></div>}</div></header>
    {showSearch&&<form className="sera-chat-search" method="get" action="/network/messages"><input type="hidden" name="thread" value={thread.id}/><Search size={18}/><input name="q" type="search" defaultValue={search} placeholder="Search messages" maxLength={120} aria-label="Search messages"/><button type="submit" className="sera-chat-icon" aria-label="Search"><Search size={18}/></button><Link href={"/network/messages?thread="+thread.id} className="sera-chat-icon" aria-label="Clear search" onClick={()=>setShowSearch(false)}><X size={18}/></Link></form>}
    {!online&&<div className="sera-chat-offline" role="status"><Clock size={15}/> Offline · messages will send when connected</div>}
    <div className="sera-chat-scroll" ref={scroll} aria-label="Message history" onScroll={e=>{const el=e.currentTarget;nearLatest.current=el.scrollHeight-el.scrollTop-el.clientHeight<180;setJumpVisible(!nearLatest.current);}}>
      {more&&all.length>0&&<button type="button" className="sera-chat-history" onClick={()=>void history()} disabled={loadingOlder}>{loadingOlder?"Loading…":"Load earlier messages"}</button>}
      {!all.length&&!local.length&&!callEvents.length&&<div className="sera-chat-empty"><MessageCircle size={34}/><strong>{search?"No matching messages":"Start the conversation"}</strong><p>{search?"Try another search.":"Say hello, share an idea or send a project file."}</p></div>}
      {[...all.map(m=>({kind:"message" as const,at:m.createdAt,m})),...callEvents.filter(e=>!search||"voice call".includes(search.toLowerCase())).map(e=>({kind:"call" as const,at:e.created_at,e}))].sort((a,b)=>Date.parse(a.at)-Date.parse(b.at)).map((item,i,timeline)=>{
        const dayChanged=i===0||day(timeline[i-1].at)!==day(item.at);
        if(item.kind==="call"){const e=item.e;const title=e.state==="missed"?"Missed voice call":e.state==="declined"?"Declined voice call":e.state==="cancelled"?"Cancelled voice call":e.state==="failed"?"Voice call failed":"Voice call ended";return <div key={"call-"+e.id}>{dayChanged&&<div className="sera-chat-day">{day(item.at)}</div>}<div className="sera-call-event" role="note"><Phone size={17}/><span><strong>{title}</strong><small>{e.caller===userId?"Outgoing":"Incoming"} · {time(item.at)}</small></span></div></div>;}
        const m=item.m;const meeting=m.unsent?null:parseMeetingProposal(m.body);const own=m.senderId===userId,myReaction=m.reactions.find(r=>r.memberId===userId)?.emoji;
        const next=timeline[i+1];const showAvatar=!own&&!m.unsent&&(!next||next.kind!=="message"||next.m.senderId!==m.senderId||next.m.unsent||day(next.at)!==day(item.at));
        return <div key={m.id}>{dayChanged&&<div className="sera-chat-day">{day(m.createdAt)}</div>}<div className={"sera-chat-message-row"+(own?" is-own":"")} data-unread-id={!own&&!m.readAt&&!m.privateReadAt&&!m.unsent?m.id:undefined}>
          {!own&&<span className="sera-chat-message-avatar" aria-hidden="true">{showAvatar&&<MemberAvatar name={thread.name} photo={thread.photo} size={22}/>}</span>}<article className={"sera-chat-bubble"+(own?" is-own":"")+(m.unsent?" is-unsent":"")+(focusedMessage===m.id?" is-focused":"")+(menu===m.id?" is-selected":"")} onTouchStart={e=>{if(m.unsent||e.touches.length!==1)return;const t=e.touches[0];swipeStart.current={id:m.id,x:t.clientX,y:t.clientY};}} onTouchCancel={()=>{swipeStart.current=null;}} onTouchEnd={e=>{const start=swipeStart.current;swipeStart.current=null;const t=e.changedTouches[0];if(!start||start.id!==m.id||!t||m.unsent)return;const dx=t.clientX-start.x,dy=t.clientY-start.y;if((own?-dx:dx)>64&&Math.abs(dx)>Math.abs(dy)*1.6){setReply(m);setMenu(null);setReactions(null);input.current?.focus();}}}>
            {m.forwarded&&!m.unsent&&<small className="sera-chat-forwarded"><Forward size={13}/> Forwarded</small>}
            {m.reply&&!m.unsent&&<button type="button" className="sera-chat-quote" aria-label={"Go to quoted message from "+(m.reply.senderId===userId?"you":thread.name)} onClick={()=>focusMessage(m.reply!.id)}><strong>{m.reply.senderId===userId?"You":thread.name}</strong><span>{m.reply.unsent?"Message unsent":m.reply.body.slice(0,160)}</span></button>}
            <div id={"message-"+m.id}>{meeting?<div className="sera-meeting-card"><small>Meeting proposal</small><strong>{meeting.title}</strong><span>{new Date(meeting.at).toLocaleString()} · {meeting.minutes} min</span>{!own&&<div><button type="button" onClick={()=>{if(draft.trim()){setError("Finish your existing draft before responding.");return;}setReply(m);changeDraft("I accept the proposed meeting time.");input.current?.focus();}}>Accept</button><button type="button" onClick={()=>{if(draft.trim()){setError("Finish your existing draft before responding.");return;}setMeetingReply(m);setMeetingTitle(meeting.title);setMeetingAt("");setMeetingOpen(true);}}>Suggest time</button></div>}</div>:(!m.files.length||!["Attachment","Voice message"].includes(m.body))&&<p><MessageSearchText body={m.body} query={m.unsent?"":search}/></p>}
              {!m.unsent&&messageContact(m.body)&&<ContactMessageCard body={m.body}/>}
              {!m.unsent&&messageShareCards(m.body).map(card=><a className="sera-chat-share-card" key={card.url} href={card.url} target="_blank" rel="noopener noreferrer"><span><strong>{card.label}</strong><small>{card.kind==="professional"?"SERA.et · Open to view current content":"Google Maps · Open location"}</small></span><span aria-hidden="true">↗</span></a>)}
              {m.files.map(f=>f.mime==="video/mp4"?<div className="sera-chat-video" key={f.id}><video src={f.url} controls playsInline preload="metadata" aria-label={f.name}/><small>{f.name}</small></div>:f.mime.startsWith("audio/")?<div className="sera-chat-audio" key={f.id}><SeraVoicePlayer src={f.url} durationMs={f.durationMs}/></div>:f.mime.startsWith("image/")?<button type="button" className="sera-chat-image" key={f.id} onClick={()=>{setZoom(false);setViewer(f);}} aria-label={"View "+f.name}><img src={f.url} alt={f.name} loading="lazy"/></button>:<a className="sera-chat-file" href={f.url} key={f.id}><FileText size={23}/><span><strong>{f.name}</strong><small>PDF · {(f.size/1024).toFixed(0)} KB</small></span><ArrowDown size={18}/></a>)}
            </div>
            <div className="sera-chat-time"><time dateTime={m.createdAt}>{time(m.createdAt)}</time>{own&&!m.unsent&&<span className={"sera-chat-receipt"+(m.readAt?" is-read":"")} aria-label={m.readAt?"Read":m.deliveredAt?"Delivered":"Sent"} title={m.readAt?"Read":m.deliveredAt?"Delivered":"Sent"}>{m.deliveredAt||m.readAt?<CheckCheck size={16}/>:<Check size={15}/>}</span>}</div>
            {m.reactions.length>0&&!m.unsent&&<div className="sera-chat-reaction-chips">{[...new Set(m.reactions.map(r=>r.emoji))].map(e=><button type="button" key={e} aria-pressed={myReaction===e} onClick={()=>void react(m,e)}>{e} {m.reactions.filter(r=>r.emoji===e).length}</button>)}</div>}
          </article>{!m.unsent&&<div className="sera-chat-message-actions"><button className="sera-chat-icon sera-chat-react-quick" type="button" aria-label="React to message" onClick={()=>{setReactions(reactions===m.id?null:m.id);setMenu(null);}}><Smile size={18}/></button><button className="sera-chat-icon" type="button" aria-label="Message actions" aria-expanded={menu===m.id} onClick={()=>{setMenu(menu===m.id?null:m.id);setReactions(null);}}><MoreHorizontal size={19}/></button>
            {reactions===m.id&&<div className="sera-chat-reaction-picker">{REACTIONS.map(e=><button key={e} type="button" aria-label={"React "+e} aria-pressed={myReaction===e} onClick={()=>void react(m,e)}>{e}</button>)}</div>}
            {menu===m.id&&<div className="sera-chat-menu"><button type="button" onClick={()=>{setReply(m);setMenu(null);input.current?.focus();}}><Reply size={17}/> Reply</button><button type="button" onClick={()=>{setReactions(m.id);setMenu(null);}}><Smile size={17}/> React</button><button type="button" onClick={()=>{setForward(m);setForwardTo("");forwardId.current=crypto.randomUUID();setMenu(null);}}><Forward size={17}/> Forward</button><button type="button" onClick={()=>{void navigator.clipboard.writeText(m.body).then(()=>setMenu(null)).catch(()=>setError("Copy is unavailable in this browser."));}}><Copy size={17}/> Copy text</button>{own&&<button className="is-danger" type="button" onClick={()=>{setConfirm(m);setMenu(null);}}><Trash2 size={17}/> Unsend</button>}</div>}
          </div>}
        </div></div>;
      })}
      {local.map(p=><div className="sera-chat-message-row is-own" key={p.clientId}><article className="sera-chat-bubble is-own is-pending">{p.replyLabel&&<div className="sera-chat-quote">{p.replyLabel}</div>}<p>{p.body||p.attachment?.name||(p.album?.length?p.album.length+" photos":"Attachment")}</p><div className={"sera-chat-time"+(p.status==="failed"?" is-failed":"")} role="status">{p.status==="sent"?<Check size={15}/>:p.status==="failed"?<AlertCircle size={15}/>:<Clock size={14}/>} {p.status==="sent"?"Sent":p.status==="failed"?"Not sent":p.status==="sending"?(p.progress!=null&&p.progress<100?"Uploading "+p.progress+"%":p.progress===100?"Upload complete · sending…":"Sending…"):"Queued"}</div>{p.status==="sending"&&cancellable.includes(p.clientId)&&<button type="button" className="sera-chat-cancel-pending" onClick={()=>activeUploads.current.get(p.clientId)?.abort()}>Cancel upload</button>}{p.status==="sending"&&p.progress!=null&&p.progress<100&&<progress className="sera-chat-upload-progress" max={100} value={p.progress} aria-label="Attachment upload progress"/>}{p.status==="failed"&&<div className="sera-chat-failure"><small>{p.error}</small><button type="button" onClick={()=>setPending(ps=>ps.map(x=>x.clientId===p.clientId?{...x,status:"queued"}:x))}>Retry</button></div>}{(p.status==="queued"||p.status==="failed")&&<button type="button" className="sera-chat-cancel-pending" onClick={()=>{setPending(ps=>ps.filter(x=>x.clientId!==p.clientId));void removeOutbox(userId,thread.id,p.clientId).catch(()=>{});}}>Discard</button>}</article></div>)}
    </div>
    <div className="sera-chat-composer">
      {error&&<div className="sera-chat-error" role="alert"><AlertCircle size={17}/><span>{error}</span><button className="sera-chat-icon" type="button" aria-label="Dismiss message" onClick={()=>setError("")}><X size={16}/></button></div>}
      {reply&&<div className="sera-chat-reply-preview"><Reply size={19}/><span><strong>Replying to {reply.senderId===userId?"your message":thread.name}</strong><small>{preview(reply)}</small></span><button type="button" className="sera-chat-icon" aria-label="Cancel reply" onClick={()=>setReply(null)}><X size={19}/></button></div>}
      {album.length>0&&<div className="sera-chat-album-preview"><strong>{album.length} photos · Ready to send</strong><div>{album.map((p,i)=><AlbumThumbnail key={p.id} photo={p} onRemove={()=>{const next=album.filter((_,j)=>j!==i);if(next.length===1){setAttachment(next[0]);setAlbum([]);}else setAlbum(next);}}/>)}</div></div>}
      {attachment&&<div className="sera-chat-attachment-preview">{fileUrl&&attachment.meta.mime==="video/mp4"?<video className="sera-chat-video-preview" src={fileUrl} controls playsInline preload="metadata" aria-label="Video preview"/>:fileUrl&&attachment.meta.mime.startsWith("audio/")?<SeraVoicePlayer key={fileUrl} src={fileUrl} durationMs={attachment.meta.durationMs} label="Voice preview"/>:fileUrl&&attachment.meta.mime.startsWith("image/")?<img src={fileUrl} alt="Attachment preview"/>:<FileText size={27}/>}<span><strong>{attachment.meta.name}</strong><small>{(attachment.meta.size/1024).toFixed(0)} KB · Ready to send</small>{fileUrl&&attachment.meta.mime==="application/pdf"&&<a href={fileUrl} target="_blank" rel="noopener noreferrer" className="sera-chat-pdf-preview-link">Preview PDF</a>}</span><button type="button" className="sera-chat-icon" aria-label="Remove attachment" onClick={()=>setAttachment(null)}><X size={20}/></button></div>}
      <div className="sera-chat-compose-row"><input hidden ref={fileInput} type="file" accept="image/jpeg,image/png,image/webp,application/pdf,audio/mp4,audio/webm,audio/ogg,audio/mpeg" onChange={e=>{void choose(Array.from(e.target.files??[]));e.target.value="";}}/><button type="button" className="sera-chat-icon" aria-label="Open attachment studio" aria-expanded={studio} disabled={checkingAttachment} onClick={()=>setStudio(true)}><Paperclip size={22}/></button><VoiceRecorder disabled={Boolean(attachment)||album.length>0} onFile={(file,meta)=>{setAlbum([]);setAttachment({file,meta});setError("");}} onError={setError}/><textarea ref={input} rows={1} maxLength={MESSAGE_LIMIT} value={draft} onChange={e=>changeDraft(e.target.value)} placeholder="Message…" aria-label="Message" onKeyDown={e=>{if(e.key==="Enter"&&!e.shiftKey&&!e.nativeEvent.isComposing&&!window.matchMedia("(pointer: coarse)").matches){e.preventDefault();void send();}}}/><button type="button" className="sera-chat-send" disabled={checkingAttachment||(!draft.trim()&&!attachment&&!album.length)} aria-label="Send message" onClick={()=>void send()}><Send size={21}/></button></div>
      {draft.length>3600&&<div className="sera-chat-composer-foot" style={{display:"flex",justifyContent:"flex-end"}}>{draft.length}/{MESSAGE_LIMIT}</div>}
    </div>
    <dialog ref={viewerDialog} className="sera-private-image-viewer" aria-label="Photo viewer" onCancel={()=>setViewer(null)} onClose={()=>setViewer(null)}>{viewer&&<><header><span>{viewer.name}</span><button type="button" aria-label="Close photo" onClick={()=>setViewer(null)}><X/></button></header><div className="sera-private-image-stage"><img src={viewer.url} alt={viewer.name} className={zoom?"is-zoomed":""} onClick={()=>setZoom(z=>!z)} onError={()=>{setError("Photo unavailable or access expired.");setViewer(null);}}/></div><footer><button type="button" onClick={()=>setZoom(z=>!z)}>{zoom?"Fit image":"Zoom in"}</button><a href={viewer.url} download={viewer.name}>Save photo</a></footer></>}</dialog>
    {jumpVisible&&<button type="button" className="sera-chat-jump-latest" onClick={()=>{const el=scroll.current;if(el)el.scrollTo({top:el.scrollHeight,behavior:"smooth"});nearLatest.current=true;setJumpVisible(false);}}><ArrowDown size={16}/> Latest messages</button>}
    {meetingOpen&&<div className="sera-chat-dialog-backdrop"><section className="sera-chat-dialog" role="dialog" aria-modal="true" aria-label="Propose a meeting"><h3>Propose a meeting</h3><label>Title<input value={meetingTitle} onChange={e=>setMeetingTitle(e.target.value)} maxLength={120}/></label><label>Date and time (your local time)<input type="datetime-local" value={meetingAt} onChange={e=>setMeetingAt(e.target.value)}/></label><label>Duration<select value={meetingMinutes} onChange={e=>setMeetingMinutes(Number(e.target.value))}>{[15,30,45,60,90,120].map(n=><option key={n} value={n}>{n} minutes</option>)}</select></label><div className="sera-chat-dialog-actions"><button type="button" onClick={()=>setMeetingOpen(false)}>Cancel</button><button type="button" className="is-primary" onClick={()=>{const proposal=meetingProposalText(meetingTitle,meetingAt,meetingMinutes);if(!proposal){setError("Enter a title and future time.");return;}if(draft.trim()){setError("Finish your existing draft first.");return;}if(meetingReply)setReply(meetingReply);changeDraft(proposal);setMeetingReply(null);setMeetingOpen(false);input.current?.focus();}}>Add to message</button></div></section></div>}
    {shareMode&&<div className="sera-chat-dialog-backdrop"><section className="sera-chat-dialog" role="dialog" aria-modal="true" aria-label={shareMode==="professional"?"Share SERA content":shareMode==="contact"?"Share a contact":"Share a location"}><h3>{shareMode==="professional"?"Share SERA content":shareMode==="contact"?"Share a contact":"Share a location"}</h3>{shareMode==="professional"?<><p>Paste a SERA profile, portfolio, job or post link. The recipient must have permission to view it.</p><input aria-label="SERA link" value={shareValue} onChange={e=>setShareValue(e.target.value)} placeholder={SERA_ORIGIN+"/members/…"}/><button type="button" onClick={()=>setShareValue(SERA_ORIGIN+"/members/"+userId)}>My profile</button><button type="button" onClick={()=>setShareValue(SERA_ORIGIN+"/portfolio/"+userId)}>My portfolio</button></>:shareMode==="contact"?<><p>Review the name and phone number. Only these details will be shared.</p><button type="button" onClick={()=>void pickContact()}>Choose device contact</button><input aria-label="Contact name" placeholder="Name" maxLength={100} value={shareValue} onChange={e=>setShareValue(e.target.value)}/><input aria-label="Phone number" placeholder="Phone" inputMode="tel" maxLength={30} value={shareLng} onChange={e=>setShareLng(e.target.value)}/></>:<><p>Enter a latitude and longitude you want to share. No location is collected automatically.</p><input aria-label="Latitude" inputMode="decimal" placeholder="Latitude" value={shareValue} onChange={e=>setShareValue(e.target.value)}/><input aria-label="Longitude" inputMode="decimal" placeholder="Longitude" value={shareLng} onChange={e=>setShareLng(e.target.value)}/></>}<div className="sera-call-controls"><button type="button" onClick={()=>setShareMode(null)}>Cancel</button><button type="button" disabled={!(shareMode==="professional"?professionalShare(shareValue):shareMode==="contact"?contactShare(shareValue,shareLng):locationShare(shareValue,shareLng))} onClick={()=>{if(shareMode==="contact"){const contact=contactShare(shareValue,shareLng);if(contact)appendShare(contact);}else{const card=shareMode==="professional"?professionalShare(shareValue):locationShare(shareValue,shareLng);if(card)appendShare(card.url);}}}>Add to message</button></div></section></div>}
    <dialog ref={studioDialog} className="sera-attachment-studio" aria-label="Share something" onCancel={()=>setStudio(false)} onClose={()=>setStudio(false)} onClick={e=>{if(e.target===e.currentTarget)setStudio(false);}}><div className="sera-attachment-studio-inner"><div className="sera-attachment-studio-heading"><div><h3>Share something</h3><p>Choose an attachment</p></div><button type="button" className="sera-chat-icon" aria-label="Close attachment studio" onClick={()=>setStudio(false)}><X size={21}/></button></div><div className="sera-attachment-studio-grid"><button type="button" onClick={()=>pick("photo")}><ImagePlus/><strong>Photos</strong><small>Up to 4 images</small></button><button type="button" onClick={()=>pick("camera")}><Camera/><strong>Camera</strong><small>Capture a photo</small></button><button type="button" onClick={()=>pick("document")}><FileText/><strong>Document</strong><small>PDF files</small></button><button type="button" onClick={()=>pick("audio")}><Headphones/><strong>Audio</strong><small>Choose audio</small></button><button type="button" onClick={()=>pick("video")}><Video/><strong>Video</strong><small>MP4 · up to 5 MB</small></button><button type="button" onClick={()=>void chooseProfileCv()}><FileText/><strong>Profile CV</strong><small>Choose your saved CV</small></button><button type="button" onClick={()=>openShare("professional")}><FileText/><strong>SERA content</strong><small>Profile, portfolio, job or post</small></button><button type="button" onClick={()=>openShare("location")}><FileText/><strong>Location</strong><small>Enter a map pin</small></button><button type="button" onClick={()=>openShare("contact")}><FileText/><strong>Contact</strong><small>Share name and phone</small></button><button type="button" onClick={()=>{studioDialog.current?.close();setStudio(false);setMeetingReply(null);setMeetingOpen(true);}}><Clock/><strong>Meeting</strong><small>Propose a time</small></button></div><p className="sera-attachment-studio-note">Private conversation · Up to 5 MB per file</p></div></dialog>
    {confirm&&<div className="sera-chat-dialog-backdrop" onClick={()=>!actionBusy&&setConfirm(null)}><div className="sera-chat-dialog" role="dialog" aria-modal="true" aria-label="Unsend message" onClick={e=>e.stopPropagation()}><button className="sera-chat-icon sera-chat-dialog-close" type="button" onClick={()=>setConfirm(null)} aria-label="Close"><X size={21}/></button><h3>Unsend this message?</h3><p>This removes the message for everyone in this conversation. It cannot be restored.</p><div className="sera-chat-dialog-actions"><button type="button" onClick={()=>setConfirm(null)} disabled={actionBusy}>Cancel</button><button type="button" className="is-danger" onClick={()=>void remove()} disabled={actionBusy}>{actionBusy?"Removing…":"Unsend"}</button></div></div></div>}
    {forward&&<div className="sera-chat-dialog-backdrop" onClick={()=>!actionBusy&&setForward(null)}><div className="sera-chat-dialog" role="dialog" aria-modal="true" aria-label="Forward message" onClick={e=>e.stopPropagation()}><button className="sera-chat-icon sera-chat-dialog-close" type="button" onClick={()=>setForward(null)} aria-label="Close"><X size={21}/></button><h3>Forward message</h3><p className="sera-chat-forward-preview">{preview(forward)}</p><label>Choose a conversation<select value={forwardTo} onChange={e=>setForwardTo(e.target.value)}><option value="">Select a conversation</option>{threads.map(t=><option key={t.id} value={t.id}>{t.name}</option>)}</select></label><div className="sera-chat-dialog-actions"><button type="button" onClick={()=>setForward(null)} disabled={actionBusy}>Cancel</button><button type="button" className="is-primary" onClick={()=>void doForward()} disabled={!forwardTo||actionBusy}>{actionBusy?"Forwarding…":"Forward"}</button></div></div></div>}
  </section>;
}

function MessageSearchText({body,query}:{body:string;query:string}){
  const term=query.trim();
  if(!term)return body;
  const parts=[];
  let cursor=0;
  while(cursor<body.length){
    const index=body.toLowerCase().indexOf(term.toLowerCase(),cursor);
    if(index<0){parts.push(body.slice(cursor));break;}
    parts.push(body.slice(cursor,index),<mark key={index}>{body.slice(index,index+term.length)}</mark>);
    cursor=index+term.length;
  }
  return parts;
}

function AlbumThumbnail({photo,onRemove}:{photo:OutboxPhoto;onRemove:()=>void}){
  const image=useRef<HTMLImageElement>(null);
  useEffect(()=>{const url=URL.createObjectURL(photo.file);if(image.current)image.current.src=url;return()=>URL.revokeObjectURL(url);},[photo.file]);
  return <div className="sera-chat-album-thumb"><img ref={image} alt={photo.meta.name}/><button type="button" aria-label={"Remove "+photo.meta.name} onClick={onRemove}><X size={16}/></button></div>;
}

function ContactMessageCard({body}:{body:string}){const contact=messageContact(body);if(!contact)return null;return <div className="sera-chat-share-card"><span><strong>{contact.name}</strong><small>{contact.phone}</small></span><a href={"tel:"+contact.phone.replace(/[^+0-9]/g,"")} aria-label={"Call "+contact.name}>Call</a></div>;}

function ConversationControls({thread}:{thread:ThreadPreview}){
 const router=useRouter();const [busy,setBusy]=useState(false);const [error,setError]=useState("");
 const [safety,setSafety]=useState<"block"|"report"|null>(null),[reason,setReason]=useState("spam"),[details,setDetails]=useState("");
 async function submitSafety(){if(!safety||busy)return;setBusy(true);setError("");try{const result=await conversationSafety(thread.id,safety,reason,details);if(!result.ok)setError(result.error);else{setSafety(null);if(safety==="block")router.replace("/network/messages");else setError("Report submitted.");router.refresh();}}catch{setError("Unable to complete request.");}finally{setBusy(false);}}
 async function change(action:"pin"|"archive"|"unread"|"mute",value:boolean,minutes=0){
  setBusy(true);setError("");try{const result=await setConversationControl(thread.id,action,value,minutes);if(!result.ok)setError(result.error);else router.refresh();}catch{setError("Unable to save. Try again.");}finally{setBusy(false);}
 }
 return <div className="sera-chat-presence-settings"><button disabled={busy} onClick={()=>void change("pin",!thread.pinned)}>{thread.pinned?"Unpin":"Pin conversation"}</button><button disabled={busy} onClick={()=>void change("archive",!thread.archived)}>{thread.archived?"Restore to inbox":"Archive conversation"}</button><button disabled={busy} onClick={()=>void change("unread",!thread.markedUnread)}>{thread.markedUnread?"Clear follow-up":"Mark unread for follow-up"}</button><label>Mute alerts<select disabled={busy} value="" onChange={e=>void change("mute",true,Number(e.target.value))}><option value="" disabled>Choose duration</option><option value="0">Unmute</option><option value="60">1 hour</option><option value="480">8 hours</option><option value="1440">24 hours</option><option value="-1">Indefinitely</option></select></label><button disabled={busy} onClick={()=>setSafety("block")}>Block member</button><button disabled={busy} onClick={()=>setSafety("report")}>Report spam or abuse</button>{safety&&<div className="sera-chat-safety-confirm"><strong>{safety==="block"?"Block this member?":"Report this member"}</strong>{safety==="block"?<p>You will no longer be able to message each other or access this conversation.</p>:<><label>Reason<select value={reason} onChange={e=>setReason(e.target.value)}>{["spam","harassment","impersonation","inappropriate","other"].map(r=><option key={r}>{r}</option>)}</select></label><label>Details<textarea value={details} maxLength={1000} onChange={e=>setDetails(e.target.value)}/></label></>}<button disabled={busy} onClick={()=>void submitSafety()}>{safety==="block"?"Confirm block":"Submit report"}</button><button disabled={busy} onClick={()=>setSafety(null)}>Cancel</button></div>}{error&&<p role="status">{error}</p>}</div>
}
