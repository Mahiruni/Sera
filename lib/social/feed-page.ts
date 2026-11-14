import "server-only";
import type { SupabaseClient } from "@supabase/supabase-js";

type FeedRow = Record<string, unknown>;

/**
 * The authenticated page query shared by the web feed and the native feed API.
 * RLS stays enabled for both callers; the fallback only verifies/retries the
 * same viewer-scoped query when an empty response conflicts with a visible head.
 */
export async function querySocialFeedPage(
  db: SupabaseClient,
  options: { offset: number; pageSize: number; personalized?: boolean },
): Promise<{ rows: FeedRow[]; error: Error | null }> {
  const { offset, pageSize, personalized = false } = options;
  const latest = () =>
    db
      .from("posts")
      .select("*")
      .eq("status", "published")
      .order("published_at", { ascending: false })
      .order("id", { ascending: false })
      .range(offset, offset + pageSize);
  const load = () =>
    personalized
      ? db.rpc("sera_ranked_feed", {
          p_offset: offset,
          p_limit: pageSize + 1,
        })
      : latest();

  let result = await load();
  let rows = (result.data || []) as FeedRow[];

  // The ranking RPC can fail or return no rows while published posts exist.
  // Keep For you useful by falling back to the same viewer-scoped Latest query.
  if (personalized && offset === 0 && (result.error || rows.length === 0)) {
    result = await latest();
    if (result.error) return { rows: [], error: result.error };
    rows = (result.data || []) as FeedRow[];
  }

  if (result.error) return { rows: [], error: result.error };

  // Only show the quiet-feed state after authenticated exact-count checks
  // confirm there are no published posts visible under this viewer's RLS rules.
  if (offset === 0 && rows.length === 0) {
    const countVisiblePosts = () =>
      db.from("posts").select("id", { count: "exact", head: true }).eq("status", "published");

    const visible = await countVisiblePosts();
    if (visible.error) return { rows: [], error: visible.error };
    if (visible.count === null) {
      return { rows: [], error: new Error("Unable to verify whether published posts are available.") };
    }
    if (visible.count === 0) return { rows: [], error: null };

    // A nonzero count means the page query was incomplete; retry the same RLS query.
    result = await latest();
    if (result.error) return { rows: [], error: result.error };
    rows = (result.data || []) as FeedRow[];
    if (rows.length === 0) {
      const confirmed = await countVisiblePosts();
      if (confirmed.error) return { rows: [], error: confirmed.error };
      if (confirmed.count === 0) return { rows: [], error: null };
      return { rows: [], error: new Error("Published posts are available but could not be loaded.") };
    }
  }

  return { rows, error: null };
}
