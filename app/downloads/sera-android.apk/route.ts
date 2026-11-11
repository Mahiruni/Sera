import { NextRequest, NextResponse } from "next/server";

export const dynamic = "force-dynamic";
const REPO = "Mahiruni/Sera";

/** First-party stable download URL for older SERA Android clients. */
export async function GET(request: NextRequest) {
  const version = request.nextUrl.searchParams.get("version") || "";
  if (!/^\d+\.\d+\.\d+$/.test(version))
    return new NextResponse("Invalid version", { status: 400 });
  const tag = `android-v${version}`;
  const asset = `SERA-${version}.apk`;
  const url = `https://github.com/${REPO}/releases/download/${tag}/${asset}`;
  // A fixed repository and validated version prevent open redirects.
  return NextResponse.redirect(url, { status: 302, headers: { "Cache-Control": "no-store" } });
}
