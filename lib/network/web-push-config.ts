import "server-only";
export function webPushConfig(){
 try{
  const raw=JSON.parse(process.env.FIREBASE_WEB_CONFIG_JSON??"null") as Record<string,unknown>|null;
  const vapidKey=process.env.FIREBASE_WEB_VAPID_KEY;
  if(!raw||typeof raw.apiKey!=="string"||typeof raw.projectId!=="string"||typeof raw.messagingSenderId!=="string"||typeof raw.appId!=="string"||!vapidKey)return null;
  // Only public Firebase client fields; never expose service account JSON.
  const config={apiKey:raw.apiKey,projectId:raw.projectId,messagingSenderId:raw.messagingSenderId,appId:raw.appId};
  return {config,vapidKey};
 }catch{return null;}
}
