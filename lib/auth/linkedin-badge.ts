import "server-only";
import { serverClient } from "@/lib/supabase/server";
import { linkedInState } from "@/lib/auth/linkedin-status";
export type LinkedInBadge = {
  connected: boolean;
  importedAt: string | null;
  importedCount: number;
};
export async function linkedInBadge(
  userId: string,
): Promise<LinkedInBadge | null> {
  try {
    const client = await serverClient();
    const [state, row] = await Promise.all([
      linkedInState(),
      client
        .from("profiles")
        .select("linkedin_imported_at,linkedin_imported_fields")
        .eq("id", userId)
        .single(),
    ]);
    return {
      connected: Boolean(state.connected),
      importedAt: (row.data?.linkedin_imported_at as string | null) ?? null,
      importedCount: (
        (row.data?.linkedin_imported_fields as string[] | null) ?? []
      ).length,
    };
  } catch {
    return null;
  }
}
