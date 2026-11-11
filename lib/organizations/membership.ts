import "server-only";
import { currentUser, serverClient } from "@/lib/supabase/server";

export type OrganizationMembership = {
  organization_id: string;
  role: "owner" | "admin" | "member";
  organization: {
    id: string;
    name: string;
    slug: string;
    kind: "company" | "organization";
    city: string | null;
    verification_status: "unverified" | "pending" | "verified" | "rejected";
  };
};

export async function currentOrganizationMemberships(): Promise<OrganizationMembership[]> {
  const user = await currentUser();
  if (!user?.email_confirmed_at) return [];
  const db = await serverClient();
  const { data, error } = await db
    .from("sera_organization_members")
    .select("organization_id,role,organization:sera_organizations!inner(id,name,slug,kind,city,verification_status)")
    .eq("user_id", user.id)
    .order("joined_at", { ascending: false })
    .limit(100);
  if (error) throw new Error("Organizations are currently unavailable.");
  return (data || []).map((row) => ({
    organization_id: row.organization_id as string,
    role: row.role as OrganizationMembership["role"],
    organization: (Array.isArray(row.organization) ? row.organization[0] : row.organization) as OrganizationMembership["organization"],
  })).filter((row) => row.organization?.id);
}
export async function requireOrganizationMembership(organizationId: string) {
  const user = await currentUser();
  if (!user?.email_confirmed_at) return null;
  if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(organizationId)) return null;
  const db = await serverClient();
  const { data, error } = await db.from("sera_organization_members")
    .select("organization_id,role")
    .eq("organization_id", organizationId)
    .eq("user_id", user.id)
    .maybeSingle();
  if (error || !data) return null;
  return { user, role: data.role as OrganizationMembership["role"] };
}
