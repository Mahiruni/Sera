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
    const digest = typeof asset?.digest === "string" ? asset.digest : "";
    const sha256 = /^sha256:([a-fA-F0-9]{64})$/.exec(digest)?.[1]?.toLowerCase() || "";
    const parsed = new URL(apkUrl);
    if (!sha256 || !Number.isSafeInteger(versionCode) || versionCode < 2 ||
        parsed.protocol !== "https:" || parsed.hostname !== "github.com" ||
        parsed.pathname !== `/${REPO}/releases/download/${tag}/SERA-${versionName}.apk`)
      return NextResponse.json({ available: false }, { headers: jsonHeaders });
    const known: Record<string,string[]> = {"1.0.21":["Instant post reactions","Private APK downloads","Publishing corrections"],"1.0.22":["Professional activity status","Presence privacy controls"],"1.0.23":["Unique professional usernames","Shareable profile links","Username availability"],"1.0.24":["Animated update highlights","Improved updater placement","Username editor fix"]};
    const highlights = notes.split("\n").filter((line:string)=>line.startsWith("Highlight: ")).map((line:string)=>line.slice(11).trim()).filter((line:string)=>line.length<=48&&line.split(/\s+/).length<=4&&/^[a-zA-Z0-9 &-]+$/.test(line)).slice(0,8);
    const summary = highlights.length ? highlights : known[versionName] ?? ["Signed SERA update"];
    return NextResponse.json({
      available: true, versionCode, versionName, apkUrl: `https://sera.hisabtech.com/downloads/sera-android.apk?version=${versionName}`, sha256,
      notes: summary.join(" · "), highlights: summary,
    }, { headers: jsonHeaders });
  } catch {
    return NextResponse.json({ available: false }, { headers: jsonHeaders });
  }
}
