"use client";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { Languages } from "lucide-react";
import { useId } from "react";
import { useSera } from "./store";
import { locales, type LocaleCode } from "@/lib/locales";
export function LanguageSelector({
  onNavigate,
  compact = false,
}: {
  onNavigate?: () => void;
  compact?: boolean;
}) {
  const { locale, setLang, t, lang } = useSera();
  const pathname = usePathname();
  const router = useRouter();
  const selectId = useId();
  return (
    <div
      className={
        compact ? "header-control header-language-picker" : "language-picker"
      }
    >
      <label htmlFor={selectId}>
        <Languages size={compact ? 20 : 17} aria-hidden="true" />
        <span className={compact ? "sr-only" : undefined}>{t("language")}</span>
      </label>
      <select
        id={selectId}
        value={locale}
        onChange={(e) => {
          const next = e.target.value as LocaleCode;
          setLang(next);
          onNavigate?.();
          if (["/","/am","/en","/om"].includes(pathname)) router.push(next === "en" ? "/" : `/${next}`);
          else router.refresh();
        }}
      >
        {locales.map((item) => (
          <option key={item.code} value={item.code}>
            {item.native} · {item.name}
            {item.status === "in-progress" ? " — in progress" : ""}
          </option>
        ))}
      </select>
      {!compact && (
        <Link href="/languages">
          {lang === "am" ? "ስለ ቋንቋ ድጋፍ" : "Language availability"}
        </Link>
      )}
    </div>
  );
}
export function TranslationNotice() {
  const { locale } = useSera();
  const current = locales.find((item) => item.code === locale)!;
  if (current.status === "available") return null;
  return (
    <div className="translation-notice" lang="en" dir="ltr">
      {current.name} is selected. Core interface translation is available; some newer screens and public content still use English.{" "}
      <Link href={`/${locale}`}>View language status</Link>
    </div>
  );
}
