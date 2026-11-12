import {readAudience,type PostAudience} from "@/lib/social/audience";
import { postKinds, type PostKind } from "@/lib/social/validation";

export type SeraPostDraft = {
  kind: PostKind;
  audience?:PostAudience;
  body: string;
  title: string;
  quoteSource: string;
  category: string;
  location: string;
  skills: string;
  projectUrl: string;
  showHeadline: boolean;
  detailsOpen: boolean;
  savedAt: number;
};

const text = (value: unknown, limit: number) =>
  typeof value === "string" ? value.slice(0, limit) : "";
const validKind = (value: unknown): value is PostKind =>
  typeof value === "string" && postKinds.includes(value as PostKind);

export function seraDraftKey(userId: string, postId?: string): string {
  // Account-scoped: no drafts from a different signed-in member on a shared browser.
  return "sera:post-draft:v3:" + userId + ":" + (postId || "new");
}

export function saveSeraDraft(key: string, value: SeraPostDraft): boolean {
  try {
    window.localStorage.setItem(key, JSON.stringify({ ...value, savedAt: Date.now() }));
    return true;
  } catch { return false; }
}

export function restoreSeraDraft(key: string): SeraPostDraft | null {
  try {
    const raw = window.localStorage.getItem(key);
    if (!raw) return null;
    const parsed: unknown = JSON.parse(raw);
    if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) return null;
    const d = parsed as Record<string, unknown>;
    if (!validKind(d.kind) || typeof d.savedAt !== "number" ||
        d.savedAt <= 0 || d.savedAt > Date.now() + 60_000) return null;
    // Text-only drafts expire after 30 days to reduce retained private content.
    if (Date.now() - d.savedAt > 30 * 86400_000) {
      window.localStorage.removeItem(key);
      return null;
    }
    return {
      kind: d.kind,
      audience:readAudience(d.audience)||"only_me",
      body: text(d.body, 10000),
      title: text(d.title, 140),
      quoteSource: text(d.quoteSource, 120),
      category: text(d.category, 60),
      location: text(d.location, 120),
      skills: text(d.skills, 400),
      projectUrl: text(d.projectUrl, 500),
      showHeadline: d.showHeadline === true,
      detailsOpen: d.detailsOpen === true,
      savedAt: d.savedAt,
    };
  } catch { return null; }
}

/** Enables the wrapper's Close, Escape and backdrop dismissal to flush drafts. */
export function draftFromSeraForm(form: HTMLFormElement, kindFallback: PostKind): SeraPostDraft {
  const data = new FormData(form);
  const read = (name: string, limit: number) => text(data.get(name), limit);
  const currentKind = data.get("kind");
  return {
    kind: validKind(currentKind) ? currentKind : kindFallback,
    audience:readAudience(data.get("audience"))||"only_me",
    body: read("body", 10000),
    title: read("title", 140),
    quoteSource: read("quote_source", 120),
    category: read("category", 60),
    location: read("location", 120),
    skills: read("skills", 400),
    projectUrl: read("project_url", 500),
    showHeadline: form.querySelector<HTMLInputElement>('input[name="title"]')?.type !== "hidden",
    detailsOpen: form.querySelector<HTMLButtonElement>('button[aria-expanded]')?.getAttribute("aria-expanded") === "true",
    savedAt: Date.now(),
  };
}
