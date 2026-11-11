import "server-only";
import type { PostKind } from "@/lib/social/validation";

type ModerationResult = { verdict: "approved" | "rejected" | "review"; reason?: string };
type ModerationResponse = {
  results?: Array<{ flagged?: boolean; categories?: Record<string, boolean>; category_scores?: Record<string, number> }>;
};

/** No public-facing moderation request is made without explicit server configuration. */
export function moderationEnabled() {
  return process.env.SERA_MODERATION_ENABLED === "true";
}
export async function screenSubmission(
  post: { kind: PostKind; title: string; body: string; quote_source: string; category: string; skills: string[] },
  images: Buffer[],
): Promise<ModerationResult> {
  const key = process.env.SERA_MODERATION_API_KEY;
  if (!key) return { verdict: "review", reason: "Moderation provider is not configured." };
  const text = [post.title, post.body, post.quote_source, post.category, ...post.skills].filter(Boolean).join("\n").slice(0, 16000);
  const input: Array<Record<string, string>> = [];
  if (text) input.push({ type: "text", text });
  for (const buffer of images) input.push({ type: "image_url", image_url: { url: "data:image/webp;base64," + buffer.toString("base64") } } as unknown as Record<string,string>);
  if (!input.length) return { verdict: "review", reason: "Empty content." };
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 12000);
  try {
    const response = await fetch("https://api.openai.com/v1/moderations", {
      method: "POST",
      headers: { Authorization: `Bearer ${key}`, "Content-Type": "application/json" },
      body: JSON.stringify({ model: "omni-moderation-latest", input }),
      signal: controller.signal,
      cache: "no-store",
    });
    if (!response.ok) return { verdict: "review", reason: "Moderation service unavailable." };
    const data = await response.json() as ModerationResponse;
    if (!data.results?.length) return { verdict: "review", reason: "No moderation verdict returned." };
    const flagged = data.results.some(r => r.flagged);
    if (!flagged) return { verdict: "approved" };
    // Flagged content is held for review, not automatically deleted.
    const categories = [...new Set(data.results.flatMap(r => Object.entries(r.categories || {}).filter(([,value]) => value).map(([name]) => name)))];
    return { verdict: "rejected", reason: categories.join(", ").slice(0, 200) || "Safety policy" };
  } catch {
    return { verdict: "review", reason: "Moderation service unavailable." };
  } finally {
    clearTimeout(timeout);
  }
}
