import { NextResponse } from "next/server";
import { createClient } from "@supabase/supabase-js";
import { querySocialFeedPage } from "@/lib/social/feed-page";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

const noStore = { "Cache-Control": "private, no-store, max-age=0", Vary: "Authorization" };

/**
 * Native clients and the web feed share one authenticated, RLS-filtered query.
 * The public Supabase key is only the project identifier; the member's bearer
 * token remains the database identity and no service-role access is used here.
 */
export async function GET(request: Request) {
  const authorization = request.headers.get("authorization") || "";
  if (!/^Bearer [A-Za-z0-9._~-]+$/.test(authorization)) {
    return NextResponse.json({ error: "Authentication required." }, { status: 401, headers: noStore });
  }

  const url = process.env.NEXT_PUBLIC_SUPABASE_URL;
  const key = process.env.NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY;
  if (!url || !key) {
    return NextResponse.json({ error: "Feed service is unavailable." }, { status: 503, headers: noStore });
  }

  const token = authorization.slice(7);
  const db = createClient(url, key, {
    global: { headers: { Authorization: authorization } },
    auth: { persistSession: false, autoRefreshToken: false },
  });
  const { data: { user }, error: authError } = await db.auth.getUser(token);
  if (authError || !user) {
    return NextResponse.json({ error: "Your session has expired. Please sign in again." }, { status: 401, headers: noStore });
  }

  const params = new URL(request.url).searchParams;
  const page = Number(params.get("page") ?? "0");
  const limit = Number(params.get("limit") ?? "20");
  const view = params.get("view") ?? "latest";
  if (!Number.isSafeInteger(page) || page < 0 || page > 5000 ||
      !Number.isSafeInteger(limit) || limit < 1 || limit > 20 ||
      (view !== "latest" && view !== "for-you")) {
    return NextResponse.json({ error: "Invalid feed request." }, { status: 400, headers: noStore });
  }

  try {
    const result = await querySocialFeedPage(db, {
      offset: page * limit,
      pageSize: limit,
      personalized: view === "for-you",
    });
    if (result.error) {
      console.error("native feed query failed", { code: (result.error as { code?: string }).code });
      return NextResponse.json({ error: "Unable to load posts. Please retry." }, { status: 503, headers: noStore });
    }
    return NextResponse.json(
      { posts: result.rows.slice(0, limit), hasMore: result.rows.length > limit },
      { headers: noStore },
    );
  } catch (error) {
    console.error("native feed request failed", { name: error instanceof Error ? error.name : "UnknownError" });
    return NextResponse.json({ error: "Unable to load posts. Please retry." }, { status: 503, headers: noStore });
  }
}
