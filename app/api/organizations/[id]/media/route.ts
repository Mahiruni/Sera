import { NextRequest, NextResponse } from "next/server";
import sharp from "sharp";
import { randomUUID } from "node:crypto";
import { currentUser, serverClient } from "@/lib/supabase/server";
import { requireOrganizationMembership } from "@/lib/organizations/membership";
export const runtime = "nodejs";
export const maxDuration = 60;
const BUCKET = "sera-company-brand";
const fail = (error: string, status = 400) => NextResponse.json({ error }, { status });
export async function POST(req: NextRequest, { params }: { params: Promise<{ id: string }> }) {
  if (req.headers.get("origin") !== new URL(req.url).origin) return fail("Request not allowed.", 403);
  const user = await currentUser();
  if (!user?.email_confirmed_at) return fail("Please sign in.", 401);
  const { id } = await params;
  const member = await requireOrganizationMembership(id);
  if (!member || !["owner", "admin"].includes(member.role)) return fail("Only company administrators can change branding.", 403);
  const length = Number(req.headers.get("content-length") || "0");
  if (length > 4 * 1024 * 1024) return fail("Image must be under 3 MB.", 413);
  let form: FormData;
  try { form = await req.formData(); } catch { return fail("Invalid upload."); }
  const kind = form.get("kind");
  const file = form.get("file");
  if (kind !== "logo" && kind !== "cover") return fail("Invalid image type.");
  if (!(file instanceof File) || file.size === 0 || file.size > 3 * 1024 * 1024 ||
      !["image/png", "image/jpeg", "image/webp"].includes(file.type)) return fail("Use a JPG, PNG or WebP image below 3 MB.");
  let image: Buffer;
  try {
    const original = Buffer.from(await file.arrayBuffer());
    image = await sharp(original, { limitInputPixels: 20000000 })
      .rotate().resize(kind === "logo" ? 600 : 1800, kind === "logo" ? 600 : 675,
        { fit: kind === "logo" ? "contain" : "cover", background: { r: 255, g: 255, b: 255, alpha: 0 } })
      .webp({ quality: 84, effort: 4 }).toBuffer();
  } catch { return fail("Image could not be processed.", 422); }
  if (image.byteLength > 3 * 1024 * 1024) return fail("Processed image is too large.", 413);
  const path = `${id}/${kind}/${randomUUID()}.webp`;
  const db = await serverClient();
  const uploaded = await db.storage.from(BUCKET).upload(path, image, { contentType: "image/webp", upsert: false });
  if (uploaded.error) return fail("Could not upload company branding.", 500);
  const field = kind === "logo" ? "logo_path" : "cover_path";
  const previous = await db.from("sera_organization_details").select("logo_path,cover_path").eq("organization_id", id).maybeSingle();
  if (previous.error) {
    await db.storage.from(BUCKET).remove([path]);
    return fail("Company branding could not be loaded.", 500);
  }
  const update = { [field]: path, updated_at: new Date().toISOString() };
  const saved = previous.data
    ? await db.from("sera_organization_details").update(update).eq("organization_id", id)
    : await db.from("sera_organization_details").insert({ organization_id: id, ...update });
  if (saved.error) {
    await db.storage.from(BUCKET).remove([path]);
    return fail("Could not save your company branding.", 500);
  }
  const old = previous.data?.[field];
  if (typeof old === "string" && old.startsWith(`${id}/${kind}/`) && old !== path)
    await db.storage.from(BUCKET).remove([old]);
  return NextResponse.json({ success: true, url: db.storage.from(BUCKET).getPublicUrl(path).data.publicUrl });
}
