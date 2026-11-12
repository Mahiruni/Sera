/** Encrypted, account-scoped browser outbox. IndexedDB is optional; the UI keeps
 * a failed message in memory when browser storage is unavailable. */
import type {AttachmentMeta} from "@/lib/network/message-contract";
export type OutboxMessage={clientId:string;conversation:string;body:string;replyTo:string|null;replyLabel:string|null;createdAt:string;attachment:AttachmentMeta|null;file:File|null};
type Stored={id:string;scope:string;iv:ArrayBuffer;data:ArrayBuffer;fileIv?:ArrayBuffer;fileData?:ArrayBuffer};
let database:Promise<IDBDatabase>|undefined;
function db(){
  if(!database)database=new Promise((resolve,reject)=>{
    const r=indexedDB.open("sera-private-outbox-v1",1);
    r.onupgradeneeded=()=>{const d=r.result;d.createObjectStore("keys");const s=d.createObjectStore("messages",{keyPath:"id"});s.createIndex("scope","scope");};
    r.onsuccess=()=>resolve(r.result);r.onerror=()=>reject(r.error);r.onblocked=()=>reject(new Error("Outbox storage blocked"));
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
  const d=await db(),k=await key(user),{file,...meta}=message;
  const encrypted=await encrypt(k,new TextEncoder().encode(JSON.stringify(meta)).buffer);
  const attachment=file?await encrypt(k,await file.arrayBuffer()):null;
  const row:Stored={id:scope(user,message.conversation)+":"+message.clientId,scope:scope(user,message.conversation),...encrypted,fileIv:attachment?.iv,fileData:attachment?.data};
  await request(d.transaction("messages","readwrite").objectStore("messages").put(row));
}
export async function readOutbox(user:string,conversation:string):Promise<OutboxMessage[]>{
  const d=await db(),k=await key(user);
  const rows=await request(d.transaction("messages","readonly").objectStore("messages").index("scope").getAll(scope(user,conversation))) as Stored[];
  const result:OutboxMessage[]=[];
  for(const row of rows){try{
    const meta=JSON.parse(new TextDecoder().decode(await crypto.subtle.decrypt({name:"AES-GCM",iv:row.iv},k,row.data))) as Omit<OutboxMessage,"file">;
    const file=row.fileIv&&row.fileData&&meta.attachment?new File([await crypto.subtle.decrypt({name:"AES-GCM",iv:row.fileIv},k,row.fileData)],meta.attachment.name,{type:meta.attachment.mime}):null;
    result.push({...meta,file});
  }catch{/* Corrupt local data must never be sent. */}}
  return result.sort((a,b)=>a.createdAt.localeCompare(b.createdAt));
}
export async function removeOutbox(user:string,conversation:string,clientId:string){
  const d=await db();await request(d.transaction("messages","readwrite").objectStore("messages").delete(scope(user,conversation)+":"+clientId));
}
