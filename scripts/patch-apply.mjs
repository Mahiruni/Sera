import { readFileSync, writeFileSync } from "node:fs";
const root = process.argv[2] ?? ".";
function patch(file, edits) {
  const p = `${root}/${file}`;
  let s = readFileSync(p, "utf8");
  for (const e of edits) {
    if (s.includes(e.marker)) continue; // already applied
    if (e.replaceBetween) {
      const [a, b] = e.replaceBetween;
      const i = s.indexOf(a), j = s.indexOf(b);
      if (i < 0 || j < i) throw new Error(`${file}: block not found: ${a}`);
      s = s.slice(0, i) + e.to + s.slice(j);
    } else {
      if (s.split(e.from).length !== 2) throw new Error(`${file}: anchor not found exactly once: ${e.from.slice(0, 60)}`);
      s = s.replace(e.from, () => e.to);
    }
  }
  writeFileSync(p, s);
  console.log("patched", file);
}

const applyForm = `export type ApplySnapshot = {
  name: string;
  headline: string;
  skills: string;
  experience: string;
  education: string;
};
export function ApplyForm({
  jobId,
  jobTitle,
  company,
  snapshot,
}: {
  jobId: string;
  jobTitle: string;
  company: string;
  snapshot: ApplySnapshot;
}) {
  const [state, action, pending] = useActionState(applyToJob, {});
  const rows = [
    ["Name", snapshot.name],
    ["Headline", snapshot.headline],
    ["Skills", snapshot.skills],
    ["Experience", snapshot.experience],
    ["Education", snapshot.education],
  ] as const;
  const intro = \`Hello,\\n\\nI’m \${snapshot.name}\${snapshot.headline ? \`, \${snapshot.headline}\` : ""}. I’m applying for the \${jobTitle} role at \${company} because \`;
  return (
    <form action={action} className="panel live-form" aria-busy={pending}>
      <input name="job_id" type="hidden" value={jobId} />
      <h2>Make your introduction</h2>
      <section aria-label="Details that will be shared">
        <h3>Review what will be shared</h3>
        <dl style={{ display: "grid", gap: 10, margin: 0 }}>
          {rows.map(([label, value]) => (
            <div key={label}>
              <dt className="auth-help">
                <strong>{label}</strong>
              </dt>
              <dd style={{ margin: 0, whiteSpace: "pre-line" }}>
                {value || "Not added yet"}
              </dd>
            </div>
          ))}
        </dl>
        <p className="auth-help">
          Something missing or out of date?{" "}
          <Link href="/settings/linkedin">Import from LinkedIn</Link> or{" "}
          <Link href="/settings">update your profile</Link> first. Your phone
          number and email are never shared.
        </p>
      </section>
      <label className="auth-label">
        Cover note
        <textarea
          name="cover_note"
          required
          minLength={20}
          maxLength={4000}
          rows={8}
          defaultValue={intro}
        />
      </label>
      <label className="live-check">
        <input type="checkbox" name="share_profile" value="yes" required />
        <span>
          Share the details listed above and this note with this job’s poster.
          Your phone number, email and other private profile fields are not
          included. This creates a snapshot; later profile changes do not alter
          it.
        </span>
      </label>
      <Feedback state={state} />
      <Submit pending={pending} label="Send application" />
    </form>
  );
}
`;

patch("components/recruitment/forms.tsx", [
  { marker: 'import Link from "next/link";', from: 'import { ArrowRight, LoaderCircle } from "lucide-react";', to: 'import { ArrowRight, LoaderCircle } from "lucide-react";\nimport Link from "next/link";' },
  { marker: "export type ApplySnapshot", replaceBetween: ["export function ApplyForm(", "export function MessageForm("], to: applyForm },
]);
patch("lib/recruitment/actions.ts", [
  { marker: "candidate_headline", from: "      skills: profile.skills,\n      experience: profile.experience,\n", to: '      skills: profile.skills,\n      experience: profile.experience,\n      candidate_headline: profile.headline ?? "",\n      education: profile.education ?? "",\n' },
]);
patch("app/jobs/[id]/page.tsx", [
  { marker: "const profile =", from: "  const p = await searchParams;\n  return (", to: '  const profile = user?.email_confirmed_at\n    ? (\n        await c\n          .from("profiles")\n          .select("full_name,headline,skills,experience,education")\n          .eq("id", user.id)\n          .single()\n      ).data\n    : null;\n  const p = await searchParams;\n  return (' },
  { marker: "snapshot={{", from: "<ApplyForm jobId={id} />", to: '<ApplyForm\n          jobId={id}\n          jobTitle={job.title}\n          company={job.company}\n          snapshot={{\n            name: profile?.full_name ?? "",\n            headline: profile?.headline ?? "",\n            skills: profile?.skills ?? "",\n            experience: profile?.experience ?? "",\n            education: profile?.education ?? "",\n          }}\n        />' },
]);
patch("lib/recruitment/data.ts", [
  { marker: "candidate_headline: string;", from: "  experience: string;\n  cover_note: string;", to: "  experience: string;\n  candidate_headline: string;\n  education: string;\n  cover_note: string;" },
]);
patch("app/applications/[id]/page.tsx", [
  { marker: "a.candidate_headline", from: '          <h3>Skills</h3>\n          <p>{a.skills || "Not provided"}</p>', to: '          <h3>Headline</h3>\n          <p>{a.candidate_headline || "Not provided"}</p>\n          <h3>Skills</h3>\n          <p>{a.skills || "Not provided"}</p>' },
  { marker: "a.education", from: '          <p>{a.experience || "Not provided"}</p>', to: '          <p>{a.experience || "Not provided"}</p>\n          <h3>Education</h3>\n          <p style={{ whiteSpace: "pre-line" }}>\n            {a.education || "Not provided"}\n          </p>' },
]);
patch("components/workspace/overview.tsx", [
  { marker: "/settings/linkedin", from: "              Update your profile\n              <ChevronRight size={14} />\n            </Link>", to: '              Update your profile\n              <ChevronRight size={14} />\n            </Link>\n            <Link href="/settings/linkedin">\n              <UserRound size={16} />\n              LinkedIn import & connection\n              <ChevronRight size={14} />\n            </Link>' },
]);
