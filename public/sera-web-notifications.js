/* global window, document, navigator, localStorage, Notification */
const storageKey="sera-web-push-owner";
const status=message=>{const el=document.getElementById("sera-browser-notification-status");if(el)el.textContent=message;};
const owner=()=>document.getElementById("sera-web-push-account")?.dataset.owner||"";
const supported=()=>window.isSecureContext&&"Notification" in window&&"serviceWorker" in navigator&&"PushManager" in window;
let current=null;
let offMessage=null;
let operation=false;
async function firebaseSession(){
 if(current)return current;
 const response=await fetch("/api/network/push/config",{cache:"no-store",credentials:"same-origin"});
 if(!response.ok)throw new Error("Browser notifications are not configured yet.");
 const {config,vapidKey}=await response.json();
 const [{initializeApp,getApps},{getMessaging,getToken,deleteToken,onMessage,isSupported}]=await Promise.all([
  import("https://www.gstatic.com/firebasejs/13.0.0/firebase-app.js"),
  import("https://www.gstatic.com/firebasejs/13.0.0/firebase-messaging.js")
 ]);
 if(!await isSupported())throw new Error("This browser does not support push notifications.");
 const app=getApps()[0]||initializeApp(config);
 const registration=await navigator.serviceWorker.register("/sera-firebase-sw.js");
 await navigator.serviceWorker.ready;
 const messaging=getMessaging(app);
 current={messaging,getToken,deleteToken,onMessage,registration,vapidKey};
 return current;
}
function banner(preview,thread,kind="message"){
 const old=document.getElementById("sera-web-message-banner");old?.remove();
 const a=document.createElement("a");a.id="sera-web-message-banner";a.href=(kind==="space_message"?"/collaboration?space=":"/network/messages?thread=")+encodeURIComponent(thread);
 a.setAttribute("role","status");Object.assign(a.style,{position:"fixed",top:"16px",left:"50%",transform:"translateX(-50%)",width:"min(440px, calc(100% - 24px))",zIndex:"10000",display:"flex",gap:"12px",alignItems:"center",padding:"14px",borderRadius:"18px",background:"var(--sera-surface, #fff)",color:"var(--sera-ink, #153b40)",boxShadow:"0 8px 32px #123a4033",border:"1px solid #087e8333",textDecoration:"none"});
 const avatar=document.createElement("span");Object.assign(avatar.style,{width:"44px",height:"44px",borderRadius:"50%",display:"grid",placeItems:"center",flexShrink:"0",background:"#087e83",color:"white",overflow:"hidden"});
 avatar.textContent=String(preview.title||"Sera").trim().split(/\s+/).slice(0,2).map(s=>s[0]).join("").toUpperCase();
 if(preview.avatar){const img=document.createElement("img");img.src=preview.avatar;img.alt="";Object.assign(img.style,{width:"44px",height:"44px",objectFit:"cover"});img.onerror=()=>img.remove();avatar.append(img);}
 const copy=document.createElement("span");const name=document.createElement("strong");name.textContent=preview.title||"Sera";const body=document.createElement("span");body.textContent=preview.body;body.style.display="block";body.style.fontSize="13px";copy.append(name,body);a.append(avatar,copy);document.body.append(a);setTimeout(()=>a.remove(),5500);
}
function listen(session){
 if(offMessage)return;
 offMessage=session.onMessage(session.messaging,async payload=>{
  const d=payload.data;if(!["message","space_message","missed_call"].includes(d?.kind)||d.recipient_id!==owner()||document.visibilityState!=="visible")return;
  if(d.kind==="space_message"&&location.pathname==="/collaboration"&&new URLSearchParams(location.search).get("space")===d.conversation_id)return;
  if(d.kind!=="space_message"&&location.pathname==="/network/messages"&&new URLSearchParams(location.search).get("thread")===d.conversation_id)return;
  const q=new URLSearchParams({owner:d.recipient_id,conversation:d.conversation_id,message:d.message_id,kind:d.kind});const endpoint=d.kind==="message"?"/api/native/message-preview":"/api/native/activity-preview";
  try{const r=await fetch(endpoint+"?"+q,{credentials:"same-origin",cache:"no-store",signal:AbortSignal.timeout(5000)});if(r.status===200&&d.recipient_id===owner())banner(await r.json(),d.conversation_id,d.kind);}catch{/* Do not show unauthorized previews. */}
 });
}
async function enable(){
 if(operation)return;operation=true;
 try{
  if(!supported())throw new Error("Browser notifications are unavailable on this device.");
  const permission=await Notification.requestPermission();
  if(permission!=="granted")throw new Error("Notification permission was not granted. Check browser settings.");
  const uid=owner();if(!uid)throw new Error("Sign in to Sera first.");
  const session=await firebaseSession();
  if(localStorage.getItem(storageKey)&&localStorage.getItem(storageKey)!==uid)await session.deleteToken(session.messaging);
  const token=await session.getToken(session.messaging,{vapidKey:session.vapidKey,serviceWorkerRegistration:session.registration});
  if(!token||owner()!==uid)throw new Error("Account changed. Try again.");
  const r=await fetch("/api/network/push/devices",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({token,platform:"web"}),credentials:"same-origin"});
  if(!r.ok)throw new Error("Could not register this browser. Try again.");
  localStorage.setItem(storageKey,uid);listen(session);status("Browser notifications enabled.");
 }catch(e){status(e instanceof Error?e.message:"Could not enable browser notifications.");}finally{operation=false;}
}
async function disable(){
 if(operation)return;operation=true;
 try{
  const session=await firebaseSession();const token=await session.getToken(session.messaging,{vapidKey:session.vapidKey,serviceWorkerRegistration:session.registration});
  if(token){await fetch("/api/network/push/devices",{method:"DELETE",headers:{"Content-Type":"application/json"},body:JSON.stringify({token}),credentials:"same-origin"});await session.deleteToken(session.messaging);}
  localStorage.removeItem(storageKey);offMessage?.();offMessage=null;
  session.registration.active?.postMessage({type:"sera-clear-notifications"});status("Browser notifications disabled.");
 }catch{status("Could not disable browser registration. You can also turn it off in browser settings.");}finally{operation=false;}
}
document.addEventListener("click",event=>{const button=event.target instanceof Element?event.target.closest("[data-sera-web-push]"):null;if(!button)return;if(button.dataset.seraWebPush==="enable")void enable();else if(button.dataset.seraWebPush==="disable")void disable();});
if(supported()&&Notification.permission==="granted"&&localStorage.getItem(storageKey)===owner())void firebaseSession().then(listen).catch(()=>{});
