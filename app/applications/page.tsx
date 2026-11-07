import Link from "next/link";
import { requiredProfile } from "@/lib/auth/profile";
import { serverClient } from "@/lib/supabase/server";
import { type LiveApplication } from "@/lib/recruitment/data";
import { MarketNav, Empty } from "@/components/recruitment/ui";
import { pageMetadata } from "@/lib/seo";
export const metadata = pageMetadata(
  "/applications",
  "My applications",
  "Track your private applications and conversations.",
);
export default async function Page() {
  const { user } = await requiredProfile();
  const c = await serverClient();
  const { data, error } = await c
    .from("applications")
    .select("*,jobs(id,title,company,employer_id,status)")
    .eq("candidate_id", user.id)
    .order("created_at", { ascending: false })
    .limit(100);
  const apps = (data || []) as unknown as LiveApplication[];
  return (
    <div className="container page live-market">
      <p className="eyebrow">SERA / YOUR NEXT MOVE</p>
      <h1>Every introduction, in one place.</h1>
      <MarketNav />
      {error ? (
        <Empty title="Applications could not be loaded">
          <p>Please refresh.</p>
        </Empty>
      ) : apps.length ? (
        <div className="live-list">
          {apps.map((a) => (
            <Link
              className="panel live-row"
              href={"/applications/" + a.id}
              key={a.id}
            >
              <div>
                <p>{a.jobs?.company}</p>
                <h2>{a.jobs?.title || "Job unavailable"}</h2>
                <p>
                  Applied {new Date(a.created_at).toLocaleDateString("en-GB")}
                </p>
              </div>
              <span className="live-badge">{a.stage} →</span>
            </Link>
          ))}
        </div>
      ) : (
        <Empty title="Your next introduction is waiting">
          <p>Explore open roles and send a thoughtful application.</p>
          <Link className="inline-link" href="/jobs">
            Find jobs →
          </Link>
        </Empty>
      )}
    </div>
  );
}
