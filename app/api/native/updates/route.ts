import { NextResponse } from "next/server";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

const REPO = "Mahiruni/Sera";
const jsonHeaders = { "Cache-Control": "no-store", "X-Content-Type-Options": "nosniff" };

/** Discover only public, published, production-signed Android releases. */
export async function GET() {
  try {
    const response = await fetch(
      `https://api.github.com/repos/${REPO}/releases/latest`,
      { headers: { Accept: "application/vnd.github+json", "User-Agent": "SERA-Android-Update" }, cache: "no-store", signal: AbortSignal.timeout(7000) },
    );
    if (!response.ok) return NextResponse.json({ available: false }, { headers: jsonHeaders });
    const release = await response.json();
    const tag = typeof release.tag_name === "string" ? release.tag_name : "";
    const match = /^android-v(\d+\.\d+\.\d+)$/.exec(tag);
    if (!match || release.draft || release.prerelease)
      return NextResponse.json({ available: false }, { headers: jsonHeaders });
    const versionName = match[1];
    const notes = typeof release.body === "string" ? release.body : "";
    const codeMatch = /Android versionCode:\s*(\d+)/.exec(notes);
    const versionCode = codeMatch ? Number(codeMatch[1]) : 0;
    const asset = Array.isArray(release.assets) ? release.assets.find(
      (item: { name?: string; browser_download_url?: string }) =>
        item.name === `SERA-${versionName}.apk`,
    ) : null;
    const apkUrl = asset?.browser_download_url || "";
    const parsed = new URL(apkUrl);
    if (!Number.isSafeInteger(versionCode) || versionCode < 2 ||
        parsed.protocol !== "https:" || parsed.hostname !== "github.com" ||
        parsed.pathname !== `/${REPO}/releases/download/${tag}/SERA-${versionName}.apk`)
      return NextResponse.json({ available: false }, { headers: jsonHeaders });
    return NextResponse.json({
      available: true, versionCode, versionName, apkUrl,
      notes: "A new signed SERA Android release is available.",
    }, { headers: jsonHeaders });
  } catch {
    return NextResponse.json({ available: false }, { headers: jsonHeaders });
  }
}
