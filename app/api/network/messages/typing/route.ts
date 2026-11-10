import { currentUser, serverClient } from "@/lib/supabase/server";
const validId = (id: unknown): id is string =>
  typeof id === "string" && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(id);
export async function GET(request: Request) {
  const user = await currentUser();
  if (!user?.email_confirmed_at) return Response.json({ typing: false }, { status: 401 });
  const id = new URL(request.url).searchParams.get("conversation");
  if (!validId(id)) return Response.json({ typing: false }, { status: 400 });
  const db = await serverClient();
  const { data, error } = await db.from("professional_typing_status").select("member_id")
    .eq("conversation_id", id).neq("member_id", user.id).limit(1);
  if (error) return Response.json({ typing: false }, { status: 503 });
  return Response.json({ typing: Boolean(data?.length) }, { headers: { "Cache-Control": "private, no-store" } });
}
export async function POST(request: Request) {
  const user = await currentUser();
  if (!user?.email_confirmed_at) return new Response(null, { status: 401 });
  const data = await request.json().catch(() => null);
  if (!validId(data?.conversation) || typeof data?.typing !== "boolean")
    return new Response(null, { status: 400 });
  const db = await serverClient();
  // Explicit authorization prevents an outsider's silent no-op from looking successful.
  const { data: conversation } = await db.from("professional_conversations").select("id").eq("id", data.conversation).maybeSingle();
  if (!conversation) return new Response(null, { status: 403 });
  const query = data.typing
    ? db.from("professional_typing_status").upsert({
        conversation_id: data.conversation, member_id: user.id, updated_at: new Date().toISOString(),
      }, { onConflict: "conversation_id,member_id" })
    : db.from("professional_typing_status").delete().eq("conversation_id", data.conversation).eq("member_id", user.id);
  const { error } = await query;
  return new Response(null, { status: error ? 503 : 204 });
}
