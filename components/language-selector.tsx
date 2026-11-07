"use client";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { Languages } from "lucide-react";
import { useSera } from "./store";
import { locales, type LocaleCode } from "@/lib/locales";
export function LanguageSelector({ onNavigate }: { onNavigate?: () => void }) {
  const { locale, setLang, t, lang } = useSera();
  const pathname = usePathname();
  const router = useRouter();
  return (
    <div className="language-picker">
      <label htmlFor="sera-language">
        <Languages size={17} />
        {t("language")}
      </label>
      <select
        id="sera-language"
        value={locale}
        onChange={(e) => {
          const next = e.target.value as LocaleCode;
          setLang(next);
          onNavigate?.();
          if (
            [
              "/",
              "/am",
              "/en",
              "/om",
              "/ti",
              "/ar",
              "/aa",
              "/so",
              "/education",
              "/institutions",
              "/opportunities",
              "/languages",
              "/about",
              "/help",
              "/contact",
              "/privacy",
              "/terms",
              "/how-it-works",
            ].includes(pathname) ||
            pathname.startsWith("/guides")
          )
            router.push(next === "en" ? "/" : `/${next}`);
        }}
      >
        {locales.map((item) => (
          <option key={item.code} value={item.code}>
            {item.native} · {item.name}
            {item.status === "in-progress" ? " — in progress" : ""}
          </option>
        ))}
      </select>
      <Link href="/languages">
        {lang === "am" ? "ስለ ቋንቋ ድጋፍ" : "Language availability"}
      </Link>
    </div>
  );
}
export function TranslationNotice() {
  const { locale } = useSera();
  const current = locales.find((item) => item.code === locale)!;
  if (current.status === "available") return null;
  return (
    <div className="translation-notice" lang="en" dir="ltr">
      {current.name} is selected. Full translation is in progress; app content
      is currently shown in English.{" "}
      <Link href={`/${locale}`}>View language status</Link>
    </div>
  );
}
