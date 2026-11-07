import { mkdir, writeFile } from "node:fs/promises";
import { emailKinds, renderEmail } from "../lib/emails/render";
async function main() {
  const origin = "https://sera-dusky-seven.vercel.app";
  await mkdir("supabase/templates", { recursive: true });
  await mkdir("docs/emails", { recursive: true });
  for (const kind of emailKinds) {
    const path =
      kind === "confirmation"
        ? "/auth/confirm?token_hash={{ .TokenHash }}&type=email"
        : kind === "recovery"
          ? "/auth/confirm?token_hash={{ .TokenHash }}&type=recovery"
          : kind === "welcome"
            ? "/onboarding"
            : "/settings";
    const actionUrl = `{{ .SiteURL }}${path}`;
    for (const locale of ["en", "am"]) {
      const template = renderEmail({
        kind,
        locale,
        name: "{{ if .Data.full_name }}{{ .Data.full_name }}{{ else }}there{{ end }}",
        actionUrl,
        origin: "{{ .SiteURL }}",
        template: true,
      });
      await writeFile(
        `supabase/templates/${kind}${locale === "am" ? ".am-draft" : ""}.html`,
        template.html,
      );
      await writeFile(
        `supabase/templates/${kind}${locale === "am" ? ".am-draft" : ""}.txt`,
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
  }
}
main().catch(() => {
  process.exitCode = 1;
});
