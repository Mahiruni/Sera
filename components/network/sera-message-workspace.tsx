"use client";
import {useCallback,useEffect,useLayoutEffect,useMemo,useRef,useState} from "react";
import Link from "next/link";
import {useRouter} from "next/navigation";
import {ArrowLeft,ArrowDown,Check,CheckCheck,Copy,FileText,Forward,MessageCircle,MoreHorizontal,Paperclip,Plus,Reply,Search,Send,Smile,Trash2,Volume2,VolumeX,X,Clock,AlertCircle} from "lucide-react";
import {MemberAvatar} from "@/components/activity/avatar";
import {ProfessionalPresence} from "@/components/network/professional-presence";
import {InboxRefresh} from "@/components/network/inbox-refresh";
import {VoiceRecorder} from "@/components/network/sera-voice-recorder";
import {openProfessionalConversation} from "@/lib/network/messaging";
import {prepareMessageUpload,sendMessageV2,setMessageReaction,unsendMessage,forwardMessage,recordMessageReceipts,loadEarlierMessages,refreshOlderMessages} from "@/lib/network/messaging-excellence";
import {REACTIONS,FILE_LIMIT,MESSAGE_LIMIT,isChatMime,validAttachment,type AttachmentMeta,type MessageView,type ThreadPreview} from "@/lib/network/message-contract";
import {saveOutbox,readOutbox,removeOutbox,type OutboxMessage} from "@/lib/network/message-outbox";
type Candidate={id:string;name:string;photo?:string|null};
type Pending=OutboxMessage&{status:"queued"|"sending"|"failed"|"sent";error?:string};
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
  const [query,setQuery]=useState("");
  const filtered=threads.filter(t=>t.name.toLowerCase().includes(query.trim().toLowerCase())||(t.lastBody??"").toLowerCase().includes(query.trim().toLowerCase()));
  return <div className={"sera-chat-workspace"+(selected?" has-thread":"")}>
    <InboxRefresh/><DeliverySync userId={userId} threads={threads}/>
    <aside className="sera-chat-sidebar" aria-label="Conversations">
      <div className="sera-chat-inbox-head"><div><h1>Messages</h1><p>Your professional conversations</p></div><details className="sera-chat-new"><summary className="sera-chat-icon" aria-label="New conversation"><Plus size={22}/></summary><div className="sera-chat-new-panel"><h3>New conversation</h3>{candidates.length?candidates.map(c=><form key={c.id} action={openProfessionalConversation}><input type="hidden" name="recipient" value={c.id}/><MemberAvatar name={c.name} photo={c.photo}/><span>{c.name}</span><button type="submit" className="sera-chat-icon" aria-label={"Message "+c.name}><MessageCircle size={19}/></button></form>):<p>Connect with a professional to start a conversation.</p>}<Link href="/connections">Discover connections</Link></div></details></div>
      <label className="sera-chat-list-search"><Search size={19}/><input value={query} onChange={e=>setQuery(e.target.value)} placeholder="Search conversations" aria-label="Search conversations"/></label>
      <div className="sera-chat-list">{filtered.map(t=><Link key={t.id} href={"/network/messages?thread="+t.id} className={"sera-chat-list-item"+(selected?.id===t.id?" is-selected":"")} aria-current={selected?.id===t.id?"page":undefined}>
        <MemberAvatar name={t.name} photo={t.photo} size={48}/><span className="sera-chat-list-text"><strong>{t.name}</strong><small>{t.lastSenderId===userId?"You: ":""}{t.lastBody||"Start a conversation"}</small></span><span className="sera-chat-list-meta"><time>{time(t.lastAt)}</time>{t.unread>0&&<span className="sera-chat-unread">{t.unread>99?"99+":t.unread}</span>}</span>
      </Link>)}{!filtered.length&&<div className="sera-chat-empty"><MessageCircle size={30}/><strong>{query?"No matching conversations":"Your inbox is ready"}</strong><p>{query?"Try another name or message.":"Start a conversation with a connection."}</p></div>}</div>
    </aside>
    {selected?<ChatThread key={selected.id+":"+search} userId={userId} thread={selected} threads={threads} messages={messages} hasMore={hasMore} search={search} loadError={loadError}/>:<section className="sera-chat-welcome"><div><MessageCircle size={40}/><h2>Conversations that move work forward.</h2><p>Select a conversation or start a new one with your professional network.</p></div></section>}
  </div>;
}
function ChatThread({userId,thread,threads,messages,hasMore,search,loadError}:{userId:string;thread:ThreadPreview;threads:ThreadPreview[];messages:MessageView[];hasMore:boolean;search:string;loadError:string}){
  const router=useRouter(),sound=useChatSound();
  const [draft,setDraft]=useState(""),[attachment,setAttachment]=useState<{file:File;meta:AttachmentMeta}|null>(null),[reply,setReply]=useState<MessageView|null>(null),[pending,setPending]=useState<Pending[]>([]);
  const [online,setOnline]=useState(true),[error,setError]=useState(loadError),[menu,setMenu]=useState<string|null>(null),[reactions,setReactions]=useState<string|null>(null),[confirm,setConfirm]=useState<MessageView|null>(null),[forward,setForward]=useState<MessageView|null>(null),[forwardTo,setForwardTo]=useState(""),[actionBusy,setActionBusy]=useState(false),[showSearch,setShowSearch]=useState(Boolean(search)),[settings,setSettings]=useState(false);
  const [older,setOlder]=useState<MessageView[]>([]),[more,setMore]=useState(hasMore),[loadingOlder,setLoadingOlder]=useState(false),[typing,setTyping]=useState(false),[fileUrl,setFileUrl]=useState<string|null>(null);
  const panel=useRef<HTMLElement|null>(null),scroll=useRef<HTMLDivElement>(null),input=useRef<HTMLTextAreaElement>(null),fileInput=useRef<HTMLInputElement>(null),working=useRef(new Set<string>()),mounted=useRef(true),typingTimer=useRef<ReturnType<typeof setTimeout>|null>(null),lastTyping=useRef(0),forwardId=useRef(""),receiptSeen=useRef(new Set<string>()),deliveredSeen=useRef(new Set<string>()),lastNew=useRef<string|null>(null),olderRef=useRef(older),scrollAnchor=useRef<number|null>(null),initialScroll=useRef(false);
  olderRef.current=older;
  const all=useMemo(()=>{const map=new Map<string,MessageView>();for(const m of older)map.set(m.id,m);for(const m of messages)map.set(m.id,m);return [...map.values()].sort((a,b)=>a.createdAt.localeCompare(b.createdAt)||a.id.localeCompare(b.id));},[older,messages]);
  const sentIds=useMemo(()=>new Set(all.flatMap(m=>m.clientId?[m.clientId]:[])),[all]);
  const local=pending.filter(p=>!sentIds.has(p.clientId));
  const announce=useCallback((active:boolean)=>{void fetch("/api/network/messages/typing",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({conversation:thread.id,typing:active}),keepalive:!active}).catch(()=>{});},[thread.id]);
  useEffect(()=>{mounted.current=true;setOnline(navigator.onLine);const up=()=>{setOnline(true);setPending(ps=>ps.map(p=>p.status==="failed"?{...p,status:"queued"}:p));};const down=()=>setOnline(false);
    window.addEventListener("online",up);window.addEventListener("offline",down);
    void readOutbox(userId,thread.id).then(items=>{if(mounted.current)setPending(ps=>{const ids=new Set(ps.map(p=>p.clientId));return [...ps,...items.filter(p=>!ids.has(p.clientId)).map(p=>({...p,status:"queued" as const}))];});}).catch(()=>{});
    return()=>{mounted.current=false;window.removeEventListener("online",up);window.removeEventListener("offline",down);if(typingTimer.current)clearTimeout(typingTimer.current);announce(false);};
  },[userId,thread.id,announce]);
  useEffect(()=>{const el=panel.current;if(!el)return;const sync=()=>{if(window.matchMedia("(max-width: 700px)").matches&&window.visualViewport){el.style.height=window.visualViewport.height+"px";el.style.top=window.visualViewport.offsetTop+"px";}else{el.style.height="";el.style.top="";}};sync();window.visualViewport?.addEventListener("resize",sync);window.visualViewport?.addEventListener("scroll",sync);window.addEventListener("resize",sync);return()=>{window.visualViewport?.removeEventListener("resize",sync);window.visualViewport?.removeEventListener("scroll",sync);window.removeEventListener("resize",sync);};},[]);
  useEffect(()=>{if(!attachment){setFileUrl(null);return;}const url=URL.createObjectURL(attachment.file);setFileUrl(url);return()=>URL.revokeObjectURL(url);},[attachment]);
  useEffect(()=>{let active=true;const poll=async()=>{if(document.visibilityState!=="visible")return;try{const r=await fetch("/api/network/messages/typing?conversation="+encodeURIComponent(thread.id),{cache:"no-store"});if(active&&r.ok)setTyping(Boolean((await r.json()).typing));}catch{if(active)setTyping(false);}};void poll();const id=setInterval(poll,2500);return()=>{active=false;clearInterval(id);};},[thread.id]);
  useEffect(()=>{if(!olderRef.current.length)return;let active=true;void refreshOlderMessages(thread.id,olderRef.current.map(m=>m.id).slice(0,600)).then(result=>{if(active&&result)setOlder(result);}).catch(()=>{});return()=>{active=false;};},[messages,thread.id]);
  useEffect(()=>{for(const p of pending)if(sentIds.has(p.clientId))void removeOutbox(userId,thread.id,p.clientId).catch(()=>{});setPending(ps=>ps.some(p=>sentIds.has(p.clientId))?ps.filter(p=>!sentIds.has(p.clientId)):ps);},[sentIds,userId,thread.id,pending]);
  const attempt=useCallback(async(p:Pending)=>{
    if(working.current.has(p.clientId)||!navigator.onLine)return;
    working.current.add(p.clientId);setPending(ps=>ps.map(x=>x.clientId===p.clientId?{...x,status:"sending",error:undefined}:x));
    try{
      let ack:string|undefined;
      if(p.file&&p.attachment){
        const prep=await prepareMessageUpload(thread.id,p.clientId,p.attachment);
        if(!prep.ok)throw new Error(prep.error);ack=prep.alreadySent;
        if(prep.url&&!ack){const upload=await fetch(prep.url,{method:"PUT",headers:{"Content-Type":p.attachment.mime,"x-upsert":"false"},body:p.file});
          if(!upload.ok){const retry=await prepareMessageUpload(thread.id,p.clientId,p.attachment);if(!retry.ok||retry.url)throw new Error("Private upload failed. Retry when connected.");ack=retry.alreadySent;}
        }
      }
      if(!ack){const r=await sendMessageV2({conversation:thread.id,clientId:p.clientId,body:p.body,replyTo:p.replyTo,attachment:p.attachment});if(!r.ok)throw new Error(r.error);ack=r.id;}
      await removeOutbox(userId,thread.id,p.clientId).catch(()=>{});
      if(mounted.current){setPending(ps=>ps.map(x=>x.clientId===p.clientId?{...x,status:"sent"}:x));sound.play();router.refresh();}
    }catch(e){if(mounted.current)setPending(ps=>ps.map(x=>x.clientId===p.clientId?{...x,status:navigator.onLine?"failed":"queued",error:e instanceof Error?e.message:"Connection lost. Retry."}:x));}
    finally{working.current.delete(p.clientId);}
  },[thread.id,userId,router,sound.play]);
  useEffect(()=>{if(online)for(const p of pending)if(p.status==="queued")void attempt(p);},[online,pending,attempt]);
  useEffect(()=>{const latest=messages.at(-1);if(!latest)return;if(lastNew.current&&latest.id!==lastNew.current&&latest.senderId!==userId)sound.play(true);lastNew.current=latest.id;},[messages,userId,sound.play]);
  useLayoutEffect(()=>{const el=scroll.current;if(!el)return;if(scrollAnchor.current!==null){el.scrollTop=el.scrollHeight-scrollAnchor.current;scrollAnchor.current=null;return;}if(!initialScroll.current){el.scrollTop=el.scrollHeight;initialScroll.current=true;}else if(el.scrollHeight-el.scrollTop-el.clientHeight<180||local.length)el.scrollTop=el.scrollHeight;},[all,local.length]);
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
  function changeDraft(value:string){setDraft(value.slice(0,MESSAGE_LIMIT));if(Date.now()-lastTyping.current>3000){lastTyping.current=Date.now();announce(Boolean(value.trim()));}if(typingTimer.current)clearTimeout(typingTimer.current);typingTimer.current=setTimeout(()=>announce(false),3500);}
  function choose(file:File|undefined){if(!file)return;const mime=file.type==="audio/x-m4a"?"audio/mp4":file.type;if(!isChatMime(mime)||file.size>FILE_LIMIT||!file.size){setError("Choose a supported image, PDF or audio file of 5 MB or less.");return;}const normalized=file.type===mime?file:new File([file],file.name,{type:mime});const meta={name:file.name.slice(0,180)||"Attachment",mime,size:file.size};if(!validAttachment(meta))return;setAttachment({file:normalized,meta});setError("");}
  async function send(){if(!draft.trim()&&!attachment)return;const p:Pending={clientId:crypto.randomUUID(),conversation:thread.id,body:draft.trim(),replyTo:reply?.id??null,replyLabel:reply?preview(reply):null,createdAt:new Date().toISOString(),attachment:attachment?.meta??null,file:attachment?.file??null,status:"queued"};
    setDraft("");setAttachment(null);setReply(null);setError("");announce(false);
    try{await saveOutbox(userId,p);}catch{if(!navigator.onLine)setError("Offline storage is unavailable. Keep this chat open to retry your message.");}
    if(mounted.current)setPending(ps=>[...ps,p]);
  }
  async function history(){if(loadingOlder||!all.length)return;setLoadingOlder(true);setError("");const first=all[0];try{const result=await loadEarlierMessages(thread.id,search,{id:first.id,createdAt:first.createdAt});if(result.error)throw new Error(result.error);const el=scroll.current;if(el)scrollAnchor.current=el.scrollHeight-el.scrollTop;setOlder(ps=>[...result.messages,...ps]);setMore(result.hasMore);}catch(e){setError(e instanceof Error?e.message:"Could not load older messages.");}finally{setLoadingOlder(false);}}
  async function react(m:MessageView,emoji:string){if(actionBusy)return;setActionBusy(true);setReactions(null);const own=m.reactions.find(r=>r.memberId===userId);try{const r=await setMessageReaction(m.id,own?.emoji===emoji?null:emoji);if(!r.ok)throw new Error(r.error);router.refresh();}catch(e){setError(e instanceof Error?e.message:"Reaction failed.");}finally{setActionBusy(false);}}
  async function remove(){if(!confirm||actionBusy)return;setActionBusy(true);try{const r=await unsendMessage(confirm.id);if(!r.ok)throw new Error(r.error);setConfirm(null);setError(r.warning||"");router.refresh();}catch(e){setError(e instanceof Error?e.message:"Could not unsend.");}finally{setActionBusy(false);}}
  async function doForward(){if(!forward||!forwardTo||actionBusy)return;setActionBusy(true);try{const r=await forwardMessage(forward.id,forwardTo,forwardId.current);if(!r.ok)throw new Error(r.error);setForward(null);setError("");router.refresh();}catch(e){setError(e instanceof Error?e.message:"Forward failed.");}finally{setActionBusy(false);}}
  return <section ref={panel} className="sera-chat-thread" aria-label={"Conversation with "+thread.name}>
    <header className="sera-chat-header"><Link href="/network/messages" className="sera-chat-icon sera-chat-back" aria-label="Back to inbox"><ArrowLeft size={23}/></Link><Link href={"/members/"+thread.peerId} className="sera-chat-identity"><MemberAvatar name={thread.name} photo={thread.photo} size={44}/><span><strong>{thread.name}</strong><small>{typing?<span className="sera-chat-typing">Typing…</span>:<ProfessionalPresence member={thread.peerId} compact/>}</small></span></Link><button className="sera-chat-icon" type="button" aria-label="Search conversation" onClick={()=>setShowSearch(v=>!v)}><Search size={21}/></button><div className="sera-chat-settings"><button className="sera-chat-icon" type="button" aria-label="Conversation settings" aria-expanded={settings} onClick={()=>setSettings(v=>!v)}><MoreHorizontal size={23}/></button>{settings&&<div className="sera-chat-menu"><button type="button" onClick={sound.toggle}>{sound.enabled?<Volume2 size={18}/>:<VolumeX size={18}/>}Message sounds {sound.enabled?"on":"off"}</button><div className="sera-chat-presence-settings"><ProfessionalPresence member={thread.peerId}/></div></div>}</div></header>
    {showSearch&&<form className="sera-chat-search" method="get" action="/network/messages"><input type="hidden" name="thread" value={thread.id}/><Search size={18}/><input name="q" type="search" defaultValue={search} placeholder="Search messages" maxLength={120} aria-label="Search messages"/><button type="submit" className="sera-chat-icon" aria-label="Search"><Search size={18}/></button><Link href={"/network/messages?thread="+thread.id} className="sera-chat-icon" aria-label="Clear search" onClick={()=>setShowSearch(false)}><X size={18}/></Link></form>}
    {!online&&<div className="sera-chat-offline" role="status"><Clock size={15}/> Offline · messages will send when connected</div>}
    <div className="sera-chat-scroll" ref={scroll} aria-label="Message history">
      {more&&all.length>0&&<button type="button" className="sera-chat-history" onClick={()=>void history()} disabled={loadingOlder}>{loadingOlder?"Loading…":"Load earlier messages"}</button>}
      {!all.length&&!local.length&&<div className="sera-chat-empty"><MessageCircle size={34}/><strong>{search?"No matching messages":"Start the conversation"}</strong><p>{search?"Try another search.":"Say hello, share an idea or send a project file."}</p></div>}
      {all.map((m,i)=>{
        const own=m.senderId===userId,dayChanged=i===0||day(all[i-1].createdAt)!==day(m.createdAt),myReaction=m.reactions.find(r=>r.memberId===userId)?.emoji;
        return <div key={m.id}>{dayChanged&&<div className="sera-chat-day">{day(m.createdAt)}</div>}<div className={"sera-chat-message-row"+(own?" is-own":"")} data-unread-id={!own&&!m.readAt&&!m.unsent?m.id:undefined}>
          {!own&&<MemberAvatar name={thread.name} photo={thread.photo} size={28}/>}<article className={"sera-chat-bubble"+(own?" is-own":"")+(m.unsent?" is-unsent":"")}>
            {m.forwarded&&!m.unsent&&<small className="sera-chat-forwarded"><Forward size={13}/> Forwarded</small>}
            {m.reply&&!m.unsent&&<button type="button" className="sera-chat-quote" onClick={()=>document.getElementById("message-"+m.reply!.id)?.scrollIntoView({block:"center",behavior:"smooth"})}><strong>{m.reply.senderId===userId?"You":thread.name}</strong><span>{m.reply.body.slice(0,160)}</span></button>}
            <div id={"message-"+m.id}>{(!m.files.length||!["Attachment","Voice message"].includes(m.body))&&<p>{m.body}</p>}
              {m.files.map(f=>f.mime.startsWith("audio/")?<div className="sera-chat-audio" key={f.id}><audio controls preload="metadata" src={f.url} aria-label="Voice message"/>{f.durationMs&&<small>{Math.floor(f.durationMs/60000)}:{String(Math.floor(f.durationMs/1000)%60).padStart(2,"0")}</small>}</div>:f.mime.startsWith("image/")?<a className="sera-chat-image" key={f.id} href={f.url} target="_blank" rel="noopener noreferrer"><img src={f.url} alt={f.name} loading="lazy"/></a>:<a className="sera-chat-file" href={f.url} key={f.id}><FileText size={23}/><span><strong>{f.name}</strong><small>PDF · {(f.size/1024).toFixed(0)} KB</small></span><ArrowDown size={18}/></a>)}
            </div>
            <div className="sera-chat-time"><time dateTime={m.createdAt}>{time(m.createdAt)}</time>{own&&!m.unsent&&<span className={"sera-chat-receipt"+(m.readAt?" is-read":"")} aria-label={m.readAt?"Read":m.deliveredAt?"Delivered":"Sent"} title={m.readAt?"Read":m.deliveredAt?"Delivered":"Sent"}>{m.deliveredAt||m.readAt?<CheckCheck size={16}/>:<Check size={15}/>}</span>}</div>
            {m.reactions.length>0&&!m.unsent&&<div className="sera-chat-reaction-chips">{[...new Set(m.reactions.map(r=>r.emoji))].map(e=><button type="button" key={e} aria-pressed={myReaction===e} onClick={()=>void react(m,e)}>{e} {m.reactions.filter(r=>r.emoji===e).length}</button>)}</div>}
          </article>{!m.unsent&&<div className="sera-chat-message-actions"><button className="sera-chat-icon" type="button" aria-label="React to message" onClick={()=>{setReactions(reactions===m.id?null:m.id);setMenu(null);}}><Smile size={18}/></button><button className="sera-chat-icon" type="button" aria-label="Message actions" aria-expanded={menu===m.id} onClick={()=>{setMenu(menu===m.id?null:m.id);setReactions(null);}}><MoreHorizontal size={19}/></button>
            {reactions===m.id&&<div className="sera-chat-reaction-picker">{REACTIONS.map(e=><button key={e} type="button" aria-label={"React "+e} aria-pressed={myReaction===e} onClick={()=>void react(m,e)}>{e}</button>)}</div>}
            {menu===m.id&&<div className="sera-chat-menu"><button type="button" onClick={()=>{setReply(m);setMenu(null);input.current?.focus();}}><Reply size={17}/> Reply</button><button type="button" onClick={()=>{setForward(m);setForwardTo("");forwardId.current=crypto.randomUUID();setMenu(null);}}><Forward size={17}/> Forward</button><button type="button" onClick={()=>{void navigator.clipboard.writeText(m.body).then(()=>setMenu(null)).catch(()=>setError("Copy is unavailable in this browser."));}}><Copy size={17}/> Copy text</button>{own&&<button className="is-danger" type="button" onClick={()=>{setConfirm(m);setMenu(null);}}><Trash2 size={17}/> Unsend</button>}</div>}
          </div>}
        </div></div>;
      })}
      {local.map(p=><div className="sera-chat-message-row is-own" key={p.clientId}><article className="sera-chat-bubble is-own is-pending">{p.replyLabel&&<div className="sera-chat-quote">{p.replyLabel}</div>}<p>{p.body||p.attachment?.name||"Attachment"}</p><div className="sera-chat-time">{p.status==="sent"?<Check size={15}/>:p.status==="failed"?<AlertCircle size={15}/>:<Clock size={14}/>} {p.status==="sent"?"Sent":p.status==="failed"?"Not sent":p.status==="sending"?"Sending…":"Queued"}</div>{p.status==="failed"&&<div className="sera-chat-failure"><small>{p.error}</small><button type="button" onClick={()=>setPending(ps=>ps.map(x=>x.clientId===p.clientId?{...x,status:"queued"}:x))}>Retry</button></div>}{(p.status==="queued"||p.status==="failed")&&<button type="button" className="sera-chat-cancel-pending" onClick={()=>{setPending(ps=>ps.filter(x=>x.clientId!==p.clientId));void removeOutbox(userId,thread.id,p.clientId).catch(()=>{});}}>Discard</button>}</article></div>)}
    </div>
    <div className="sera-chat-composer">
      {error&&<div className="sera-chat-error" role="alert"><AlertCircle size={17}/><span>{error}</span><button className="sera-chat-icon" type="button" aria-label="Dismiss message" onClick={()=>setError("")}><X size={16}/></button></div>}
      {reply&&<div className="sera-chat-reply-preview"><Reply size={19}/><span><strong>Replying to {reply.senderId===userId?"your message":thread.name}</strong><small>{preview(reply)}</small></span><button type="button" className="sera-chat-icon" aria-label="Cancel reply" onClick={()=>setReply(null)}><X size={19}/></button></div>}
      {attachment&&<div className="sera-chat-attachment-preview">{fileUrl&&attachment.meta.mime.startsWith("audio/")?<audio controls src={fileUrl} aria-label="Preview voice message"/>:fileUrl&&attachment.meta.mime.startsWith("image/")?<img src={fileUrl} alt="Attachment preview"/>:<FileText size={27}/>}<span><strong>{attachment.meta.name}</strong><small>{(attachment.meta.size/1024).toFixed(0)} KB · Ready to send</small></span><button type="button" className="sera-chat-icon" aria-label="Remove attachment" onClick={()=>setAttachment(null)}><X size={20}/></button></div>}
      <div className="sera-chat-compose-row"><input hidden ref={fileInput} type="file" accept="image/jpeg,image/png,image/webp,application/pdf,audio/mp4,audio/webm,audio/ogg,audio/mpeg" onChange={e=>{choose(e.target.files?.[0]);e.target.value="";}}/><button type="button" className="sera-chat-icon" aria-label="Attach a file" onClick={()=>fileInput.current?.click()}><Paperclip size={22}/></button><VoiceRecorder disabled={Boolean(attachment)} onFile={(file,meta)=>{setAttachment({file,meta});setError("");}} onError={setError}/><textarea ref={input} rows={1} maxLength={MESSAGE_LIMIT} value={draft} onChange={e=>changeDraft(e.target.value)} placeholder="Write a message…" aria-label="Message" onKeyDown={e=>{if(e.key==="Enter"&&!e.shiftKey&&!e.nativeEvent.isComposing&&!window.matchMedia("(pointer: coarse)").matches){e.preventDefault();void send();}}}/><button type="button" className="sera-chat-send" disabled={!draft.trim()&&!attachment} aria-label="Send message" onClick={()=>void send()}><Send size={21}/></button></div>
      <div className="sera-chat-composer-foot"><span>Private professional conversation</span><span>{draft.length>3600?draft.length+"/"+MESSAGE_LIMIT:""}</span></div>
    </div>
    {confirm&&<div className="sera-chat-dialog-backdrop" onClick={()=>!actionBusy&&setConfirm(null)}><div className="sera-chat-dialog" role="dialog" aria-modal="true" aria-label="Unsend message" onClick={e=>e.stopPropagation()}><button className="sera-chat-icon sera-chat-dialog-close" type="button" onClick={()=>setConfirm(null)} aria-label="Close"><X size={21}/></button><h3>Unsend this message?</h3><p>This removes the message for everyone in this conversation. It cannot be restored.</p><div className="sera-chat-dialog-actions"><button type="button" onClick={()=>setConfirm(null)} disabled={actionBusy}>Cancel</button><button type="button" className="is-danger" onClick={()=>void remove()} disabled={actionBusy}>{actionBusy?"Removing…":"Unsend"}</button></div></div></div>}
    {forward&&<div className="sera-chat-dialog-backdrop" onClick={()=>!actionBusy&&setForward(null)}><div className="sera-chat-dialog" role="dialog" aria-modal="true" aria-label="Forward message" onClick={e=>e.stopPropagation()}><button className="sera-chat-icon sera-chat-dialog-close" type="button" onClick={()=>setForward(null)} aria-label="Close"><X size={21}/></button><h3>Forward message</h3><p className="sera-chat-forward-preview">{preview(forward)}</p><label>Choose a conversation<select value={forwardTo} onChange={e=>setForwardTo(e.target.value)}><option value="">Select a conversation</option>{threads.map(t=><option key={t.id} value={t.id}>{t.name}</option>)}</select></label><div className="sera-chat-dialog-actions"><button type="button" onClick={()=>setForward(null)} disabled={actionBusy}>Cancel</button><button type="button" className="is-primary" onClick={()=>void doForward()} disabled={!forwardTo||actionBusy}>{actionBusy?"Forwarding…":"Forward"}</button></div></div></div>}
  </section>;
}
