import {webPushConfig} from "@/lib/network/web-push-config";
export const dynamic="force-dynamic";
export async function GET(){
 const config=webPushConfig();
 if(!config)return new Response("// Browser push is not configured.",{status:503,headers:{"Content-Type":"application/javascript","Cache-Control":"no-store"}});
 const js='importScripts("/sera-notification-worker.js");\nimportScripts("https://www.gstatic.com/firebasejs/13.0.0/firebase-app-compat.js","https://www.gstatic.com/firebasejs/13.0.0/firebase-messaging-compat.js");\nfirebase.initializeApp('+JSON.stringify(config.config)+');\nfirebase.messaging().onBackgroundMessage(payload=>self.seraHandlePush(payload.data));';
 return new Response(js,{headers:{"Content-Type":"application/javascript; charset=utf-8","Cache-Control":"no-store","Service-Worker-Allowed":"/"}});
}
