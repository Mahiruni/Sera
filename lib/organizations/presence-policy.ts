/** An individual can display recent activity around social content.
 * A company must not expose its availability on a public profile or post.
 * Company availability is a separate, optional inbox-only signal.
 */
export type PresenceSubject = "person" | "company" | "organization";
export type PresenceSurface = "feed" | "profile" | "inbox";
export function canShowPresence(subject: PresenceSubject, surface: PresenceSurface): boolean {
  return subject === "person" || surface === "inbox";
}
export type CompanyInboxAvailability = "available" | "away" | "offline" | "hidden";
export function companyInboxAvailability(
  lastStaffInboxHeartbeat: string | null,
  enabled: boolean,
  now: Date = new Date(),
): CompanyInboxAvailability {
  if (!enabled) return "hidden";
  if (!lastStaffInboxHeartbeat) return "offline";
  const last = Date.parse(lastStaffInboxHeartbeat);
  if (!Number.isFinite(last) || last > now.getTime() + 5000) return "offline";
  const age = now.getTime() - last;
  if (age < 90000) return "available";
  if (age < 15 * 60000) return "away";
  return "offline";
}
export const companyAvailabilityLabel: Record<CompanyInboxAvailability, string> = {
  available: "Company team available",
  away: "Company team recently active",
  offline: "Company team currently away",
  hidden: "Availability not shared",
};
