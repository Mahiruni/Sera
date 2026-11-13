/** Encrypted, account-scoped browser outbox. IndexedDB is optional; the UI keeps
 * a failed message in memory when browser storage is unavailable. */
import type {AttachmentMeta,MessageView} from "@/lib/network/message-contract";
export type OutboxPhoto={id:string;meta:AttachmentMeta;file:File};
export type OutboxMessage={clientId:string;conversation:string;body:string;replyTo:string|null;replyLabel:string|null;createdAt:string;attachment:AttachmentMeta|null;file:File|null;album?:OutboxPhoto[]};
type Stored={id:string;scope:string;iv:ArrayBuffer;data:ArrayBuffer;fileIv?:ArrayBuffer;fileData?:ArrayBuffer;albumFiles?:{iv:ArrayBuffer;data:ArrayBuffer}[]};
let database:Promise<IDBDatabase>|undefined;
function db(){
  if(!database)database=new Promise((resolve,reject)=>{
    const r=indexedDB.open("sera-private-outbox-v1",2);
    r.onupgradeneeded=()=>{const d=r.result;if(!d.objectStoreNames.contains("keys"))d.createObjectStore("keys");if(!d.objectStoreNames.contains("messages")){const s=d.createObjectStore("messages",{keyPath:"id"});s.createIndex("scope","scope");}if(!d.objectStoreNames.contains("snapshots"))d.createObjectStore("snapshots");};
    r.onsuccess=()=>{r.result.onversionchange=()=>{r.result.close();database=undefined;};resolve(r.result);};r.onerror=()=>{database=undefined;reject(r.error);};r.onblocked=()=>reject(new Error("Outbox storage blocked"));
  });
  return database;
}
function request<T>(r:IDBRequest<T>):Promise<T>{return new Promise((resolve,reject)=>{r.onsuccess=()=>resolve(r.result);r.onerror=()=>reject(r.error);});}
async function key(user:string){
  const d=await db();const current=await request(d.transaction("keys","readonly").objectStore("keys").get(user)) as CryptoKey|undefined;
  if(current)return current;
  const created=await crypto.subtle.generateKey({name:"AES-GCM",length:256},false,["encrypt","decrypt"]);
  try{await request(d.transaction("keys","readwrite").objectStore("keys").add(created,user));return created;}
  catch{const existing=await request(d.transaction("keys","readonly").objectStore("keys").get(user)) as CryptoKey|undefined;if(!existing)throw new Error("Outbox key unavailable");return existing;}
}
async function encrypt(k:CryptoKey,value:ArrayBuffer){const iv=crypto.getRandomValues(new Uint8Array(12));return {iv:iv.buffer,data:await crypto.subtle.encrypt({name:"AES-GCM",iv},k,value)};}
const scope=(user:string,conversation:string)=>user+":"+conversation;
export async function saveOutbox(user:string,message:OutboxMessage){
  const d=await db(),k=await key(user),{file,album,...meta}=message;
  if(album&&(album.length<2||album.length>4||file))throw new Error("Invalid album");
  const encrypted=await encrypt(k,new TextEncoder().encode(JSON.stringify({...meta,albumMeta:album?.map(({id,meta})=>({id,meta}))})).buffer);
  const attachment=file?await encrypt(k,await file.arrayBuffer()):null;
  const albumFiles=album?await Promise.all(album.map(async p=>encrypt(k,await p.file.arrayBuffer()))):undefined;
  const row:Stored={id:scope(user,message.conversation)+":"+message.clientId,scope:scope(user,message.conversation),...encrypted,fileIv:attachment?.iv,fileData:attachment?.data,albumFiles};
  await request(d.transaction("messages","readwrite").objectStore("messages").put(row));
}
export async function readOutbox(user:string,conversation:string):Promise<OutboxMessage[]>{
  const d=await db(),k=await key(user);
  const rows=await request(d.transaction("messages","readonly").objectStore("messages").index("scope").getAll(scope(user,conversation))) as Stored[];
  const result:OutboxMessage[]=[];
  for(const row of rows){try{
    const meta=JSON.parse(new TextDecoder().decode(await crypto.subtle.decrypt({name:"AES-GCM",iv:row.iv},k,row.data))) as Omit<OutboxMessage,"file"|"album">&{albumMeta?:{id:string;meta:AttachmentMeta}[]};
    const file=row.fileIv&&row.fileData&&meta.attachment?new File([await crypto.subtle.decrypt({name:"AES-GCM",iv:row.fileIv},k,row.fileData)],meta.attachment.name,{type:meta.attachment.mime}):null;
    const {albumMeta,...base}=meta;
    let album:OutboxPhoto[]|undefined;
    if(albumMeta){if(albumMeta.length<2||albumMeta.length>4||row.albumFiles?.length!==albumMeta.length||file)throw new Error("Invalid stored album");album=await Promise.all(albumMeta.map(async (p,i)=>{const item=row.albumFiles![i];const bytes=await crypto.subtle.decrypt({name:"AES-GCM",iv:item.iv},k,item.data);if(bytes.byteLength!==p.meta.size)throw new Error("Corrupt photo");return {...p,file:new File([bytes],p.meta.name,{type:p.meta.mime})};}));}
    result.push({...base,file,album});
  }catch{/* Corrupt local data must never be sent. */}}
  return result.sort((a,b)=>a.createdAt.localeCompare(b.createdAt));
}
export async function removeOutbox(user:string,conversation:string,clientId:string){
  const d=await db();await request(d.transaction("messages","readwrite").objectStore("messages").delete(scope(user,conversation)+":"+clientId));
}

