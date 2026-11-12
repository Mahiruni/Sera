import { NextRequest, NextResponse } from "next/server";
import { createClient } from "@supabase/supabase-js";
import { randomUUID } from "node:crypto";
import sharp from "sharp";
import { screenSubmission } from "@/lib/moderation/screen";
import { moderationWriter, securePublishingConfigured } from "@/lib/moderation/writer";
import { MEDIA_BUCKET, MEDIA_LIMIT, readPost, validMedia } from "@/lib/social/validation";

export const runtime = "nodejs";
const fail = (error: string, status: number) => NextResponse.json({ error }, { status });

export async function POST(request: NextRequest) {
  const bearer = request.headers.get("authorization") || "";
  if (!/^Bearer [A-Za-z0-9._~-]+$/.test(bearer)) return fail("Authentication required.", 401);
  if (!securePublishingConfigured()) return fail("Secure publishing unavailable.", 503);
  const url = process.env.NEXT_PUBLIC_SUPABASE_URL;
  const key = process.env.NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY;
  if (!url || !key) return fail("Authentication configuration unavailable.", 503);
  const lengthHeader = request.headers.get("content-length");
  // Android's streaming multipart uploader uses Transfer-Encoding: chunked
  // and deliberately omits Content-Length. Enforce the byte cap while reading.
  if (lengthHeader !== null && (!/^\d+$/.test(lengthHeader) || Number(lengthHeader) > 4_000_000))
    return fail("Request exceeds the 4 MB media limit.", 413);
  const client = createClient(url, key, {
    global: { headers: { Authorization: bearer } },
    auth: { persistSession: false, autoRefreshToken: false },
  });
  const { data: { user }, error: authError } = await client.auth.getUser(bearer.slice(7));
  if (authError || !user?.email_confirmed_at) return fail("Verified account required.", 401);
  let form: FormData;
  try {
    const reader = request.body?.getReader();
    if (!reader) return fail("Empty media request.", 400);
    const chunks: Uint8Array[] = [];
    let size = 0;
    try {
      while (true) {
        const part = await reader.read();
        if (part.done) break;
        size += part.value.byteLength;
        if (size > 4_000_000) {
          await reader.cancel();
          return fail("Request exceeds the 4 MB media limit.", 413);
        }
        chunks.push(part.value);
      }
    } finally {
      reader.releaseLock();
    }
    if (!size) return fail("Empty media request.", 400);
    const combined = new Uint8Array(size);
    let offset = 0;
    for (const chunk of chunks) {
      combined.set(chunk, offset);
      offset += chunk.byteLength;
    }
    form = await new Response(combined, {
      headers: { "content-type": request.headers.get("content-type") || "" },
    }).formData();
  } catch {
    return fail("Invalid media request.", 400);
  }
  const files = form.getAll("media").filter((f): f is File => f instanceof File && f.size > 0);
  const input = readPost(form);
  if (!input || !files.length || !validMedia(files)) return fail("Choose up to four JPG, PNG or WebP images totaling at most 3 MB.", 400);
  const writer = moderationWriter();
  if (!writer) return fail("Secure publishing unavailable.", 503);
  const images: Buffer[] = [];
  try {
    for (const file of files) {
      images.push(await sharp(new Uint8Array(await file.arrayBuffer()), {
        limitInputPixels: 16000000, animated: false,
      }).rotate().resize({ width: 1024, height: 1024, fit: "inside", withoutEnlargement: true })
        .webp({ quality: 70 }).toBuffer());
    }
  } catch { return fail("Could not safely process the selected images.", 422); }
  const verdict = await screenSubmission(input, images);
  if (verdict.verdict !== "approved")
    return fail(verdict.verdict === "rejected" ? "Content may violate community guidelines." : "Safety screening unavailable. Nothing published.", verdict.verdict === "rejected" ? 422 : 503);
  // Verified member must own the initial draft. The privileged writer only
  // uploads already-moderated media and promotes that draft to published.
  const draft = await client.from("posts").insert({ ...input, author_id: user.id, status: "draft" }).select("id").single();
  if (draft.error || !draft.data) {
    console.error("native media draft insert failed", { code: draft.error?.code });
    return fail("Could not create a draft.", 409);
  }
  const id = draft.data.id as string;
  const paths: string[] = [];
  try {
    for (const [index, file] of files.entries()) {
      const image = await sharp(new Uint8Array(await file.arrayBuffer()), {
        limitInputPixels: 16000000, animated: false,
      }).rotate().resize({ width: 2400, height: 2400, fit: "inside", withoutEnlargement: true })
        .webp({ quality: 85 }).toBuffer({ resolveWithObject: true });
      if (image.data.length > MEDIA_LIMIT) throw new Error("Processed image too large");
      const path = `${user.id}/${id}/${randomUUID()}.webp`;
      const upload = await writer.storage.from(MEDIA_BUCKET).upload(path, image.data, {
        contentType: "image/webp", upsert: false,
      });
      if (upload.error) throw upload.error;
      paths.push(path);
      const row = await writer.from("post_images").insert({
        post_id: id, path, alt: input.title || "Community photo",
        sort_order: index, width: image.info.width, height: image.info.height,
        user_id: user.id,
      });
      if (row.error) throw row.error;
    }
    const publish = await writer.from("posts").update({ status: "published" })
      .eq("id", id).eq("author_id", user.id).eq("status", "draft").select("id").single();
    if (publish.error || !publish.data) throw new Error("Publish failed");
    return NextResponse.json({ success: true, post_id: id }, { status: 201 });
  } catch (error) {
    console.error("native moderated media publication failed", {
      code: error && typeof error === "object" && "code" in error ? String(error.code) : "unknown",
    });
    const cleanup = await writer.from("posts").delete().eq("id", id).eq("author_id", user.id);
    const removed = paths.length ? await writer.storage.from(MEDIA_BUCKET).remove(paths) : null;
    return fail(cleanup.error || removed?.error
      ? "Publishing failed and cleanup requires support. Nothing was published."
      : "Publishing failed. Please retry.", 500);
  }
}
