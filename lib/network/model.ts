export const discoveryKinds = [
  "people",
  "stories",
  "projects",
  "jobs",
  "companies",
  "topics",
] as const;
export type DiscoveryKind = (typeof discoveryKinds)[number];
export function sanitizeDiscoveryQuery(value: unknown) {
  const raw = typeof value === "string" ? value.trim().slice(0, 80) : "";
  return raw
    .replace(/[^\p{L}\p{N}\s-]/gu, " ")
    .replace(/\s+/g, " ")
    .trim();
}
export function discoveryInput(
  params: Record<string, string | string[] | undefined>,
) {
  const query = sanitizeDiscoveryQuery(params.q);
  const kind = discoveryKinds.includes(params.tab as DiscoveryKind)
    ? (params.tab as DiscoveryKind)
    : "people";
  const page =
    typeof params.page === "string" && /^\d{1,4}$/.test(params.page)
      ? Math.min(1000, Math.max(1, Number(params.page)))
      : 1;
  return { query, kind, page };
}
export function profileEssentials(
  profile: {
    full_name?: string;
    headline?: string;
    summary?: string;
    skills?: string;
    location?: string;
    experience?: string;
    education?: string;
  } | null,
) {
  const fields = [
    ["full_name", "Your name"],
    ["headline", "Professional headline"],
    ["summary", "About you"],
    ["skills", "Skills"],
    ["location", "Location"],
    ["experience", "Work experience"],
    ["education", "Education"],
  ] as const;
  const complete = fields.filter(([key]) => Boolean(profile?.[key]?.trim()));
  return {
    percent: Math.round((complete.length / fields.length) * 100),
    next:
      fields.find(([key]) => !profile?.[key]?.trim())?.[1] ||
      "All essentials added",
  };
}
export function skillTopics(skills?: string) {
  return [
    ...new Set(
      (skills || "")
        .split(/[,;\n]/)
        .map((s) => s.trim())
        .filter((s) => s.length > 1 && s.length <= 40),
    ),
  ].slice(0, 6);
}
