import { NextRequest, NextResponse } from "next/server";
import { dispatchWelcome } from "@/lib/emails/welcome";
export const maxDuration = 300;
export async function GET(request: NextRequest) {
  const secret = process.env.CRON_SECRET;
  if (!secret || request.headers.get("authorization") !== `Bearer ${secret}`)
    return new NextResponse(null, { status: 401 });
  const result = await dispatchWelcome();
  return NextResponse.json(result, {
    status: result.deferred ? 503 : 200,
    headers: { "Cache-Control": "private, no-store" },
  });
}
