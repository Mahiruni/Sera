import type { Profile } from "@/components/auth/forms";
export type WorkspaceMode = "candidate" | "employer";
export function profileChecklist(
  profile: Profile,
  assets: { avatar_path?: string | null; cv_path?: string | null } | null,
  mode: WorkspaceMode,
) {
  const entries: [string, boolean][] = [
    ["Name", !!profile.full_name?.trim()],
    ["Professional headline", !!profile.headline?.trim()],
    ["Location", !!profile.location?.trim()],
    ["About you", !!profile.summary?.trim()],
    ["Profile photo", !!assets?.avatar_path],
    ...(mode === "employer"
      ? ([
          ["Organization", !!profile.company_name?.trim()],
          ["Organization role", !!profile.company_role?.trim()],
        ] as [string, boolean][])
      : ([
          ["Skills", !!profile.skills?.trim()],
          ["Experience", !!profile.experience?.trim()],
          ["Education", !!profile.education?.trim()],
          ["CV uploaded", !!assets?.cv_path],
        ] as [string, boolean][])),
  ];
  const complete = entries.filter(([, done]) => done).length;
  return {
    entries,
    complete,
    total: entries.length,
    percent: Math.round((complete / entries.length) * 100),
  };
}
export const stageLabels: Record<string, string> = {
  applied: "Submitted",
  screened: "In review",
  interview: "Interview",
  offer: "Offer",
  rejected: "Not selected",
  withdrawn: "Withdrawn",
};
export function stageLabel(stage: string) {
  return stageLabels[stage] || "Status unavailable";
}
export function pageNumber(value: unknown) {
  return typeof value === "string" && /^\d{1,4}$/.test(value)
    ? Math.max(1, Math.min(1000, Number(value)))
    : 1;
}
export function employerReturn(data: FormData, error = false) {
  if (!data.has("jobsPage"))
    return error ? "/employer?error=status" : "/employer";
  const filter = data.get("filter");
  const status = filter === "open" || filter === "closed" ? filter : "all";
  return `/employer?jobsPage=${pageNumber(data.get("jobsPage"))}&appsPage=${pageNumber(data.get("appsPage"))}&status=${status}${error ? "&error=status" : ""}#postings`;
}
export function activityDate(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? "Date unavailable"
    : new Intl.DateTimeFormat("en-GB", {
        day: "numeric",
        month: "short",
        year: "numeric",
        timeZone: "Africa/Addis_Ababa",
      }).format(date);
}
