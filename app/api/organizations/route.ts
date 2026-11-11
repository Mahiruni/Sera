import { NextRequest, NextResponse } from "next/server";
import { currentUser, serverClient, authConfigured } from "@/lib/supabase/server";
import { validateOrganizationDraft } from "@/lib/organizations/validation";

export const runtime = "nodejs";
const fail = (error: string, status: number) => NextResponse.json({ error }, { status });

export async function POST(req: NextRequest) {
  if (req.headers.get("origin") !== new URL(req.url).origin) return fail("Request not allowed.", 403);
  if (!authConfigured()) return fail("Organization registration is unavailable.", 503);
  const user = await currentUser();
  if (!user?.email_confirmed_at) return fail("Please sign in and verify your email.", 401);
  const length = Number(req.headers.get("content-length") || "0");
  if (length > 8192) return fail("Organization details are too large.", 413);
  let input: unknown;
  try { input = await req.json(); } catch { return fail("Invalid JSON.", 400); }
  const email = typeof input === "object" && input !== null && !Array.isArray(input) && "contactEmail" in input && typeof input.contactEmail === "string"
    ? input.contactEmail.trim().toLowerCase() : "";
  const accountEmail = user.email?.trim().toLowerCase();
  if (!accountEmail || email !== accountEmail) return NextResponse.json({ errors: { contactEmail: "Use the confirmed email address of your signed-in SERA account." } }, { status: 422 });
  const result = validateOrganizationDraft(input);
  if (!result.ok) return NextResponse.json({ errors: result.errors }, { status: 422 });
  const { kind, name, slug, description, city, industry } = result.value;
  const client = await serverClient();
  const { data, error } = await client.rpc("sera_create_organization", {
    p_kind: kind, p_name: name, p_slug: slug,
    p_description: description || null, p_city: city || null, p_industry: industry || null,
  });
  if (error) {
    if (error.code === "23505") return fail("That organization address is already taken.", 409);
    return fail("Organization registration failed. Please try again.", 500);
  }
  return NextResponse.json({ organizationId: data, slug }, { status: 201 });
}