/** Private draft text, scoped to account and conversation. No plaintext fallback. */
const draftWrites=new Map<string,Promise<void>>();
export function saveChatDraft(user:string,conversation:string,body:string){
  const id=scope(user,conversation)+":draft";
  const task=(draftWrites.get(id)||Promise.resolve()).catch(()=>{}).then(async()=>{
    const d=await db();
    if(!body){await request(d.transaction("snapshots","readwrite").objectStore("snapshots").delete(id));return;}
    const k=await key(user),encrypted=await encrypt(k,new TextEncoder().encode(body.slice(0,4000)).buffer);
    await request(d.transaction("snapshots","readwrite").objectStore("snapshots").put({...encrypted,at:Date.now()},id));
  });
  draftWrites.set(id,task);void task.finally(()=>{if(draftWrites.get(id)===task)draftWrites.delete(id);}).catch(()=>{});
  return task;
}
export async function readChatDraft(user:string,conversation:string):Promise<string>{
  const id=scope(user,conversation)+":draft";await draftWrites.get(id)?.catch(()=>{});
  const d=await db(),row=await request(d.transaction("snapshots","readonly").objectStore("snapshots").get(id)) as {iv:ArrayBuffer;data:ArrayBuffer;at:number}|undefined;
  if(!row||Date.now()-row.at>30*86400000)return "";
  const k=await key(user);
  return new TextDecoder().decode(await crypto.subtle.decrypt({name:"AES-GCM",iv:row.iv},k,row.data)).slice(0,4000);
}

/** Cache only recent authorized message metadata and text; no attachment bytes or session credentials. */
export async function saveChatHistory(user:string,conversation:string,messages:MessageView[]){
 const d=await db(),k=await key(user),id=scope(user,conversation)+":history";
 const rows=messages.slice(-60).map(m=>m.unsent?{...m,body:"Message unsent",files:[],reactions:[]}:m);
 const raw=new TextEncoder().encode(JSON.stringify(rows));if(raw.byteLength>600000)return;
 const encrypted=await encrypt(k,raw.buffer);
 await request(d.transaction("snapshots","readwrite").objectStore("snapshots").put({...encrypted,at:Date.now()},id));
 const keys=await request(d.transaction("snapshots","readonly").objectStore("snapshots").getAllKeys());
 const history=keys.filter(x=>typeof x==="string"&&x.startsWith(user+":")&&x.endsWith(":history")&&x!==id);
 for(const old of history.slice(0,Math.max(0,history.length-7)))await request(d.transaction("snapshots","readwrite").objectStore("snapshots").delete(old));
}
export async function readChatHistory(user:string,conversation:string):Promise<MessageView[]>{
 const d=await db(),id=scope(user,conversation)+":history";
 const row=await request(d.transaction("snapshots","readonly").objectStore("snapshots").get(id)) as {iv:ArrayBuffer;data:ArrayBuffer;at:number}|undefined;
 if(!row)return [];
 if(Date.now()-row.at>600000||Date.now()<row.at){await request(d.transaction("snapshots","readwrite").objectStore("snapshots").delete(id));return [];}
 const k=await key(user),raw=JSON.parse(new TextDecoder().decode(await crypto.subtle.decrypt({name:"AES-GCM",iv:row.iv},k,row.data)));
 return Array.isArray(raw)?raw.slice(-60):[];
}
export async function clearChatHistory(user:string){
 const d=await db(),keys=await request(d.transaction("snapshots","readonly").objectStore("snapshots").getAllKeys());
 for(const key of keys)if(typeof key==="string"&&key.startsWith(user+":")&&key.endsWith(":history"))await request(d.transaction("snapshots","readwrite").objectStore("snapshots").delete(key));
}
