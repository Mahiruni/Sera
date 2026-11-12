import "server-only";
import { discoverDirectory } from "@/lib/network/data";
import { sanitizeDiscoveryQuery } from "@/lib/network/model";
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

export async function loadUniversalSearch(rawQuery: string) {
  const query = sanitizeDiscoveryQuery(rawQuery);
  if (!query) return null;
  const [people, jobs, posts, projects, topics] = await Promise.all([
    available(() => discoverDirectory("people", query, 1)),
    available(() => discoverDirectory("jobs", query, 1)),
    available(() => loadSocialFeed({ query, excludeKind: "project" })),
    available(() => loadSocialFeed({ query, kind: "project" })),
    available(() => discoverDirectory("topics", query, 1)),
  ]);
  // Companies are derived from the same visible open-job directory. There is
  // no separate unrestricted company index or cross-user result cache.
  const companies = [...new Map((jobs?.jobs || []).map(job => [job.company.trim().toLocaleLowerCase(), job])).values()];
  return {
    people: people?.people.slice(0, PREVIEW_LIMIT) || [],
    jobs: jobs?.jobs.slice(0, PREVIEW_LIMIT) || [],
    posts: posts?.posts.slice(0, PREVIEW_LIMIT) || [],
    projects: projects?.posts.slice(0, PREVIEW_LIMIT) || [],
    companies: companies.slice(0, PREVIEW_LIMIT),
    topics: topics?.topics.slice(0, PREVIEW_LIMIT) || [],
    unavailable: [people, jobs, posts, projects, topics].some(value => value === null),
  };
}
