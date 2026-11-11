import { NextResponse } from "next/server";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

/** Fail closed until a stable-key, signed release is published to our own host. */
export async function GET() {
  const versionCode = Number(process.env.SERA_ANDROID_RELEASE_VERSION_CODE || 0);
  const versionName = process.env.SERA_ANDROID_RELEASE_VERSION_NAME || "";
  const apkUrl = process.env.SERA_ANDROID_RELEASE_APK_URL || "";
  const notes = process.env.SERA_ANDROID_RELEASE_NOTES || "";
  let valid = false;
  try {
    const parsed = new URL(apkUrl);
    valid = parsed.protocol === "https:" && parsed.hostname === "sera.hisabtech.com" &&
      !parsed.username && !parsed.password && parsed.pathname.endsWith(".apk");
  } catch { /* No release configured. */ }
  if (!Number.isSafeInteger(versionCode) || versionCode < 2 || !versionName ||
      !valid || process.env.SERA_ANDROID_RELEASE_ENABLED !== "true")
    return NextResponse.json({ available: false }, { headers: { "Cache-Control": "no-store" } });
  return NextResponse.json({ available: true, versionCode, versionName, apkUrl, notes }, {
    headers: { "Cache-Control": "no-store", "X-Content-Type-Options": "nosniff" },
  });
}
