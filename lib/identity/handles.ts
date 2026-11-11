/** Public SERA handles are distinct from display names and from URL separators. */
export const HANDLE_CHANGE_COOLDOWN_DAYS = 60;
export type HandleKind = "person" | "company";

export function validatePublicHandle(value: unknown): string | null {
  if (typeof value !== "string") return null;
  const handle = value.trim().normalize("NFKC").toLowerCase();
  // Letters, single interior underscores/hyphens; no numbers, slash, or other symbols.
  return /^[a-z]+(?:[_-][a-z]+)*$/.test(handle) && handle.length >= 3 && handle.length <= 40
    ? handle : null;
}

export function profileLink(kind: HandleKind, handle: string): string | null {
  const safe = validatePublicHandle(handle);
  return safe ? (kind === "company" ? "/business/" : "/u/") + safe : null;
}

export function nextHandleChangeAt(lastChangedAt: string | null): Date | null {
  if (!lastChangedAt) return null;
  const timestamp = new Date(lastChangedAt);
  if (!Number.isFinite(timestamp.getTime())) return null;
  return new Date(timestamp.getTime() + HANDLE_CHANGE_COOLDOWN_DAYS * 86400000);
}

export function canChangeHandle(lastChangedAt: string | null, now: Date = new Date()): boolean {
  const next = nextHandleChangeAt(lastChangedAt);
  return !next || now.getTime() >= next.getTime();
}
