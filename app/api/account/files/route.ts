import { NextRequest, NextResponse } from "next/server";
import { currentUser, serverClient } from "@/lib/supabase/server";
import { accountAssets, ASSET_BUCKET, ownedPath } from "@/lib/account/assets";
export const runtime = "nodejs";
export async function GET(req: NextRequest) {
  const user = await currentUser();
  if (!user?.email_confirmed_at) return new NextResponse(null, { status: 401 });
  try {
    const a = await accountAssets(user.id),
      kind = req.nextUrl.searchParams.get("kind");
    const path =
      kind === "avatar"
        ? a?.avatar_path
        : kind === "cv"
          ? a?.cv_path
          : kind === "branded"
            ? a?.branded_path
            : null;
    if (!ownedPath(path, user.id))
      return new NextResponse(null, { status: 404 });
    const client = await serverClient();
    const isDownload = req.nextUrl.searchParams.get("download") === "1";
    if (isDownload || kind === "avatar") {
      const q = await client.storage
        .from(ASSET_BUCKET)
        .createSignedUrl(
          path!,
          60,
          isDownload
            ? {
                download:
                  kind === "branded" ? "Sera-CV.pdf" : a?.cv_name || "CV.pdf",
              }
            : undefined,
        );
      if (q.error) return new NextResponse(null, { status: 404 });
      return NextResponse.redirect(q.data.signedUrl, {
        headers: {
          "Cache-Control": "private, no-store",
          "Referrer-Policy": "no-referrer",
        },
      });
    }
    const q = await client.storage.from(ASSET_BUCKET).download(path!);
    if (q.error) return new NextResponse(null, { status: 404 });
    return new NextResponse(await q.data.arrayBuffer(), {
      headers: {
        "Content-Type": "application/pdf",
        "Content-Disposition": 'inline; filename="CV.pdf"',
        "Cache-Control": "private, no-store",
        "Content-Security-Policy": "sandbox; default-src 'none'",
        "X-Content-Type-Options": "nosniff",
        "Referrer-Policy": "no-referrer",
      },
    });
  } catch {
    return new NextResponse(null, { status: 503 });
  }
}
