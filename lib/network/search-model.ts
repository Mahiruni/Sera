import { sanitizeDiscoveryQuery } from "./model";
export type SearchSuggestion = { id: string; label: string; kind: "People" | "Jobs" | "Posts" | "Projects" };
export const RECENT_LIMIT = 8;
export function recentSearches(values: unknown): string[] {
  if (!Array.isArray(values)) return [];
  const seen = new Set<string>();
  return values.flatMap(value => {
    const q = sanitizeDiscoveryQuery(value);
    const key = q.toLocaleLowerCase();
    if (!q || seen.has(key)) return [];
    seen.add(key);
    return [q];
  }).slice(0, RECENT_LIMIT);
}
export function rememberSearch(values: unknown, query: string) {
  return recentSearches([query, ...recentSearches(values)]);
}
export function rankSuggestions(items: SearchSuggestion[], query: string) {
  const q = sanitizeDiscoveryQuery(query).toLocaleLowerCase();
  const rank = (label: string) => {
    const s = label.toLocaleLowerCase();
    return s === q ? 0 : s.startsWith(q) ? 1 : s.includes(q) ? 2 : 3;
  };
  return [...items].sort((a,b) => rank(a.label)-rank(b.label) || a.label.localeCompare(b.label) || a.id.localeCompare(b.id)).slice(0, 8);
}
