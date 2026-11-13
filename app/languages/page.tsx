import Link from "next/link";
import { locales } from "@/lib/locales";
import { pageMetadata } from "@/lib/seo";
import { Breadcrumbs } from "@/components/public-site";
export const metadata = pageMetadata(
  "/languages",
  "Language Availability — English, Amharic and Afaan Oromo",
  "Check English, Amharic and Afaan Oromo availability on Sera.",
);
export default function Page() {
  return (
    <div className="container page public-hub">
      <Breadcrumbs items={[{ name: "Languages", path: "/languages" }]} />
      <h1>Your language, with an honest availability status.</h1>
      <p className="subtext">
        Sera supports English, Amharic and Afaan Oromo. Core interface translations are being expanded; some newer screens still use English.
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
        Only these three languages are offered. Translation quality and coverage are reviewed before localized content is advertised as complete.
      </p>
    </div>
  );
}
