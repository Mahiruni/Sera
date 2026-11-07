import Link from "next/link";
import { locales } from "@/lib/locales";
import { pageMetadata } from "@/lib/seo";
import { Breadcrumbs } from "@/components/public-site";
export const metadata = pageMetadata(
  "/languages",
  "Language Availability — Seven Language Preferences",
  "Check English, Amharic, Afaan Oromo, Tigrinya, Arabic, Afar and Somali availability on Sera.et.",
);
export default function Page() {
  return (
    <div className="container page public-hub">
      <Breadcrumbs items={[{ name: "Languages", path: "/languages" }]} />
      <h1>Your language, with an honest availability status.</h1>
      <p className="subtext">
        English and Amharic are available in the app. New Amharic public content
        awaits native editorial review. Five additional languages can be
        selected as preferences; complete translations are not yet published.
        Untranslated app content remains explicitly English.
      </p>
      <div className="locale-grid">
        {locales.map((locale) => (
          <Link
            className="panel"
            key={locale.code}
            href={locale.code === "en" ? "/" : `/${locale.code}`}
          >
            <strong lang={locale.code}>{locale.native}</strong>
            <span>{locale.name}</span>
            <p>
              {locale.status === "available"
                ? locale.code === "am"
                  ? "Available · public translation draft"
                  : "Available"
                : "Translation in progress"}
            </p>
          </Link>
        ))}
      </div>
      <p className="small-note">
        Arabic has a right-to-left availability page. No incomplete translation
        is included in the search sitemap or advertised as a finished localized
        version.
      </p>
    </div>
  );
}
