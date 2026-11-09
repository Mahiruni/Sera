import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { currentUser, serverClient } from "@/lib/supabase/server";
import { linkedInState } from "@/lib/auth/linkedin-status";
import LinkedInPanel from "@/components/auth/LinkedInPanel";

export const metadata: Metadata = { title: "LinkedIn · Sera", robots: { index: false } };
export const dynamic = "force-dynamic";

export default async function LinkedInSettings({
  searchParams,
}: {
  searchParams: Promise<{ imported?: string; error?: string }>;
}) {
  const user = await currentUser();
  if (!user || !user.email_confirmed_at) redirect("/sign-in");
  const sp = await searchParams;
  const client = await serverClient();
  const [state, profile, assets] = await Promise.all([
    linkedInState(),
    client
      .from("profiles")
      .select("full_name,headline,summary,location,skills,experience,education,linkedin_imported_at,linkedin_imported_fields")
      .eq("id", user.id)
      .single(),
    client.from("account_assets").select("avatar_path").eq("user_id", user.id).maybeSingle(),
  ]);
  return (
    <section style={{ maxWidth: 640, margin: "0 auto", padding: "24px 16px 140px" }}>
      <LinkedInPanel
        {...state}
        profileName={profile.data?.full_name ?? ""}
        hasAvatar={Boolean(assets.data?.avatar_path)}
        importedAt={profile.data?.linkedin_imported_at ?? null}
        importedFields={(profile.data?.linkedin_imported_fields as string[] | null) ?? []}
        justImported={sp.imported === "1"}
        connectError={sp.error === "connect"}
        current={{
          headline: profile.data?.headline ?? "",
          summary: profile.data?.summary ?? "",
          location: profile.data?.location ?? "",
          skills: profile.data?.skills ?? "",
          experience: profile.data?.experience ?? "",
          education: profile.data?.education ?? "",
        }}
      />
    </section>
  );
}
