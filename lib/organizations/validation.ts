/** Phase 4 organization onboarding validation shared by web and native APIs. */
export type SeraOrganizationKind = "company" | "organization";
export type SeraOrganizationDraft = {
  kind: SeraOrganizationKind;
  name: string;
  slug: string;
  description?: string;
  city?: string;
  industry?: string;
  countryCode: "ET";
};
export type SeraOrganizationValidation =
  | { ok: true; value: SeraOrganizationDraft }
  | { ok: false; errors: Record<string, string> };

export function normalizeOrganizationSlug(input: string): string {
  return input.normalize("NFKC").toLowerCase().trim()
    .replace(/[^a-z0-9]+/g, "-").replace(/^-+|-+$/g, "");
}

export function validateOrganizationDraft(input: unknown): SeraOrganizationValidation {
  if (!input || typeof input !== "object" || Array.isArray(input)) {
    return { ok: false, errors: { form: "Invalid organization details." } };
  }
  const data = input as Record<string, unknown>;
  const errors: Record<string, string> = {};
  const kind = data.kind;
  const name = typeof data.name === "string" ? data.name.trim() : "";
  const slug = typeof data.slug === "string"
    ? normalizeOrganizationSlug(data.slug) : normalizeOrganizationSlug(name);
  const description = typeof data.description === "string" ? data.description.trim() : "";
  const city = typeof data.city === "string" ? data.city.trim() : "";
  const industry = typeof data.industry === "string" ? data.industry.trim() : "";
  if (kind !== "company" && kind !== "organization") errors.kind = "Choose a valid organization type.";
  if (name.length < 2 || name.length > 160) errors.name = "Name must be 2–160 characters.";
  if (slug.length < 3 || slug.length > 80 || !/^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(slug)) {
    errors.slug = "Choose a valid 3–80 character slug.";
  }
  if (description.length > 2000) errors.description = "Description must be at most 2000 characters.";
  if (city.length > 120) errors.city = "City must be at most 120 characters.";
  if (industry.length > 120) errors.industry = "Industry must be at most 120 characters.";
  if (Object.keys(errors).length) return { ok: false, errors };
  return { ok: true, value: {
    kind: kind as SeraOrganizationKind, name, slug,
    description, city, industry, countryCode: "ET"
  }};
}
