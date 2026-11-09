import { isLocale, type LocaleCode } from "../locales";
export const journeys = ["candidate", "employer"] as const;
export type Journey = (typeof journeys)[number];
export function safeDestination(value: unknown, fallback = "/dashboard") {
  return typeof value === "string" &&
    ["/dashboard", "/onboarding", "/settings", "/reset-password", "/settings/linkedin"].includes(
      value,
    )
    ? value
    : fallback;
}
export function normalizedEmail(value: unknown) {
  const email = typeof value === "string" ? value.trim().toLowerCase() : "";
  return email.length <= 254 && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)
    ? email
    : null;
}
export function passwordIssue(value: unknown) {
  return typeof value !== "string" || value.length < 12 || value.length > 128
    ? "Use a password between 12 and 128 characters."
    : null;
}
export function readRegistration(data: FormData) {
  const email = normalizedEmail(data.get("email"));
  const password = data.get("password");
  const name = String(data.get("full_name") || "").trim();
  const locale = data.get("locale");
  const journey = data.get("journey");
  if (
    data.get("accept_terms") !== "yes" ||
    !email ||
    passwordIssue(password) ||
    name.length < 2 ||
    name.length > 100 ||
    !isLocale(locale) ||
    !journeys.includes(journey as Journey)
  )
    return null;
  return {
    email,
    password: password as string,
    full_name: name,
    locale: locale as LocaleCode,
    journey: journey as Journey,
  };
}
export function readProfile(data: FormData) {
  const full_name = String(data.get("full_name") || "").trim();
  const locale = data.get("locale");
  const journey = data.get("journey");
  const fields = [
    "location",
    "phone",
    "skills",
    "experience",
    "preferred_roles",
    "company_name",
    "company_role",
  ] as const;
  const result: Record<string, string> = {};
  for (const field of fields) {
    const value = String(data.get(field) || "").trim();
    if (value.length > 500) return null;
    result[field] = value;
  }
  if (
    full_name.length < 2 ||
    full_name.length > 100 ||
    !isLocale(locale) ||
    !journeys.includes(journey as Journey)
  )
    return null;
  return { ...result, full_name, locale, journey: journey as Journey };
}
