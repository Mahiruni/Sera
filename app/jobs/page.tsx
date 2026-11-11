import Link from "next/link";
import { PageIntro } from "@/components/page-intro";
import { BriefcaseBusiness, Plus } from "lucide-react";
import { pageMetadata } from "@/lib/seo";
import { serverClient } from "@/lib/supabase/server";
import { workTypes } from "@/lib/recruitment/validation";
import { type LiveJob } from "@/lib/recruitment/data";
import { LiveJobCard, MarketNav, Empty } from "@/components/recruitment/ui";
export const metadata = pageMetadata(
  "/jobs",
  "Find jobs",
  "Explore employer-posted roles on Sera. Review each opportunity before applying.",
);
export default async function Page({
  searchParams,
}: {
  searchParams: Promise<{
    q?: string;
    location?: string;
    company?: string;
    type?: string;
    sort?: string;
    page?: string;
  }>;
}) {
  const p = await searchParams;
  const q = (p.q || "").trim().slice(0, 100),
    location = (p.location || "").trim().slice(0, 100),
    company = (p.company || "").trim().slice(0, 100),
    sort = p.sort === "oldest" ? "oldest" : "newest",
    type = workTypes.includes(p.type as (typeof workTypes)[number])
      ? p.type
      : "";
  const page = Math.min(1000, Math.max(1, Number.parseInt(p.page || "1") || 1));
  const c = await serverClient();
  let query = c
    .from("jobs")
    .select("*", { count: "exact" })
    .eq("status", "open")
    .order("created_at", { ascending: sort === "oldest" })
    .order("id", { ascending: true })
    .range((page - 1) * 20, page * 20 + 19);
  if (q) query = query.ilike("title", "%" + q.replace(/[%_\\]/g, "") + "%");
  if (location)
    query = query.ilike(
      "location",
      "%" + location.replace(/[%_\\]/g, "") + "%",
    );
  if (company)
    query = query.ilike(
      "company",
      "%" + company.replace(/[%_\\]/g, "") + "%",
    );
  if (type) query = query.eq("work_type", type);
  const { data, error, count } = await query;
  const jobs = (data || []) as LiveJob[];
  const total = count ?? 0;
  const hasNext = page * 20 < total;
  const next = new URLSearchParams({
    q,
    location,
    company,
    type: type || "",
    sort,
    page: String(page + 1),
  });
  return (
    <div className="container page live-market">
      <PageIntro icon={BriefcaseBusiness} title="Find your next chapter."
        description="Explore employer-posted roles and introduce yourself with confidence.">
        <Link className="button" href="/employer/post">
          <Plus size={16} /> Post a job
        </Link>
      </PageIntro>
      <MarketNav />
      <form className="panel live-search">
        <label>
          Job title
          <input
            name="q"
            defaultValue={q}
            placeholder="Search roles"
            maxLength={100}
          />
        </label>
        <label>
          Location
          <input
            name="location"
            defaultValue={location}
            placeholder="City or remote"
            maxLength={100}
          />
        </label>
        <label>
          Company
          <input
            name="company"
            defaultValue={company}
            placeholder="Search employers"
            maxLength={100}
          />
        </label>
        <label>
          Work type
          <select name="type" defaultValue={type}>
            <option value="">All types</option>
            {workTypes.map((t) => (
              <option key={t}>{t}</option>
            ))}
          </select>
        </label>
        <label>
          Sort by
          <select name="sort" defaultValue={sort}>
            <option value="newest">Newest first</option>
            <option value="oldest">Oldest first</option>
          </select>
        </label>
        <button className="button">Search</button>
      </form>
      <p className="live-disclosure">
        Listings are provided by their posters. Sera has not independently
        verified employers. Never pay to apply or send sensitive documents
        through messages.
      </p>
      {!error && (
        <p className="live-disclosure" role="status">
          {total === 0
            ? "No matching opportunities"
            : `${total.toLocaleString()} ${total === 1 ? "opportunity" : "opportunities"} · Page ${page} of ${Math.max(1, Math.ceil(total / 20))}`}
        </p>
      )}
      {error ? (
        <Empty title="Jobs could not be loaded">
          <p>Please refresh and try again.</p>
        </Empty>
      ) : jobs.length ? (
        <div className="live-job-grid">
          {jobs.slice(0, 20).map((j) => (
            <LiveJobCard key={j.id} job={j} />
          ))}
        </div>
      ) : (
        <Empty title="No open jobs found">
          <p>
            {q || location || company || type
              ? "Try a different search."
              : "Employers can publish the first opportunity here."}
          </p>
          <Link href="/employer/post" className="inline-link">
            Post a job →
          </Link>
        </Empty>
      )}
      <nav className="live-pagination">
        {page > 1 && (
          <Link
            href={
              "/jobs?" +
              new URLSearchParams({
                q,
                location,
                company,
                type: type || "",
                sort,
                page: String(page - 1),
              })
            }
          >
            ← Previous
          </Link>
        )}
        {hasNext && <Link href={"/jobs?" + next}>Next →</Link>}
      </nav>
      <Link className="inline-link" href="/demo/jobs">
        Explore sample jobs separately →
      </Link>
    </div>
  );
}
