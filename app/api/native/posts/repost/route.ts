import { NextRequest, NextResponse } from "next/server";
import { createClient } from "@supabase/supabase-js";
import { moderationEnabled, screenSubmission } from "@/lib/moderation/screen";
import { moderationWriter, securePublishingConfigured } from "@/lib/moderation/writer";
import { readPost, uuid } from "@/lib/social/validation";

export const runtime = "nodejs";
const noStore = { "Cache-Control": "no-store" };
const fail = (message: string, status: number) =>
  NextResponse.json({ error: message }, { status, headers: noStore });

/** Reposts always credit the original and are moderated before publication. */
export async function POST(request: NextRequest) {
  const bearer = request.headers.get("authorization") || "";
  if (!/^Bearer [A-Za-z0-9._~-]+$/.test(bearer))
    return fail("Please sign in before reposting.", 401);
  if (moderationEnabled() && !securePublishingConfigured())
    return fail("Secure repost publishing is temporarily unavailable.", 503);

  const url = process.env.NEXT_PUBLIC_SUPABASE_URL;
  const key = process.env.NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY;
  if (!url || !key) return fail("Publishing is not configured.", 503);

  const client = createClient(url, key, {
    global: { headers: { Authorization: bearer } },
    auth: { persistSession: false, autoRefreshToken: false },
  });
  const { data: { user }, error: authError } = await client.auth.getUser(bearer.slice(7));
  if (authError || !user?.email_confirmed_at)
    return fail("A verified SERA account is required.", 401);

  const size = Number(request.headers.get("content-length") || 0);
  if (size > 2048) return fail("Repost request exceeds the limit.", 413);
  const payload = await request.json().catch(() => null) as Record<string, unknown> | null;
  if (!payload || typeof payload !== "object" || Array.isArray(payload))
    return fail("Invalid repost.", 400);
  const sourceId = payload.post_id;
  const note = payload.note === undefined ? "" : payload.note;
  if (!uuid(sourceId) || typeof note !== "string" || note.length > 400)
    return fail("Choose a valid published post and a shorter note.", 400);

  const { data: source, error: sourceError } = await client.from("posts")
    .select("id,author_id,title,body,kind,repost_of")
    .eq("id", sourceId).eq("status", "published").maybeSingle();
  if (sourceError || !source)
    return fail("The original post is no longer available.", 404);
  if (source.author_id === user.id)
    return fail("This is already your own post.", 422);
  if (source.repost_of)
    return fail("Repost the original post instead.", 422);

  const { data: previous } = await client.from("posts").select("id,status")
    .eq("author_id", user.id).eq("repost_of", sourceId).limit(1).maybeSingle();
  if (previous?.status==="published") return NextResponse.json({success:true,post_id:previous.id},{status:201,headers:noStore});
  if(previous) {const cleanup=await client.from("posts").delete().eq("id",previous.id).eq("author_id",user.id).eq("status","draft");if(cleanup.error)return fail("Could not recover previous repost. Please retry.",409);}

  const title = "Repost";
  const body = note.trim() || "Shared original post";
  const form = new FormData();
  form.set("kind", "idea");
  form.set("title", title);
  form.set("body", body);
  const input = readPost(form);
  if (!input) return fail("Could not prepare this repost.", 422);
  const verdict = await screenSubmission(input, []);
  if (verdict.verdict !== "approved")
    return fail(verdict.verdict === "rejected"
      ? "This repost did not pass community safety review."
      : "Safety screening unavailable. Nothing reposted.",
      verdict.verdict === "rejected" ? 422 : 503);

  const writer = securePublishingConfigured() ? moderationWriter() : client;
  if (!writer) return fail("Secure publishing unavailable.", 503);
  const draft = await client.from("posts").insert({
    ...input, status: "draft", repost_of: sourceId, repost_note: note.trim(),
  }).select("id").single();
  if (draft.error || !draft.data) {
    if (draft.error?.code === "23505") return fail("You've already reposted this update.", 409);
    console.error("repost draft creation failed", { code: draft.error?.code });
    return fail("Could not create the repost. Please retry.", 409);
  }

  const id = draft.data.id as string;
  const publish = await writer.from("posts")
    .update({ status: "published" }).eq("id", id).eq("author_id", user.id)
    .eq("status", "draft").select("id").single();
  if (publish.error || !publish.data) {
    console.error("repost publication failed", { code: publish.error?.code });
    const cleanup = await writer.from("posts").delete()
      .eq("id", id).eq("author_id", user.id).eq("status", "draft");
    if (cleanup.error) console.error("repost cleanup failed", { code: cleanup.error.code });
    return fail("Could not complete the repost. Please retry.", 503);
  }

  return NextResponse.json({ success: true, post_id: id },
    { status: 201, headers: noStore });
}
