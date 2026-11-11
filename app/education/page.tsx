import Link from "next/link";
import { Breadcrumbs, GuideCards } from "@/components/public-site";
import { publicMetadata } from "@/lib/seo";
export const metadata = publicMetadata("/education");
export default function Page() {
  return (
    <div className="container page public-hub">
      <Breadcrumbs items={[{ name: "Education", path: "/education" }]} />
      <h1>Plan your studies, projects and next learning opportunity.</h1>
      <p className="subtext">
        Practical starting points for students, teachers and people comparing
        education options in Ethiopia. Read the guides available today; academic
        accounts and institutional workflows remain planned.
      </p>
      <section className="public-editorial">
        <h2>For students choosing a programme</h2>
        <p>
          Compare the qualification, subject, study mode, campus and teaching
          language. Ask for current admissions instructions and a written fee
          breakdown from the responsible institution. Note which details apply
          to a particular programme or intake. Our comparison guide helps
          organize these questions without inventing prices, deadlines or
          accreditation claims.
        </p>
        <Link
          href="/guides/compare-university-college-options"
          className="inline-link"
        >
          Compare study options
        </Link>
        <h2>For assignments, exams and student projects</h2>
        <p>
          Keep the official brief, marking criteria, deadline and submission
          route together. Break a project into steps, record revisions and check
          exported files before submitting them. Follow the institution’s rules
          for sharing learning materials and examination content. Public links
          should not expose private submissions, grades or restricted papers.
        </p>
        <Link
          href="/guides/organize-assignments-exams-projects"
          className="inline-link"
        >
          Read the academic planning guide
        </Link>
        <h2>For teachers and institutions</h2>
        <p>
          Sera.et’s roadmap includes secure institutional roles, course spaces,
          assignments, tests, midterm schedules, project supervision and
          reviewed announcements. These are not active services. Real student
          records will require server-enforced access, institutional permissions
          and clear retention and correction rules before they can be accepted.
        </p>
        <Link className="inline-link" href="/institutions">
          Research institution information
        </Link>
      </section>
      <GuideCards />
    </div>
  );
}
