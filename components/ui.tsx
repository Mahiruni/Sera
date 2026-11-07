"use client";
import Link from "next/link";
import {
  ArrowUpRight,
  Bookmark,
  MapPin,
  ArrowRight,
  Search,
  BriefcaseBusiness,
} from "lucide-react";
import type { Job } from "@/lib/model";
import { pay, categories } from "@/lib/model";
import { useSera } from "./store";
export function Brand({ small = false }: { small?: boolean }) {
  return (
    <Link
      href="/"
      className={`brand ${small ? "small" : ""}`}
      aria-label="Sera home"
    >
      <span className="brand-mark" aria-hidden="true">
        <i />
        <i />
        <i />
      </span>
      <span>
        sera<span className="brand-am">ሥራ</span>
        <span className="brand-dot">.</span>
      </span>
    </Link>
  );
}
export function JobCard({ job }: { job: Job }) {
  const { state, setState, t, lang } = useSera();
  const saved = state.saved.includes(job.id);
  const c = categories.find((c) => c.id === job.cat);
  return (
    <article className="job-card">
      <div className="card-top">
        <span
          className={`company-mark ${job.empId === 3 ? "gold" : ""}`}
          aria-hidden="true"
        >
          {job.org
            .split(" ")
            .map((w) => w[0])
            .slice(0, 2)
            .join("")}
        </span>
        <span className="sample-tag">{t("sample")}</span>
        <button
          type="button"
          className={`icon-button bookmark ${saved ? "selected" : ""}`}
          aria-label={`${saved ? "Unsave" : "Save"} ${job.title}`}
          aria-pressed={saved}
          onClick={() =>
            setState((s) => ({
              ...s,
              saved: saved
                ? s.saved.filter((id) => id !== job.id)
                : [...s.saved, job.id],
            }))
          }
        >
          <Bookmark size={18} fill={saved ? "currentColor" : "none"} />
        </button>
      </div>
      <p className="company-name">{job.org}</p>
      <h3>
        <Link href={`/jobs/${job.id}`}>{job.title}</Link>
      </h3>
      <p className="job-location">
        <MapPin size={13} />
        {job.loc}
      </p>
      <div className="tags">
        <span>{job.type}</span>
        <span>{c?.[lang] || job.cat}</span>
      </div>
      <div className="job-card-bottom">
        <p>
          <strong>{pay(job)}</strong>
          <span>{t("month")}</span>
        </p>
        <Link
          className="round-link"
          href={`/jobs/${job.id}`}
          aria-label={`${t("details")}: ${job.title}`}
        >
          <ArrowUpRight size={20} />
        </Link>
      </div>
    </article>
  );
}
export function Heading({
  eyebrow,
  title,
  description,
  children,
}: {
  eyebrow?: string;
  title: string;
  description?: string;
  children?: React.ReactNode;
}) {
  return (
    <div className="page-heading">
      {eyebrow && <p className="eyebrow">{eyebrow}</p>}
      <div className="heading-line">
        <div>
          <h1>{title}</h1>
          {description && <p className="subtext">{description}</p>}
        </div>
        {children}
      </div>
    </div>
  );
}
export function Empty({
  text,
  action,
}: {
  text?: string;
  action?: React.ReactNode;
}) {
  const { t } = useSera();
  return (
    <div className="empty">
      <span className="empty-icon">
        <Search size={25} />
      </span>
      <h3>{t("empty")}</h3>
      <p>{text || t("noResults")}</p>
      {action}
    </div>
  );
}
export function Notice({ children }: { children: React.ReactNode }) {
  return (
    <div className="info-note">
      <BriefcaseBusiness size={17} />
      <p>{children}</p>
    </div>
  );
}
export function LinkButton({
  href,
  children,
  secondary = false,
}: {
  href: string;
  children: React.ReactNode;
  secondary?: boolean;
}) {
  return (
    <Link href={href} className={`button ${secondary ? "secondary" : ""}`}>
      {children}
      <ArrowRight size={16} />
    </Link>
  );
}
export function Loading() {
  return (
    <div className="skeleton-wrap" aria-label="Loading workspace" role="status">
      <div className="skeleton skeleton-heading" />
      <div className="jobs-grid">
        {[1, 2, 3].map((n) => (
          <div className="skeleton skeleton-card" key={n} />
        ))}
      </div>
    </div>
  );
}
