/** Public original post previews only. No media URLs, credentials or private posts. */
export const PUBLIC_FEED_CACHE_TTL=15*60*1000;
export type PublicFeedPreview={id:string;author:string;headline:string;title:string;body:string;publishedAt:string};
type Source={id:string;audience?:string;repost_of?:string|null;title:string;body:string;published_at:string;author:{full_name:string;headline:string}};
export function publicFeedPreviews(posts:Source[]):PublicFeedPreview[]{
  return posts.filter(p=>p.audience==="public"&&!p.repost_of&&p.id).slice(0,20).map(p=>({
    id:p.id,author:p.author.full_name.slice(0,120),headline:p.author.headline.slice(0,300),title:p.title.slice(0,300),body:p.body.slice(0,4000),publishedAt:p.published_at,
  }));
}
type Snapshot={owner:string;at:number;rows:PublicFeedPreview[]};
function db():Promise<IDBDatabase>{
  return new Promise((resolve,reject)=>{
    const request=indexedDB.open("sera-public-previews",1);
    request.onupgradeneeded=()=>request.result.createObjectStore("feed");
    request.onsuccess=()=>resolve(request.result);
    request.onerror=()=>reject(request.error);
  });
}
export async function savePublicFeed(owner:string,rows:PublicFeedPreview[]){
  if(!owner)return;
  const database=await db();
  try{await new Promise<void>((resolve,reject)=>{
    const tx=database.transaction("feed","readwrite"),store=tx.objectStore("feed");
    // Keep exactly one account snapshot on this browser.
    store.clear();
    if(rows.length)store.put({owner,at:Date.now(),rows:rows.slice(0,20)} satisfies Snapshot,"latest");
    tx.oncomplete=()=>resolve();tx.onerror=()=>reject(tx.error);tx.onabort=()=>reject(tx.error);
  });}finally{database.close();}
}
export async function readPublicFeed(owner:string):Promise<PublicFeedPreview[]>{
  if(!owner)return [];
  const database=await db();
  try{return await new Promise((resolve,reject)=>{
    const tx=database.transaction("feed","readwrite"),store=tx.objectStore("feed"),request=store.get("latest");
    request.onsuccess=()=>{
      const row=request.result as Snapshot|undefined;
      if(!row||row.owner!==owner||Date.now()-row.at<0||Date.now()-row.at>PUBLIC_FEED_CACHE_TTL||!Array.isArray(row.rows)){
        store.clear();resolve([]);
      }else resolve(row.rows);
    };
    request.onerror=()=>reject(request.error);
  });}finally{database.close();}
}
