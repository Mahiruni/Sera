import Link from "next/link";
import { Breadcrumbs } from "@/components/public-site";
import { publicMetadata } from "@/lib/seo";
export const metadata = publicMetadata("/institutions");
export default function Page() {
  return (
    <div className="container page public-hub">
      <Breadcrumbs
        items={[
          { name: "Education", path: "/education" },
          { name: "Institution research", path: "/institutions" },
        ]}
      />
      <h1>
        Research universities, colleges and training providers with better
        questions.
      </h1>
      <p className="subtext">
        This is an institution-research guide, not a verified directory. Sera.et
        has not established institutional partnerships or published approved
        university, college, campus, private-provider or government-provider
        profiles.
      </p>
      <section className="panel information-article">
        <section>
          <h2>Identify the exact institution, campus and programme</h2>
          <p>
            Record the official name, location, programme and intake you are
            researching. A parent institution and a particular campus may
            publish different information. Keep official website links and dated
            announcements together, and ask the responsible institution to
            clarify conflicting details.
          </p>
        </section>
        <section>
          <h2>Separate facts that need different checks</h2>
          <p>
            Recognition, programme approval, entry requirements, teaching
            arrangements and costs are separate questions. Verify each through
            the responsible official source. A polished brochure, a familiar
            logo or a directory listing does not by itself establish all of
            them.
          </p>
        </section>
        <section>
          <h2>Compare costs and announcements fairly</h2>
          <p>
            Ask for a current written breakdown and note the period covered by
            every figure. Keep tuition, registration, examination costs and
            other relevant charges in separate rows. Treat old screenshots as
            dated records, not current quotes. Recheck application deadlines,
            required documents and examination arrangements before acting.
          </p>
        </section>
        <section>
          <h2>What a future Sera.et directory will need</h2>
          <p>
            Institution profiles will need verified sources, an identified
            record owner, correction workflows, review dates and a clear
            distinction between official notices and commentary. Public browsing
            must remain separate from private student and staff records. Until
            that foundation exists, no institution is presented here as approved
            or affiliated.
          </p>
        </section>
      </section>
      <div className="public-actions">
        <Link
          className="button"
          href="/guides/compare-university-college-options"
        >
          Read the comparison guide
        </Link>
        <Link className="button secondary" href="/education">
          Education guidance
        </Link>
      </div>
    </div>
  );
}
