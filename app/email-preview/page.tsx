import { renderEmail } from "@/lib/emails/render";
import { pageMetadata, siteOrigin } from "@/lib/seo";
export const metadata = pageMetadata(
  "/email-preview",
  "Account email previews",
  "Sample email designs only. These previews do not demonstrate actual inbox delivery.",
);
export default function Page() {
  return (
    <div className="container page">
      <h1>A warm welcome, thoughtfully delivered.</h1>
      <p className="subtext">
        Design previews with fictional details. Email delivery is not activated
        by this preview. Amharic copy is a draft awaiting native review; five
        other language preferences use English fallback.
      </p>
      <div className="email-preview-grid">
        {[
          {
            kind: "welcome" as const,
            locale: "en",
            label: "English · welcome design",
          },
          {
            kind: "confirmation" as const,
            locale: "am",
            label: "Amharic · verification draft",
          },
        ].map(({ kind, locale, label }) => (
          <section key={locale}>
            <h2>{label}</h2>
            <iframe
              title={label}
              sandbox=""
              srcDoc={
                renderEmail({
                  kind,
                  locale,
                  name: locale === "am" ? "ማሂ" : "Mahi",
                  origin: siteOrigin,
                  actionUrl: `${siteOrigin}/onboarding`,
                }).html
              }
            />
          </section>
        ))}
      </div>
    </div>
  );
}
