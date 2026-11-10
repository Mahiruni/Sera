import Link from "next/link";
import { ArrowUpRight, MapPin, BriefcaseBusiness, CalendarDays } from "lucide-react";
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
  const posted = new Date(job.created_at);
  const postedLabel = Number.isNaN(posted.getTime())
    ? null
    : posted.toLocaleDateString("en-GB", {
        day: "numeric",
        month: "short",
        year: "numeric",
      });
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
      {postedLabel && (
        <p className="live-location">
          <CalendarDays size={14} aria-hidden="true" />
          <span>Posted {postedLabel}</span>
        </p>
      )}
      <p className="subtext">{job.description.length > 150 ? job.description.slice(0, 147).trimEnd() + "…" : job.description}</p>
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
