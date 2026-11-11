import { NextRequest, NextResponse } from "next/server";
import { createClient } from "@supabase/supabase-js";
import { dispatchWelcome, welcomeConfigured } from "@/lib/emails/welcome";

export const runtime = "nodejs";
export const maxDuration = 60;

/** Authenticated, verified users can trigger their own idempotent welcome delivery. */
export async function POST(request: NextRequest) {
  const bearer = request.headers.get("authorization") || "";
  if (!/^Bearer [A-Za-z0-9._~-]+$/.test(bearer))
    return NextResponse.json({ error: "Authentication required" }, { status: 401 });

  const url = process.env.NEXT_PUBLIC_SUPABASE_URL;
  const key = process.env.NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY;
  if (!url || !key || !welcomeConfigured())
    return NextResponse.json({ error: "Welcome email service unavailable" }, { status: 503 });

  const client = createClient(url, key, {
    global: { headers: { Authorization: bearer } },
    auth: { persistSession: false, autoRefreshToken: false },
  });
  const { data: { user }, error } = await client.auth.getUser(bearer.slice(7));
  if (error || !user?.id || !user.email_confirmed_at)
    return NextResponse.json({ error: "Verified account required" }, { status: 401 });

  // The existing claim_welcome database function and user_id uniqueness
  // prevent duplicates across Android, website and cron retries.
  const sent = await dispatchWelcome(user.id);
  return NextResponse.json(
    { accepted: !sent.deferred, delivered: sent.sent > 0 },
    { status: sent.deferred ? 503 : 200, headers: { "Cache-Control": "no-store" } }
  );
}
