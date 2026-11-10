export const postKinds = [
  "idea",
  "photos",
  "quote",
  "talent",
  "project",
  "achievement",
  "question",
] as const;
export type PostKind = (typeof postKinds)[number];
export const uuid = (value: unknown): value is string =>
  typeof value === "string" &&
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value);
export const MEDIA_BUCKET = "sera-post-media";
export const MEDIA_LIMIT = 3 * 1024 * 1024;
export const MEDIA_COUNT = 4;
export function readPost(data: FormData) {
  const text = (key: string) =>
    typeof data.get(key) === "string" ? (data.get(key) as string).trim() : "";
  const kind = text("kind") as PostKind;
  const title = text("title"),
    body = text("body"),
    quote_source = text("quote_source");
  const category = text("category"),
    location = text("location");
  const skills = [
    ...new Set(
      text("skills")
        .split(",")
        .map((s) => s.trim())
        .filter(Boolean),
    ),
  ];
  const project_url = text("project_url") || null;
  if (
    !postKinds.includes(kind) ||
    title.length > 140 ||
    body.length > 10000 ||
    quote_source.length > 120 ||
    category.length > 60 ||
    location.length > 120 ||
    skills.length > 10 ||
    skills.join(",").length > 400
  )
    return null;
  if (
    kind === "quote"
      ? body.length < 3 || body.length > 600
      : title.length < 3 || (kind !== "photos" && !body)
  )
    return null;
  if (project_url) {
    try {
      const url = new URL(project_url);
      if (
        project_url.length > 500 ||
        url.protocol !== "https:" ||
        !url.hostname ||
        url.username ||
        url.password ||
        /\s/.test(project_url)
      )
        return null;
    } catch {
      return null;
    }
  }
  // Identity, status, timestamps and counters are never accepted from the client.
  return {
    kind,
    title,
    body,
    quote_source,
    category,
    location,
    skills,
    project_url,
  };
}
export function readComment(data: FormData) {
  const post_id = data.get("post_id"),
    parent_id = data.get("parent_id") || null;
  const body =
    typeof data.get("body") === "string"
      ? (data.get("body") as string).trim()
      : "";
  return uuid(post_id) &&
    (parent_id === null || uuid(parent_id)) &&
    body.length > 0 &&
    body.length <= 2000
    ? { post_id, parent_id, body }
    : null;
}
export function validMedia(files: File[]) {
  return (
    files.length <= MEDIA_COUNT &&
    files.every(
      (f) =>
        f.size > 0 &&
        ["image/jpeg", "image/png", "image/webp"].includes(f.type),
    ) &&
    files.reduce((sum, f) => sum + f.size, 0) <= MEDIA_LIMIT
  );
}
export function pageNumber(value: unknown) {
  return typeof value === "string" && /^\d{1,4}$/.test(value)
    ? Math.min(1000, Math.max(1, Number(value)))
    : 1;
}
