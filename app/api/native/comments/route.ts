import { NextRequest, NextResponse } from "next/server";
import { createClient } from "@supabase/supabase-js";
import { moderationEnabled, screenSubmission } from "@/lib/moderation/screen";
import { moderationWriter, securePublishingConfigured } from "@/lib/moderation/writer";
import { uuid } from "@/lib/social/validation";

export const runtime = "nodejs";

export async function POST(request: NextRequest) {
  const bearer = request.headers.get("authorization") || "";
  if (!/^Bearer [A-Za-z0-9._~-]+$/.test(bearer))
    return NextResponse.json({ error: "Authentication required." }, { status: 401 });
  const url = process.env.NEXT_PUBLIC_SUPABASE_URL;
  const key = process.env.NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY;
  if (!url || !key || (moderationEnabled() && !securePublishingConfigured()))
    return NextResponse.json({ error: "Secure commenting is temporarily unavailable." }, { status: 503 });
  const client = createClient(url, key, {
    global: { headers: { Authorization: bearer } },
    auth: { persistSession: false, autoRefreshToken: false },
  });
  const { data: { user }, error: authError } = await client.auth.getUser(bearer.slice(7));
  if (authError || !user?.email_confirmed_at)
    return NextResponse.json({ error: "A verified account is required." }, { status: 401 });
  let payload: unknown;
  try { payload = await request.json(); } catch {
    return NextResponse.json({ error: "Invalid request." }, { status: 400 });
  }
  if (!payload || typeof payload !== "object" || Array.isArray(payload))
    return NextResponse.json({ error: "Invalid request." }, { status: 400 });
  const input = payload as Record<string, unknown>;
  const postId = input.post_id;
  const parentId = input.parent_id ?? null;
  const body = typeof input.body === "string" ? input.body.trim() : "";
  if (!uuid(postId) || (parentId !== null && !uuid(parentId)) || !body || body.length > 2000)
    return NextResponse.json({ error: "Check the comment (1–2,000 characters)." }, { status: 400 });
  const { data: post, error: postError } = await client.from("posts").select("id").eq("id", postId).eq("status", "published").maybeSingle();
  if (postError || !post)
    return NextResponse.json({ error: "This post is unavailable." }, { status: 404 });
  if (parentId) {
    const { data: parent, error: parentError } = await client.from("post_comments").select("id").eq("id", parentId).eq("post_id", postId).maybeSingle();
    if (parentError || !parent)
      return NextResponse.json({ error: "Parent comment is unavailable." }, { status: 404 });
  }
  const verdict = await screenSubmission({ kind: "idea", title: "", body, quote_source: "", category: "", skills: [] }, []);
  if (verdict.verdict !== "approved")
    return NextResponse.json({ error: verdict.verdict === "rejected" ? "Comment may violate community guidelines." : "Safety screening is unavailable. Nothing was published." }, { status: verdict.verdict === "rejected" ? 422 : 503 });
  const writer = securePublishingConfigured() ? moderationWriter() : client;
  if (!writer) return NextResponse.json({ error: "Secure commenting unavailable." }, { status: 503 });
  const { error } = await writer.from("post_comments").insert({ post_id: postId, parent_id: parentId, body, ...(securePublishingConfigured()?{author_id:user.id}:{}) });
  if (error) return NextResponse.json({ error: "Unable to publish comment. Please retry." }, { status: 409 });
  return NextResponse.json({ success: true }, { status: 201, headers: { "Cache-Control": "no-store" } });
}
