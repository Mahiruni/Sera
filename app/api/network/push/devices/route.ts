import { currentUser, serverClient } from "@/lib/supabase/server";

export const dynamic = "force-dynamic";
const validToken = (value: unknown): value is string =>
  typeof value === "string" && value.length >= 20 && value.length <= 4096 &&
  !/[\s\u0000-\u001f]/.test(value);

export async function POST(request: Request) {
  const user = await currentUser();
  if (!user?.email_confirmed_at) return Response.json({ error: "Unauthorized" }, { status: 401 });
  const body = await request.json().catch(() => null);
  if (!validToken(body?.token) || !["android", "web"].includes(body?.platform))
    return Response.json({ error: "Invalid device registration" }, { status: 400 });
  const db = await serverClient();
  // Never overwrite a token registered to another account.
  const { data: existing, error: lookupError } = await db.from("professional_push_devices")
    .select("id,user_id").eq("token", body.token).maybeSingle();
  if (lookupError) return Response.json({ error: "Registration unavailable" }, { status: 503 });
  if (existing && existing.user_id !== user.id)
    return Response.json({ error: "Token belongs to another account" }, { status: 409 });
  const operation = existing
    ? db.from("professional_push_devices").update({
        enabled: true, platform: body.platform, updated_at: new Date().toISOString(),
      }).eq("id", existing.id).eq("user_id", user.id)
    : db.from("professional_push_devices").insert({
        user_id: user.id, token: body.token, platform: body.platform, enabled: true,
      });
  const { error } = await operation;
  if (error) return Response.json({ error: "Unable to register device" }, { status: 503 });
  const response=Response.json({ registered: true }, { headers: { "Cache-Control": "no-store" } });
  if(body.platform==="web")response.headers.append("Set-Cookie","sera-web-push-token="+encodeURIComponent(body.token)+"; Path=/; HttpOnly; Secure; SameSite=Lax; Max-Age=2592000");
  return response;
}

export async function DELETE(request: Request) {
  const user = await currentUser();
  if (!user?.email_confirmed_at) return Response.json({ error: "Unauthorized" }, { status: 401 });
  const body = await request.json().catch(() => null);
  if (!validToken(body?.token)) return Response.json({ error: "Invalid token" }, { status: 400 });
  const db = await serverClient();
  const { error } = await db.from("professional_push_devices")
    .delete().eq("token", body.token).eq("user_id", user.id);
  if (error) return Response.json({ error: "Unable to unregister device" }, { status: 503 });
  const response=Response.json({ unregistered: true }, { headers: { "Cache-Control": "no-store" } });
  response.headers.append("Set-Cookie","sera-web-push-token=; Path=/; HttpOnly; Secure; SameSite=Lax; Max-Age=0");
  return response;
}
