import { NextRequest, NextResponse } from "next/server";
import { currentUser, serverClient } from "@/lib/supabase/server";
import { accountAssets, ASSET_BUCKET, ownedPath } from "@/lib/account/assets";
import { brandCv, CV_LIMIT } from "@/lib/account/cv";
import sharp from "sharp";
import { randomUUID } from "node:crypto";
export const runtime = "nodejs";
export const maxDuration = 60;
const fail = (error: string, status = 400) =>
  NextResponse.json({ error }, { status });
export async function POST(req: NextRequest) {
  if (req.headers.get("origin") !== new URL(req.url).origin)
    return fail("Request not allowed.", 403);
  const user = await currentUser();
  if (!user?.email_confirmed_at) return fail("Please sign in again.", 401);
  const length = Number(req.headers.get("content-length"));
  if (!length || length > 4 * 1024 * 1024)
    return fail("Upload must be smaller than 4 MB.", 413);
  const client = await serverClient();
  try {
    const data = await req.formData(),
      kind = data.get("kind"),
      operation = data.get("operation");
    if (kind !== "avatar" && kind !== "cv") return fail("Unknown file type.");
    const old = await accountAssets(user.id);
    const version = randomUUID();
    const removeOld = async (paths: (string | null | undefined)[]) => {
      const safe = paths.filter((p): p is string => ownedPath(p, user.id));
      if (safe.length) await client.storage.from(ASSET_BUCKET).remove(safe);
    };
    if (operation === "remove") {
      const fields =
        kind === "avatar"
          ? { avatar_path: null }
          : {
              cv_path: null,
              branded_path: null,
              cv_name: null,
              cv_uploaded_at: null,
              cv_status: null,
            };
      if (!old) return NextResponse.json({ success: true });
      const q = await client
        .from("account_assets")
        .update({ ...fields, version })
        .eq("user_id", user.id)
        .eq("version", old.version)
        .select("user_id")
        .maybeSingle();
      if (q.error || !q.data)
        return fail("Your files changed. Refresh and try again.", 409);
      await removeOld(
        kind === "avatar"
          ? [old?.avatar_path]
          : [old?.cv_path, old?.branded_path],
      );
      return NextResponse.json({ success: true });
    }
    if (operation === "retry") {
      if (kind !== "cv" || !ownedPath(old?.cv_path, user.id))
        return fail("No CV to process.");
      const f = await client.storage.from(ASSET_BUCKET).download(old!.cv_path!);
      if (f.error) return fail("CV could not be read.", 500);
      const branded = await brandCv(new Uint8Array(await f.data.arrayBuffer()));
      const path = `${user.id}/cv/${version}-sera.pdf`;
      const upload = await client.storage
        .from(ASSET_BUCKET)
        .upload(path, branded, { contentType: "application/pdf" });
      if (upload.error) return fail("Branded copy could not be stored.", 500);
      const q = await client
        .from("account_assets")
        .update({ branded_path: path, cv_status: "ready", version })
        .eq("user_id", user.id)
        .eq("version", old!.version)
        .select("user_id")
        .maybeSingle();
      if (q.error || !q.data) {
        await removeOld([path]);
        return fail("Your CV changed. Refresh and retry.", 409);
      }
      await removeOld([old?.branded_path]);
      return NextResponse.json({ success: true, status: "ready" });
    }
    const file = data.get("file");
    if (!(file instanceof File) || !file.size)
      return fail("Choose a file first.");
    let bytes = new Uint8Array(await file.arrayBuffer());
    let branded: Uint8Array | null = null,
      path: string,
      fields: Record<string, unknown>;
    if (kind === "avatar") {
      if (
        file.size > 2 * 1024 * 1024 ||
        !["image/jpeg", "image/png", "image/webp"].includes(file.type)
      )
        return fail("Use a JPG, PNG or WebP image below 2 MB.");
      bytes = new Uint8Array(
        await sharp(bytes, { limitInputPixels: 16000000 })
          .rotate()
          .resize(512, 512, { fit: "cover" })
          .jpeg({ quality: 85 })
          .toBuffer(),
      );
      path = `${user.id}/avatar/${version}.jpg`;
      fields = { avatar_path: path };
    } else {
      if (
        file.size > CV_LIMIT ||
        file.type !== "application/pdf" ||
        Buffer.from(bytes.subarray(0, 5)).toString() !== "%PDF-"
      )
        return fail("Use a PDF no larger than 3 MB.");
      try {
        branded = await brandCv(bytes);
      } catch {
        /* Keep the original even if branding is unavailable. */
      }
      path = `${user.id}/cv/${version}-original.pdf`;
      fields = {
        cv_path: path,
        cv_name: file.name.replace(/[\r\n\\/\x00-\x1f]/g, "_").slice(0, 180),
        cv_uploaded_at: new Date().toISOString(),
        cv_status: branded ? "ready" : "failed",
        branded_path: null,
      };
    }
    const upload = await client.storage
      .from(ASSET_BUCKET)
      .upload(path, bytes, {
        contentType: kind === "avatar" ? "image/jpeg" : "application/pdf",
        upsert: false,
      });
    if (upload.error)
      return fail("Upload could not be stored. Please retry.", 500);
    const created = [path];
    if (branded) {
      const bp = `${user.id}/cv/${version}-sera.pdf`;
      const b = await client.storage
        .from(ASSET_BUCKET)
        .upload(bp, branded, { contentType: "application/pdf" });
      if (b.error) {
        fields.cv_status = "failed";
      } else {
        fields.branded_path = bp;
        created.push(bp);
      }
    }
    // Compare-and-swap prevents a slow upload from replacing a more recent edit.
    const q = old
      ? await client
          .from("account_assets")
          .update({ ...fields, version })
          .eq("user_id", user.id)
          .eq("version", old.version)
          .select("user_id")
          .maybeSingle()
      : await client
          .from("account_assets")
          .insert({ user_id: user.id, ...fields, version })
          .select("user_id")
          .maybeSingle();
    if (q.error || !q.data) {
      await removeOld(created);
      return fail("Your files changed. Refresh and try again.", 409);
    }
    await removeOld(
      kind === "avatar"
        ? [old?.avatar_path]
        : [old?.cv_path, old?.branded_path],
    );
    return NextResponse.json({
      success: true,
      status: fields.cv_status || "ready",
    });
  } catch {
    return fail(
      "File could not be processed. Try another export or image.",
      422,
    );
  }
}
