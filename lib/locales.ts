const allLocales = [
  {
    code: "en",
    name: "English",
    native: "English",
    status: "available",
    dir: "ltr",
  },
  {
    code: "am",
    name: "Amharic",
    native: "አማርኛ",
    status: "available",
    dir: "ltr",
  },
  {
    code: "om",
    name: "Afaan Oromo",
    native: "Afaan Oromo",
    status: "in-progress",
    dir: "ltr",
  },
  {
    code: "ti",
    name: "Tigrinya",
    native: "ትግርኛ",
    status: "in-progress",
    dir: "ltr",
  },
  {
    code: "ar",
    name: "Arabic",
    native: "العربية",
    status: "in-progress",
    dir: "rtl",
  },
  {
    code: "aa",
    name: "Afar",
    native: "Qafar af",
    status: "in-progress",
    dir: "ltr",
  },
  {
    code: "so",
    name: "Somali",
    native: "Soomaali",
    status: "in-progress",
    dir: "ltr",
  },
] as const;
export type LocaleCode = (typeof allLocales)[number]["code"];
export const locales = allLocales.filter(l => ["en","am","om"].includes(l.code));
export const isLocale = (value: unknown): value is LocaleCode =>
  typeof value === "string" && locales.some((l) => l.code === value);
export const contentLanguage = (locale: LocaleCode): "en" | "am" =>
  locale === "am" ? "am" : "en";
// Content language describes actual translated page copy, not the interface preference.
export function pageLanguage(path: string, preference: LocaleCode): LocaleCode {
  if (path === "/am") return "am";
  if (path === "/om" || path === "/ar" || path === "/en") return "en";
  if (["/","/about","/how-it-works","/help","/contact","/privacy","/terms","/opportunities","/education","/institutions","/guides","/languages"].includes(path)||path.startsWith("/guides/")) return "en";
  return contentLanguage(preference);
}
