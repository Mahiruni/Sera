import { NextRequest, NextResponse } from "next/server";
import { serverClient } from "@/lib/supabase/server";
import { loadComments } from "@/lib/social/data";
import { pageNumber, uuid } from "@/lib/social/validation";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";
const noStore = { "Cache-Control": "private, no-store" };

/** Authenticated, paged comment data for SERA's in-feed conversation sheet. */
export async function GET(request: NextRequest) {
  const id = request.nextUrl.searchParams.get("post_id");
  const page = pageNumber(request.nextUrl.searchParams.get("page") || "1");
  if (!uuid(id) || page > 100)
    return NextResponse.json({ error: "Invalid conversation." }, { status: 400, headers: noStore });
  const db = await serverClient();
  const { data: { user }, error: authError } = await db.auth.getUser();
  if (authError || !user?.email_confirmed_at)
    return NextResponse.json({ error: "Sign in to join the conversation." }, { status: 401, headers: noStore });
  const post = await db.from("posts").select("id").eq("id", id).eq("status", "published").maybeSingle();
  if (post.error || !post.data)
    return NextResponse.json({ error: "This post is no longer available." }, { status: 404, headers: noStore });
  try {
    const result = await loadComments(id, page);
    return NextResponse.json(result, { headers: noStore });
  } catch {
    return NextResponse.json({ error: "Unable to load the conversation. Please retry." },
      { status: 503, headers: noStore });
  }
}
