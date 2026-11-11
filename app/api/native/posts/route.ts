import { NextRequest, NextResponse } from "next/server";
import { createClient } from "@supabase/supabase-js";
import { moderationEnabled, screenSubmission } from "@/lib/moderation/screen";
import { moderationWriter, securePublishingConfigured } from "@/lib/moderation/writer";
import { readPost } from "@/lib/social/validation";

export const runtime = "nodejs";

export async function POST(request: NextRequest) {
  const bearer = request.headers.get("authorization") || "";
  if (!/^Bearer [A-Za-z0-9._~-]+$/.test(bearer))
    return NextResponse.json({ error: "Authentication required." }, { status: 401 });
  if (!moderationEnabled() || !securePublishingConfigured())
    return NextResponse.json({ error: "Secure publishing is unavailable." }, { status: 503 });
  const url = process.env.NEXT_PUBLIC_SUPABASE_URL;
  const key = process.env.NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY;
  if (!url || !key)
    return NextResponse.json({ error: "Publishing configuration unavailable." }, { status: 503 });
  const client = createClient(url, key, {
    global: { headers: { Authorization: bearer } },
    auth: { persistSession: false, autoRefreshToken: false },
  });
  const { data: { user }, error: authError } = await client.auth.getUser(bearer.slice(7));
  if (authError || !user?.email_confirmed_at)
    return NextResponse.json({ error: "Verified account required." }, { status: 401 });
  const length = Number(request.headers.get("content-length") || "0");
  if (length > 16000)
    return NextResponse.json({ error: "Post exceeds request size limit." }, { status: 413 });
  let payload: unknown;
  try { payload = await request.json(); } catch {
    return NextResponse.json({ error: "Invalid request." }, { status: 400 });
  }
  if (!payload || typeof payload !== "object" || Array.isArray(payload))
    return NextResponse.json({ error: "Invalid post." }, { status: 400 });
  const values = payload as Record<string, unknown>;
  const form = new FormData();
  for (const field of ["kind", "title", "body", "quote_source", "category", "location", "skills", "project_url"]) {
    if (values[field] !== undefined && typeof values[field] !== "string")
      return NextResponse.json({ error: "Invalid post fields." }, { status: 400 });
    form.set(field, (values[field] as string | undefined) || "");
  }
  if (values.media !== undefined)
    return NextResponse.json({ error: "Media publishing is not yet enabled." }, { status: 422 });
  const input = readPost(form);
  if (!input || input.kind === "photos")
    return NextResponse.json({ error: "Check the post title and body." }, { status: 400 });
  const verdict = await screenSubmission(input, []);
  if (verdict.verdict !== "approved")
    return NextResponse.json(
      { error: verdict.verdict === "rejected" ? "Post may violate community guidelines." : "Safety screening unavailable. Nothing published." },
      { status: verdict.verdict === "rejected" ? 422 : 503 },
    );
  const writer = moderationWriter();
  if (!writer) return NextResponse.json({ error: "Secure publishing unavailable." }, { status: 503 });
  // Database security requires INSERT to occur as the verified member, in draft state.
  // Only the trusted service-role client may promote an approved draft to published.
  const draft = await client.from("posts")
    .insert({ ...input, author_id: user.id, status: "draft" })
    .select("id").single();
  if (draft.error || !draft.data) {
    console.error("native text draft insert failed", { code: draft.error?.code });
    return NextResponse.json({ error: "Could not create your post. Please retry." }, { status: 409 });
  }
  const id = draft.data.id as string;
  const published = await writer.from("posts")
    .update({ status: "published" })
    .eq("id", id).eq("author_id", user.id).eq("status", "draft")
    .select("id").single();
  if (published.error || !published.data) {
    console.error("native text publication failed", { code: published.error?.code });
    const cleanup = await writer.from("posts").delete()
      .eq("id", id).eq("author_id", user.id).eq("status", "draft");
    if (cleanup.error) console.error("native text draft cleanup failed", { code: cleanup.error.code });
    return NextResponse.json({ error: "Could not finish publishing. Your draft is preserved in SERA; please retry." }, { status: 503 });
  }
  return NextResponse.json({ success: true, post_id: id }, { status: 201, headers: { "Cache-Control": "no-store" } });
}
