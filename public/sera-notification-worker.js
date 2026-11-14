/* global self, clients */
const seraValidId=value=>typeof value==="string"&&/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value);
self.addEventListener("notificationclick",event=>{
 const data=event.notification.data;
 if(!data?.sera||!seraValidId(data.conversation))return;
 event.notification.close();event.stopImmediatePropagation();
 const path=data.kind==="space_message"?"/collaboration?space=":"/network/messages?thread=";
 const url=new URL(path+data.conversation,self.location.origin).href;
 event.waitUntil((async()=>{const windows=await clients.matchAll({type:"window",includeUncontrolled:true});const existing=windows.find(c=>c.url.startsWith(self.location.origin+"/"));if(existing){await existing.navigate(url);await existing.focus();}else await clients.openWindow(url);})());
});
self.addEventListener("message",event=>{
 if(event.data?.type==="sera-clear-notifications")event.waitUntil(self.registration.getNotifications().then(items=>items.forEach(n=>{if(n.data?.sera)n.close();})));
});
self.seraHandlePush=async data=>{
 if(!["message","space_message","missed_call"].includes(data?.kind)||![data.recipient_id,data.conversation_id,data.message_id].every(seraValidId))return;
 const tag="sera-"+data.kind+"-"+data.recipient_id+"-"+(data.kind==="missed_call"?data.message_id:data.conversation_id);
 const existing=await self.registration.getNotifications({tag});
 if(existing.some(n=>n.data?.message===data.message_id))return;
 const q=new URLSearchParams({owner:data.recipient_id,conversation:data.conversation_id,message:data.message_id,kind:data.kind});
 const endpoint=data.kind==="message"?"/api/native/message-preview":"/api/native/activity-preview";
 try{
  const response=await fetch(endpoint+"?"+q,{credentials:"same-origin",cache:"no-store",signal:AbortSignal.timeout(5000)});
  // Never display another account's details, read/blocked messages, or denied previews.
  if(!response.ok||response.status!==200)return;
  const preview=await response.json();
  const options={body:preview.body,tag,renotify:false,data:{sera:true,kind:data.kind,conversation:data.conversation_id,message:data.message_id}};
  if(preview.avatar&&preview.mode!=="hidden")options.icon=preview.avatar;
  await self.registration.showNotification(preview.title||"Sera",options);
 }catch{/* The inbox remains the source of truth. Never put private data into fallback push payloads. */}
};
