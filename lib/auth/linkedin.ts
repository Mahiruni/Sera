"use server";
import { randomUUID } from "node:crypto";
import { redirect } from "next/navigation";
import { revalidatePath } from "next/cache";
import sharp from "sharp";
import { authConfigured, currentUser, serverClient } from "../supabase/server";
import { siteOrigin } from "../seo";
import type { ActionState } from "./actions";

const PROVIDER = "linkedin_oidc";
const BUCKET = "sera-account-private";
const CALLBACK = `${siteOrigin}/auth/callback?next=/settings/linkedin`;
type Client = Awaited<ReturnType<typeof serverClient>>;

async function identities() {
  const client = await serverClient();
  const { data } = await client.auth.getUserIdentities();
  const all = data?.identities ?? [];
  return { client, all, linkedin: all.find((i) => i.provider === PROVIDER) };
}

/** Sign in / register with LinkedIn (Supabase handles state, nonce, PKCE, linking). */
export async function continueWithLinkedIn(
  _previous: ActionState,
  _data: FormData,
): Promise<ActionState> {
  if (!authConfigured())
    return { message: "Account services are not configured yet." };
  if (process.env.SERA_ACCOUNT_REGISTRATION !== "enabled")
    return {
      message:
        "Account access with LinkedIn opens together with registration. Please try again later.",
    };
  const client = await serverClient();
  const { data, error } = await client.auth.signInWithOAuth({
    provider: PROVIDER,
    options: { redirectTo: CALLBACK },
  });
  if (error || !data.url)
    return { message: "LinkedIn sign-in is unavailable right now." };
  redirect(data.url);
}

/** Signed-in user connects LinkedIn to the existing account. */
export async function connectLinkedIn(): Promise<void> {
  const user = await currentUser();
  if (!user || !user.email_confirmed_at) redirect("/sign-in");
  const client = await serverClient();
  const { data, error } = await client.auth.linkIdentity({
    provider: PROVIDER,
    options: { redirectTo: CALLBACK },
  });
  if (error || !data.url) redirect("/settings/linkedin?error=connect");
  redirect(data.url);
}

export async function disconnectLinkedIn(
  _previous: ActionState,
  _data: FormData,
): Promise<ActionState> {
  const user = await currentUser();
  if (!user) redirect("/sign-in");
  const { client, all, linkedin } = await identities();
  if (!linkedin) return { message: "LinkedIn is not connected." };
  if (all.length < 2)
    return {
      message:
        "LinkedIn is currently your only sign-in method, so it can't be removed yet.",
    };
  const { error } = await client.auth.unlinkIdentity(linkedin);
  if (error)
    return { message: "LinkedIn could not be disconnected. Please try again." };
  revalidatePath("/settings/linkedin");
  return { success: true, message: "LinkedIn disconnected. Your Sera profile was kept." };
}

async function importAvatar(client: Client, uid: string, url: string) {
  const fail = (message: string) => ({ ok: false as const, message });
  let u: URL;
  try {
    u = new URL(url);
  } catch {
    return fail("LinkedIn did not provide a photo.");
  }
  if (
    u.protocol !== "https:" ||
    !(u.hostname === "licdn.com" || u.hostname.endsWith(".licdn.com"))
  )
    return fail("LinkedIn photo address was not recognised.");
  let out: Buffer;
  try {
    const res = await fetch(u, {
      signal: AbortSignal.timeout(8000),
      redirect: "error",
      cache: "no-store",
    });
    if (!res.ok || !(res.headers.get("content-type") ?? "").startsWith("image/"))
      return fail("LinkedIn photo could not be downloaded.");
    const raw = Buffer.from(await res.arrayBuffer());
    if (raw.length > 5_000_000) return fail("LinkedIn photo is too large.");
    out = await sharp(raw, { limitInputPixels: 40_000_000 })
      .rotate()
      .resize(512, 512, { fit: "cover" })
      .webp({ quality: 82 })
      .toBuffer();
  } catch {
    return fail("LinkedIn photo could not be processed.");
  }
  const { data: assets } = await client
    .from("account_assets")
    .select("avatar_path")
    .eq("user_id", uid)
    .maybeSingle();
  const path = `${uid}/avatar/linkedin-${Date.now()}.webp`;
  const up = await client.storage
    .from(BUCKET)
    .upload(path, out, { contentType: "image/webp", upsert: false });
  if (up.error) return fail("Photo could not be saved.");
  const row = await client
    .from("account_assets")
    .upsert(
      { user_id: uid, avatar_path: path, version: randomUUID() },
      { onConflict: "user_id" },
    );
  if (row.error) {
    await client.storage.from(BUCKET).remove([path]);
    return fail("Photo could not be saved.");
  }
  const old = assets?.avatar_path as string | null | undefined;
  if (old && old !== path) await client.storage.from(BUCKET).remove([old]);
  return { ok: true as const };
}

/** Applies ONLY the items the user ticked. Values come from Supabase, never the form. */
export async function applyLinkedInImport(
  _previous: ActionState,
  data: FormData,
): Promise<ActionState> {
  const user = await currentUser();
  if (!user || !user.email_confirmed_at) redirect("/sign-in");
  const wanted = new Set(data.getAll("field").map(String));
  if (!wanted.size) return { message: "Select at least one item to import." };
  const { client, linkedin } = await identities();
  if (!linkedin) return { message: "LinkedIn is not connected." };
  const d = (linkedin.identity_data ?? {}) as Record<string, unknown>;
  const imported: string[] = [];
  const update: Record<string, unknown> = {};

  if (wanted.has("avatar")) {
    const res = await importAvatar(
      client,
      user.id,
      String(d.picture ?? d.avatar_url ?? ""),
    );
    if (!res.ok) return { message: res.message };
    imported.push("avatar");
  }
  if (wanted.has("full_name")) {
    const name = String(d.full_name ?? d.name ?? "").trim();
    if (name.length < 2 || name.length > 100)
      return { message: "LinkedIn did not provide a usable name." };
    update.full_name = name;
    imported.push("full_name");
  }
  const { data: row, error } = await client
    .from("profiles")
    .update({
      ...update,
      linkedin_imported_at: new Date().toISOString(),
      linkedin_imported_fields: imported,
    })
    .eq("id", user.id)
    .select("onboarded_at")
    .single();
  if (error) return { message: "Profile could not be updated. Please try again." };
  if (update.full_name)
    await client.auth.updateUser({ data: { full_name: update.full_name } });
  revalidatePath("/", "layout");
  redirect(row.onboarded_at ? "/settings/linkedin?imported=1" : "/onboarding");
}
