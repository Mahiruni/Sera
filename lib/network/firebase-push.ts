import "server-only";
import { createSign } from "node:crypto";

/** FCM HTTP v1 sender. Secrets are server-side only; never use NEXT_PUBLIC_ keys. */
type ServiceAccount = { project_id: string; client_email: string; private_key: string };
type PushPayload = { title: string; body: string; url: string; kind: string; callId?:string; recipientId?:string; expiresAt?:string; messageId?:string; conversationId?:string };
type PushResult = { delivered: number; invalidTokens: string[]; failed: number };
const b64 = (value: string) => Buffer.from(value).toString("base64url");
let cached: { token: string; expiresAt: number } | null = null;

function credentials(): ServiceAccount | null {
  const raw = process.env.FIREBASE_SERVICE_ACCOUNT_JSON;
  if (!raw) return null;
  try {
    const value: unknown = JSON.parse(raw);
    if (!value || typeof value !== "object") return null;
    const c = value as Partial<ServiceAccount>;
    if (!c.project_id || !c.client_email || !c.private_key) return null;
    return c as ServiceAccount;
  } catch { return null; }
}

async function accessToken(account: ServiceAccount): Promise<string> {
  if (cached && cached.expiresAt > Date.now() + 60000) return cached.token;
  const now = Math.floor(Date.now() / 1000);
  const header = b64(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claims = b64(JSON.stringify({
    iss: account.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    iat: now, exp: now + 3600,
  }));
  const unsigned = header + "." + claims;
  const signer = createSign("RSA-SHA256");
  signer.update(unsigned);
  signer.end();
  const assertion = unsigned + "." + signer.sign(account.private_key).toString("base64url");
  const response = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion,
    }),
    signal: AbortSignal.timeout(10000),
    cache: "no-store",
  });
  if (!response.ok) throw new Error("FCM authentication failed");
  const result = await response.json() as { access_token?: string; expires_in?: number };
  if (!result.access_token) throw new Error("FCM authentication returned no token");
  cached = { token: result.access_token, expiresAt: Date.now() + (result.expires_in ?? 3600) * 1000 };
  return cached.token;
}

/** Only call from trusted server jobs after authorization and recipient selection. */
export async function sendProfessionalPush(tokens: string[], payload: PushPayload, nativeAndroid = false): Promise<PushResult> {
  const account = credentials();
  if (!account) throw new Error("Firebase service account is not configured");
  const token = await accessToken(account);
  const safeUrl = payload.url.startsWith("/") && !payload.url.startsWith("//") ? payload.url : "/notifications";
  const result: PushResult = { delivered: 0, failed: 0, invalidTokens: [] };
  for (const deviceToken of [...new Set(tokens)].slice(0, 100)) {
    const ttl=payload.expiresAt?Math.floor((Date.parse(payload.expiresAt)-Date.now())/1000):null;
    const ttlCap=payload.messageId?1800:45;
    if(ttl!==null&&(!Number.isFinite(ttl)||ttl<=0))break;
    try {
      const response = await fetch(
        "https://fcm.googleapis.com/v1/projects/" + encodeURIComponent(account.project_id) + "/messages:send",
        {
          method: "POST",
          headers: { Authorization: "Bearer " + token, "Content-Type": "application/json" },
          body: JSON.stringify({ message: {
            token: deviceToken,
            ...(nativeAndroid ? {} : { notification: { title: payload.title.slice(0,100), body: payload.body.slice(0,200) } }),
            data: { url: safeUrl, kind: payload.kind.slice(0, 50), title: payload.title.slice(0, 100), body: payload.body.slice(0, 200), ...(payload.recipientId?{recipient_id:payload.recipientId}:{}), ...(payload.messageId&&payload.conversationId?{message_id:payload.messageId,conversation_id:payload.conversationId}:{}), ...(payload.callId?{call_id:payload.callId,expires_at:payload.expiresAt??""}:{}) },
            android: { priority: "high", ...(ttl!==null?{ttl:`${Math.min(ttl,ttlCap)}s`}:{}) },
            ...(ttl!==null||payload.messageId?{webpush:{...(ttl!==null?{headers:{TTL:String(Math.min(ttl,ttlCap))}}:{}),...(payload.messageId?{notification:{tag:"sera-message-"+payload.messageId}}:{})}}:{}),
          } }),
          signal: AbortSignal.timeout(10000), cache: "no-store",
        },
      );
      if (response.ok) { result.delivered++; continue; }
      const error = await response.json().catch(() => ({})) as { error?: { status?: string; details?: Array<{ errorCode?: string }> } };
      const invalid = error.error?.status === "UNREGISTERED" ||
        error.error?.details?.some((detail) => detail.errorCode === "UNREGISTERED");
      if (invalid) result.invalidTokens.push(deviceToken);
      else result.failed++;
    } catch { result.failed++; }
  }
  return result;
}
