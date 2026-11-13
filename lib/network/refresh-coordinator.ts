/** One foreground-only refresh scheduler shared by inbox and notification chrome. */
export const REFRESH_MIN_MS=1000;
export const REFRESH_POLL_MS=30000;
export function refreshDelay(now:number,last:number):number {
  return Math.max(250,REFRESH_MIN_MS-(now-last));
}
type Subscriber={refresh:()=>void;realtime:boolean};
const subscribers=new Map<symbol,Subscriber>();
let timer:ReturnType<typeof setTimeout>|undefined;
let interval:ReturnType<typeof setInterval>|undefined;
let source:EventSource|null=null;
let last=0;
let listening=false;
const visible=()=>document.visibilityState==="visible"&&navigator.onLine;
function clearPending(){if(timer!==undefined){clearTimeout(timer);timer=undefined;}}
export function requestSeraRefresh(){
  if(!subscribers.size||!visible()||timer!==undefined)return;
  timer=setTimeout(()=>{
    timer=undefined;
    if(!subscribers.size||!visible())return;
    last=Date.now();
    // Next router refresh merges the server payload while retaining client state.
    subscribers.values().next().value?.refresh();
  },refreshDelay(Date.now(),last));
}
function disconnect(){source?.close();source=null;}
function reconcile(){
  if(!visible()){clearPending();disconnect();if(interval!==undefined){clearInterval(interval);interval=undefined;}return;}
  if(interval===undefined)interval=setInterval(requestSeraRefresh,REFRESH_POLL_MS);
  if([...subscribers.values()].some(s=>s.realtime)){
    if(!source){
      source=new EventSource("/api/network/messages/stream");
      source.onmessage=requestSeraRefresh;
      source.onopen=requestSeraRefresh;
      // Browser reconnects automatically; periodic sync remains the fallback.
      source.onerror=requestSeraRefresh;
    }
  }else disconnect();
  requestSeraRefresh();
}
export function subscribeSeraRefresh(refresh:()=>void,realtime=false):()=>void {
  const key=Symbol();
  subscribers.set(key,{refresh,realtime});
  if(!listening){
    listening=true;
    document.addEventListener("visibilitychange",reconcile);
    window.addEventListener("online",reconcile);
    window.addEventListener("offline",reconcile);
    window.addEventListener("focus",requestSeraRefresh);
  }
  reconcile();
  return ()=>{
    subscribers.delete(key);
    if(subscribers.size){reconcile();return;}
    clearPending();disconnect();
    if(interval!==undefined){clearInterval(interval);interval=undefined;}
    document.removeEventListener("visibilitychange",reconcile);
    window.removeEventListener("online",reconcile);
    window.removeEventListener("offline",reconcile);
    window.removeEventListener("focus",requestSeraRefresh);
    listening=false;last=0;
  };
}
