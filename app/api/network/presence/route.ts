import { currentUser, serverClient } from "@/lib/supabase/server";
export async function POST(request: Request) {
  const user = await currentUser();
  if (!user?.email_confirmed_at) return new Response(null, { status: 401 });
  const body = await request.json().catch(() => null);
  if (!body || (body.visibility!==undefined && body.visibility !== "connections" && body.visibility !== "hidden")) return new Response(null, { status: 400 });
  const db = await serverClient();
  const { error } = await db.rpc("sera_presence_heartbeat", {p_visibility:body.visibility??null});
  return new Response(null, { status: error ? 503 : 204, headers: { "Cache-Control": "no-store" } });
}
export async function GET(request: Request) {
  const user = await currentUser();
  if (!user?.email_confirmed_at) return Response.json({ online: false }, { status: 401 });
  const requested = new URL(request.url).searchParams.get("member");
  const member=requested==="self"?user.id:requested;
  if (!member || !/^[0-9a-f-]{36}$/i.test(member)) return Response.json({ online: false }, { status: 400 });
  const db = await serverClient();
  const { data, error } = await db.from("professional_presence")
    .select("last_seen_at,visibility").eq("member_id",member).maybeSingle();
  if (error) return Response.json({ online: false }, { status: 503 });
  return Response.json({
    online: Boolean(data && data.visibility === "connections" && Date.now()-new Date(data.last_seen_at).getTime()<90000),
    lastSeenAt: data?.visibility === "connections" ? data.last_seen_at : null,
    ...(member===user.id?{sharing:data?.visibility!=="hidden"}:{}),
  }, { headers: { "Cache-Control": "private, no-store" } });
}
