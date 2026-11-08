import Link from "next/link";
import { ArrowUpRight, MapPin, BriefcaseBusiness } from "lucide-react";
import { salary, type LiveJob } from "@/lib/recruitment/data";
export function MarketNav() {
  return (
    <nav className="live-nav" aria-label="Recruitment">
      <Link href="/jobs">Find jobs</Link>
      <Link href="/applications">My applications</Link>
      <Link href="/saved">Saved jobs</Link>
      <Link href="/employer">Employer workspace</Link>
    </nav>
  );
}
export function LiveJobCard({ job }: { job: LiveJob }) {
  return (
    <article className="panel live-job">
      <div className="live-job-top">
        <span className="live-company-icon">
          <BriefcaseBusiness size={21} />
        </span>
        <span className="live-badge">Employer provided</span>
      </div>
      <p>{job.company}</p>
      <h2>
        <Link href={"/jobs/" + job.id}>{job.title}</Link>
      </h2>
      <span className="live-location">
        <MapPin size={14} />
        {job.location} · {job.work_type}
      </span>
      <footer>
        <strong>{salary(job)}</strong>
        <Link href={"/jobs/" + job.id} aria-label={"View " + job.title}>
          <ArrowUpRight size={19} />
        </Link>
      </footer>
    </article>
  );
}
export function Empty({
  title,
  children,
}: {
  title: string;
  children: React.ReactNode;
}) {
  return (
    <section className="panel live-empty">
      <h2>{title}</h2>
      <div>{children}</div>
    </section>
  );
}
