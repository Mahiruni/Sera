import { memberCards } from "@/lib/activity/members";
import "server-only";
import { ASSET_BUCKET, ownedPath } from "@/lib/account/assets";
import { serverClient } from "@/lib/supabase/server";
import type { MemberCard, OpportunityCard } from "@/components/network/shared";
import { sanitizeDiscoveryQuery, type DiscoveryKind } from "./model";
import { emptySearchFilters, categoryFilters, searchSince, type SearchFilters } from "./search-filters";
async function withPhotos(people: MemberCard[]) {
  const cards = await memberCards(people.map((p) => p.id));
  return people.map((p) => ({ ...p, photo: cards.get(p.id)?.photo }));
}
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
    people: await withPhotos(people.data || []),
    jobs: (jobs.data || []) as OpportunityCard[],
    peopleError: !!people.error,
    jobsError: !!jobs.error,
  };
}
export async function discoverDirectory(
  kind: DiscoveryKind,
  query: string,
  page: number,
  filters: SearchFilters = emptySearchFilters,
) {
  const db = await serverClient();
  query = sanitizeDiscoveryQuery(query);
  filters = categoryFilters(kind, filters);
  const since = searchSince(filters.days);
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
    if (filters.location) q = q.ilike("location", "%"+filters.location+"%");
    const result = await q;
    if (result.error)
      throw new Error("Professional discovery is temporarily unavailable.");
    return {
      people: await withPhotos((result.data || []).slice(0, 18)),
      jobs: [] as OpportunityCard[],
      topics: [] as string[],
      hasMore: (result.data?.length || 0) > 18,
    };
  }
  if (kind === "topics") {
    // Topics are derived from published contributions, never private profile fields.
    let topicQuery = db
      .from("posts")
      .select("skills,category")
      .eq("status", "published")
      .order("published_at", { ascending: false })
      .limit(500);
    if (since) topicQuery = topicQuery.gte("published_at", since);
    if (filters.skill) topicQuery = topicQuery.contains("skills",[filters.skill]);
    const result = await topicQuery;
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
  if (filters.location) q = q.ilike("location", "%"+filters.location+"%");
  if (filters.work) q = q.eq("work_type", filters.work);
  if (since) q = q.gte("created_at", since);
  const result = await q;
  if (result.error) throw new Error("Opportunities could not be loaded.");
  return {
    people: [] as MemberCard[],
    jobs: (result.data || []).slice(0, 18) as OpportunityCard[],
    topics: [] as string[],
    hasMore: (result.data?.length || 0) > 18,
  };
}
