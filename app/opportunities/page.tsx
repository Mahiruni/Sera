import Link from "next/link";
import { Breadcrumbs, GuideCards } from "@/components/public-site";
import { publicMetadata } from "@/lib/seo";
export const metadata = publicMetadata("/opportunities");
export default function Page() {
  return (
    <div className="container page public-hub">
      <Breadcrumbs
        items={[{ name: "Career opportunities", path: "/opportunities" }]}
      />
      <h1>A clearer starting point for your job search in Ethiopia.</h1>
      <p className="subtext">
        Build a focused application and check the source of an opportunity
        before you respond. Sera.et offers practical guidance and a recruitment
        demonstration; it is not currently a source of verified live vacancies.
      </p>
      <section className="public-editorial">
        <h2>Check the announcement, then plan the application</h2>
        <p>
          Keep the original vacancy source, its date, the stated deadline and
          the official application route together. Compare responsibilities with
          examples of work you can demonstrate. Record clarification from the
          organization rather than filling gaps with assumptions. Treat
          forwarded messages and copied posters as leads to investigate.
        </p>
        <p>
          Our guide explains a practical sequence for checking an employer’s
          published information and preparing a careful application. It does not
          guarantee a vacancy’s legitimacy or replace independent verification.
        </p>
        <Link className="inline-link" href="/guides/check-a-job-opportunity">
          Read the opportunity checklist
        </Link>
      </section>
      <section className="public-release">
        <h2>Try the interface with sample information</h2>
        <p>
          The demo includes filters, bookmarks, editable profiles, local
          applications and employer stages. Its vacancies, employer names and
          salaries are demonstration content. No application is sent externally
          and there is no live recruitment database.
        </p>
        <div className="public-actions">
          <Link className="button" href="/demo">
            Open the recruitment demo
          </Link>
          <Link className="button secondary" href="/how-it-works">
            How the demo works
          </Link>
        </div>
      </section>
      <GuideCards />
    </div>
  );
}
