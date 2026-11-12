import "server-only";
import { discoverDirectory } from "@/lib/network/data";
import { sanitizeDiscoveryQuery } from "@/lib/network/model";
import { emptySearchFilters, rankSearchRows, type SearchFilters } from "./search-filters";
import { loadSocialFeed } from "@/lib/social/data";

const PREVIEW_LIMIT = 3;

// Keep requests user-scoped: existing directory/feed readers enforce the same
// database RLS and session rules as the rest of SERA. Never use an admin client.
async function available<T>(read: () => Promise<T>): Promise<T | null> {
  try {
    return await read();
  } catch {
    return null;
  }
}

export async function loadUniversalSearch(rawQuery: string, filters: SearchFilters = emptySearchFilters) {
  const query = sanitizeDiscoveryQuery(rawQuery);
  if (!query && !Object.values(filters).some(Boolean)) return null;
  const [people, jobs, posts, projects, topics] = await Promise.all([
    available(() => discoverDirectory("people", query, 1, filters)),
    available(() => discoverDirectory("jobs", query, 1, filters)),
    available(() => loadSocialFeed({ query, excludeKind: "project", filters })),
    available(() => loadSocialFeed({ query, kind: "project", filters })),
    available(() => discoverDirectory("topics", query, 1, filters)),
  ]);
  // Companies are derived from the same visible open-job directory. There is
  // no separate unrestricted company index or cross-user result cache.
  const companies = [...new Map((jobs?.jobs || []).map(job => [job.company.trim().toLocaleLowerCase(), job])).values()];
  return {
    people: rankSearchRows(people?.people || [],query,p=>p.full_name).slice(0,PREVIEW_LIMIT),
    jobs: rankSearchRows(jobs?.jobs || [],query,j=>j.title).slice(0,PREVIEW_LIMIT),
    posts: rankSearchRows(posts?.posts || [],query,p=>p.title).slice(0,PREVIEW_LIMIT),
    projects: rankSearchRows(projects?.posts || [],query,p=>p.title).slice(0,PREVIEW_LIMIT),
    companies: rankSearchRows(companies,query,j=>j.company).slice(0, PREVIEW_LIMIT),
    topics: topics?.topics.slice(0, PREVIEW_LIMIT) || [],
    unavailable: [people, jobs, posts, projects, topics].some(value => value === null),
  };
}
