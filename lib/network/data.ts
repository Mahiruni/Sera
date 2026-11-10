import "server-only";
import { ASSET_BUCKET, ownedPath } from "@/lib/account/assets";
import { serverClient } from "@/lib/supabase/server";
import type { MemberCard, OpportunityCard } from "@/components/network/shared";
import { sanitizeDiscoveryQuery, type DiscoveryKind } from "./model";
export async function loadNetworkSidebars(userId: string) {
  const db = await serverClient();
  const [people, jobs, assets] = await Promise.all([
    db
      .from("post_authors")
      .select("id,full_name,headline,location")
      .neq("id", userId)
      .order("full_name")
      .limit(4),
    db
      .from("jobs")
      .select("id,title,company,location,work_type")
      .eq("status", "open")
      .order("created_at", { ascending: false })
      .limit(3),
    db
      .from("account_assets")
      .select("avatar_path")
      .eq("user_id", userId)
      .maybeSingle(),
  ]);
  const path = assets.data?.avatar_path as string | null | undefined;
  const avatar = ownedPath(path, userId)
    ? await db.storage.from(ASSET_BUCKET).createSignedUrl(path!, 3600)
    : null;
  return {
    avatarUrl: avatar?.data?.signedUrl || null,
    people: (people.data || []) as MemberCard[],
    jobs: (jobs.data || []) as OpportunityCard[],
    peopleError: !!people.error,
    jobsError: !!jobs.error,
  };
}
export async function discoverDirectory(
  kind: DiscoveryKind,
  query: string,
  page: number,
) {
  const db = await serverClient();
  query = sanitizeDiscoveryQuery(query);
  const start = (page - 1) * 18;
  if (kind === "people") {
    let q = db
      .from("post_authors")
      .select("id,full_name,headline,location")
      .order("full_name")
      .order("id")
      .range(start, start + 18);
    if (query)
      q = q.or(
        `full_name.ilike.%${query}%,headline.ilike.%${query}%,location.ilike.%${query}%`,
      );
    const result = await q;
    if (result.error)
      throw new Error("Professional discovery is temporarily unavailable.");
    return {
      people: (result.data || []).slice(0, 18) as MemberCard[],
      jobs: [] as OpportunityCard[],
      topics: [] as string[],
      hasMore: (result.data?.length || 0) > 18,
    };
  }
  if (kind === "topics") {
    // Topics are derived from published contributions, never private profile fields.
    const result = await db
      .from("posts")
      .select("skills,category")
      .eq("status", "published")
      .order("published_at", { ascending: false })
      .limit(500);
    if (result.error) throw new Error("Topics could not be loaded.");
    const topics = [
      ...new Set(
        (result.data || [])
          .flatMap((row) => [
            ...(row.skills as string[]),
            row.category as string,
          ])
          .filter(Boolean),
      ),
    ]
      .filter(
        (t) =>
          !query || t.toLocaleLowerCase().includes(query.toLocaleLowerCase()),
      )
      .sort((a, b) => a.localeCompare(b));
    return {
      people: [] as MemberCard[],
      jobs: [] as OpportunityCard[],
      topics: topics.slice(start, start + 18),
      hasMore: topics.length > start + 18,
    };
  }
  let q = db
    .from("jobs")
    .select("id,title,company,location,work_type")
    .eq("status", "open")
    .order("created_at", { ascending: false })
    .order("id")
    .range(start, start + 18);
  if (query)
    q = q.or(
      `title.ilike.%${query}%,company.ilike.%${query}%,location.ilike.%${query}%`,
    );
  const result = await q;
  if (result.error) throw new Error("Opportunities could not be loaded.");
  return {
    people: [] as MemberCard[],
    jobs: (result.data || []).slice(0, 18) as OpportunityCard[],
    topics: [] as string[],
    hasMore: (result.data?.length || 0) > 18,
  };
}
