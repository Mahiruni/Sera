import "server-only";
import { serverClient } from "../supabase/server";

export async function linkedInState() {
  const client = await serverClient();
  const { data } = await client.auth.getUserIdentities();
  const all = data?.identities ?? [];
  const li = all.find((i) => i.provider === "linkedin_oidc");
  const d = (li?.identity_data ?? {}) as Record<string, unknown>;
  const s = (v: unknown) => (typeof v === "string" && v ? v : null);
  return {
    connected: Boolean(li),
    onlyMethod: all.length < 2,
    name: s(d.full_name) ?? s(d.name),
    picture: s(d.picture) ?? s(d.avatar_url),
    email: s(d.email),
    connectedAt: li?.created_at ?? null,
  };
}
