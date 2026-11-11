import { NextRequest, NextResponse } from "next/server";
import { currentUser, serverClient } from "@/lib/supabase/server";
import { requireOrganizationMembership } from "@/lib/organizations/membership";

const fail = (error: string, status = 400) => NextResponse.json({ error }, { status });
type Params = { params: Promise<{ id: string }> };
export async function PATCH(req: NextRequest, { params }: Params) {
  if (req.headers.get("origin") !== new URL(req.url).origin) return fail("Request not allowed.", 403);
  const user = await currentUser();
  if (!user?.email_confirmed_at) return fail("Please sign in.", 401);
  const { id } = await params;
  const member = await requireOrganizationMembership(id);
  if (!member || !["owner", "admin"].includes(member.role)) return fail("You cannot edit this company.", 403);
  if (Number(req.headers.get("content-length") || "0") > 8192) return fail("Profile details are too long.", 413);
  let input: unknown;
  try { input = await req.json(); } catch { return fail("Invalid JSON."); }
  if (!input || Array.isArray(input) || typeof input !== "object") return fail("Invalid details.");
  const data = input as Record<string, unknown>;
  const limits = {
    street_address: 300, region: 120, phone_public: 40, email_public: 254,
    website_url: 400, services: 2000, business_hours: 350, linkedin_url: 400,
  } as const;
  const update: Record<string, string | number | null> = {};
  for (const [field, max] of Object.entries(limits)) {
    if (field in data) {
      if (typeof data[field] !== "string" || (data[field] as string).trim().length > max)
        return fail("Invalid " + field.replaceAll("_", " ") + ".");
      update[field] = (data[field] as string).trim();
    }
  }
  if ("founded_year" in data) {
    if (data.founded_year !== null && (!Number.isInteger(data.founded_year) || Number(data.founded_year) < 1800 || Number(data.founded_year) > new Date().getFullYear()))
      return fail("Invalid founding year.");
    update.founded_year = data.founded_year as number | null;
  }
  if ("company_size" in data) {
    if (typeof data.company_size !== "string" || !["", "1-10", "11-50", "51-200", "201-500", "501-1000", "1000+"].includes(data.company_size))
      return fail("Invalid company size.");
    update.company_size = data.company_size;
  }
  for (const key of ["website_url", "linkedin_url"] as const) {
    const value = update[key];
    if (typeof value === "string" && value) {
      try { const u = new URL(value); if (u.protocol !== "https:" || u.username || u.password) return fail("Use a secure HTTPS URL."); }
      catch { return fail("Use a valid HTTPS URL."); }
    }
  }
  if (typeof update.email_public === "string" && update.email_public &&
      !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(update.email_public)) return fail("Invalid public email.");
  const db = await serverClient();
  const existing = await db.from("sera_organization_details")
    .select("organization_id").eq("organization_id", id).maybeSingle();
  if (existing.error) return fail("Company details could not be loaded.", 500);
  const payload = { ...update, updated_at: new Date().toISOString() };
  const result = existing.data
    ? await db.from("sera_organization_details").update(payload).eq("organization_id", id)
    : await db.from("sera_organization_details").insert({ organization_id: id, ...payload });
  if (result.error) return fail("Company details could not be saved.", 500);
  return NextResponse.json({ success: true });
}
