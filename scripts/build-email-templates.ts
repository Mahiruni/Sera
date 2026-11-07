import { mkdir, writeFile } from "node:fs/promises";
import {
  emailKinds,
  renderEmail,
  supportedEmailLocales,
} from "../lib/emails/render";
async function main() {
  const origin = "https://sera-dusky-seven.vercel.app";
  await mkdir("supabase/templates", { recursive: true });
  await mkdir("docs/emails", { recursive: true });
  for (const kind of emailKinds) {
    const combined: { locale: string; html: string; subject: string }[] = [];
    const path =
      kind === "confirmation"
        ? "/auth/confirm?token_hash={{ .TokenHash }}&type=email"
        : kind === "recovery"
          ? "/auth/confirm?token_hash={{ .TokenHash }}&type=recovery"
          : kind === "welcome"
            ? "/onboarding"
            : "/settings";
    const actionUrl = `{{ .SiteURL }}${path}`;
    for (const locale of supportedEmailLocales) {
      const template = renderEmail({
        kind,
        locale,
        name: "{{ if .Data.full_name }}{{ .Data.full_name }}{{ else }}there{{ end }}",
        actionUrl,
        origin: "{{ .SiteURL }}",
        template: true,
      });
      combined.push({ locale, html: template.html, subject: template.subject });
      await writeFile(
        `supabase/templates/${kind}${locale === "en" ? "" : "." + locale}.html`,
        template.html,
      );
      await writeFile(
        `supabase/templates/${kind}${locale === "en" ? "" : "." + locale}.txt`,
        template.text,
      );
      const preview = renderEmail({
        kind,
        locale,
        name: locale === "am" ? "ማሂ" : "Mahi",
        actionUrl: `${origin}${kind === "recovery" ? "/reset-password" : "/onboarding"}`,
        origin,
      });
      await writeFile(`docs/emails/${kind}.${locale}.html`, preview.html);
      await writeFile(`docs/emails/${kind}.${locale}.txt`, preview.text);
    }
    const english = combined.find((c) => c.locale === "en")!;
    const localized = combined.filter((c) => c.locale !== "en");
    const conditional = (field: "html" | "subject") =>
      localized
        .map(
          (c, i) =>
            `{{ ${i === 0 ? "if" : "else if"} eq .Data.locale "${c.locale}" }}${c[field]}`,
        )
        .join("") + `{{ else }}${english[field]}{{ end }}`;
    await writeFile(
      `supabase/templates/${kind}.localized.html`,
      conditional("html"),
    );
    await writeFile(
      `supabase/templates/${kind}.localized.subject.txt`,
      conditional("subject"),
    );
  }
}
main().catch(() => {
  process.exitCode = 1;
});
