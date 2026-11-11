"use client";
import { useState } from "react";
import { useSearchParams } from "next/navigation";
import Link from "next/link";
import {
  Search,
  ArrowLeft,
  MapPin,
  Clock3,
  Building2,
  BriefcaseBusiness,
  ArrowUpRight,
  Bookmark,
} from "lucide-react";
import { useSera } from "./store";
import { Heading, JobCard, Empty, Notice, Loading } from "./ui";
import {
  filterJobs,
  categories,
  locations,
  keywordMatch,
  pay,
  apply,
} from "@/lib/model";
export function Discovery({
  savedOnly = false,
  matches = false,
}: {
  savedOnly?: boolean;
  matches?: boolean;
}) {
  const { t, lang, state, ready } = useSera();
  const params = useSearchParams();
  const [q, setQ] = useState(params.get("q") || "");
  const [cat, setCat] = useState(params.get("cat") || "");
  const [loc, setLoc] = useState(params.get("loc") || "");
  const [type, setType] = useState("");
  const [sort, setSort] = useState("default");
  let jobs = filterJobs(state.jobs, q, cat, loc, type);
  if (savedOnly) jobs = jobs.filter((j) => state.saved.includes(j.id));
  if (matches)
    jobs = [...jobs].sort(
      (a, b) =>
        keywordMatch(state.profile, b).percent -
        keywordMatch(state.profile, a).percent,
    );
  if (sort === "salary") jobs = [...jobs].sort((a, b) => b.payMax - a.payMax);
  if (!ready) return <Loading />;
  return (
    <div className="container page">
      <Heading
        title={t(matches ? "matches" : savedOnly ? "savedTitle" : "browseHint")}
        description={t(matches ? "matchNote" : "featured")}
      />
      {matches && (
        <Notice>
          {t("noAI")}{" "}
          <Link href="/profile">
            {t("profile")}
            <ArrowUpRight size={14} />
          </Link>
        </Notice>
      )}
      <div className="filter-panel">
        <div className="search-field">
          <Search size={18} />
          <label className="sr-only" htmlFor="job-search">
            {t("search")}
          </label>
          <input
            id="job-search"
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder={t("search")}
            maxLength={150}
          />
        </div>
        <label>
          <span className="sr-only">{t("category")}</span>
          <select value={cat} onChange={(e) => setCat(e.target.value)}>
            <option value="">{t("all")}</option>
            {categories.map((c) => (
              <option key={c.id} value={c.id}>
                {c[lang]}
              </option>
            ))}
          </select>
        </label>
        <label>
          <span className="sr-only">{t("location")}</span>
          <select value={loc} onChange={(e) => setLoc(e.target.value)}>
            <option value="">{t("allLoc")}</option>
            {locations.map((l) => (
              <option key={l}>{l}</option>
            ))}
          </select>
        </label>
        <label>
          <span className="sr-only">{t("type")}</span>
          <select value={type} onChange={(e) => setType(e.target.value)}>
            <option value="">{t("allTypes")}</option>
            {["Full-time", "Contract", "Part-time", "Internship"].map((t) => (
              <option key={t}>{t}</option>
            ))}
          </select>
        </label>
      </div>
      <div className="results-line">
        <span role="status">
          {jobs.length} {lang === "en" ? "sample opportunities" : "የሙከራ ዕድሎች"}
        </span>
        <div>
          <button
            className="text-button"
            onClick={() => {
              setQ("");
              setCat("");
              setLoc("");
              setType("");
              setSort("default");
            }}
          >
            {t("clear")}
          </button>
          {!matches && (
            <label>
              <span className="sr-only">Sort opportunities</span>
              <select
                className="sort"
                value={sort}
                onChange={(e) => setSort(e.target.value)}
              >
                <option value="default">
                  {lang === "en" ? "Original order" : "የመጀመሪያ ቅደም ተከተል"}
                </option>
                <option value="salary">
                  {lang === "en" ? "Highest salary" : "ከፍተኛ ደመወዝ"}
                </option>
              </select>
            </label>
          )}
        </div>
      </div>
      {jobs.length ? (
        <div className="jobs-grid">
          {jobs.map((j) => (
            <div key={j.id}>
              {matches && (
                <p className="match-label">
                  {keywordMatch(state.profile, j).percent}%{" "}
                  {lang === "en" ? "keyword overlap" : "የቁልፍ ቃላት መመሳሰል"}
                </p>
              )}
              <JobCard job={j} />
            </div>
          ))}
        </div>
      ) : (
        <Empty
          text={
            savedOnly
              ? lang === "en"
                ? "Tap the bookmark on a role to keep it here."
                : "ለማስቀመጥ በሥራው ላይ ያለውን ምልክት ይጫኑ።"
              : undefined
          }
          action={
            <Link className="button secondary" href="/jobs">
              {t("explore")}
            </Link>
          }
        />
      )}
    </div>
  );
}
export function JobDetail({ id }: { id: number }) {
  const { state, setState, t, lang, ready, notify } = useSera();
  const [cover, setCover] = useState("");
  const [detailTab, setDetailTab] = useState<"description" | "company">(
    "description",
  );
  if (!ready) return <Loading />;
  const job = state.jobs.find((j) => j.id === id);
  if (!job)
    return (
      <div className="container page">
        <Heading
          title={lang === "en" ? "Sample role not found" : "የሙከራ ሥራ አልተገኘም"}
        />
        <Link href="/jobs" className="button">
          {t("back")}
        </Link>
      </div>
    );
  const applied = state.apps.some((a) => a.jobId === id);
  return (
    <div className="container page detail-page">
      <Link href="/jobs" className="inline-link">
        <ArrowLeft size={16} />
        {t("back")}
      </Link>
      <section className="role-overview">
        <div className="role-overview-band" aria-hidden="true" />
        <span className="role-company-symbol" aria-hidden="true">
          {job.org
            .split(" ")
            .map((word) => word[0])
            .slice(0, 2)
            .join("")}
        </span>
        <p className="role-company-name">
          {job.org}
          <span className="sample-tag">{t("sample")}</span>
        </p>
        <h1>{job.title}</h1>
        <p className="role-salary">
          {pay(job)} <span>{t("month")}</span>
        </p>
        <div className="tags role-overview-tags">
          <span>{job.type}</span>
          <span>{job.loc}</span>
          <span>
            {categories.find((category) => category.id === job.cat)?.[lang]}
          </span>
        </div>
      </section>
      <div className="detail-layout">
        <article className="detail-main">
          <div
            className="detail-tabs"
            role="group"
            aria-label={lang === "en" ? "Role information" : "የሥራ መረጃ"}
          >
            <button
              type="button"
              aria-pressed={detailTab === "description"}
              onClick={() => setDetailTab("description")}
            >
              <BriefcaseBusiness size={16} />
              {t("description")}
            </button>
            <button
              type="button"
              aria-pressed={detailTab === "company"}
              onClick={() => setDetailTab("company")}
            >
              <Building2 size={16} />
              {lang === "en" ? "Company" : "ድርጅት"}
            </button>
          </div>
          {detailTab === "description" ? (
            <>
              <div className="role-facts">
                <div>
                  <Clock3 size={19} />
                  <span>{t("type")}</span>
                  <strong>{job.type}</strong>
                </div>
                <div>
                  <MapPin size={19} />
                  <span>{t("location")}</span>
                  <strong>{job.loc}</strong>
                </div>
                <div>
                  <BriefcaseBusiness size={19} />
                  <span>{t("category")}</span>
                  <strong>
                    {
                      categories.find((category) => category.id === job.cat)?.[
                        lang
                      ]
                    }
                  </strong>
                </div>
              </div>
              <h2>{t("about")}</h2>
              <p className="body-copy">{job.desc}</p>
              <h2>{t("requirements")}</h2>
              <ul className="requirements">
                {job.req.split("·").map((requirement, i) => (
                  <li key={i}>{requirement.trim()}</li>
                ))}
              </ul>
            </>
          ) : (
            <div className="company-information">
              <h2>{job.org}</h2>
              <p className="body-copy">
                {lang === "en"
                  ? "This employer is part of Sera’s sample collection. Company verification and live hiring are not available in this demo."
                  : "ይህ ቀጣሪ የሥራ የሙከራ ስብስብ አካል ነው። የድርጅት ማረጋገጫ እና ቀጥታ ቅጥር በዚህ ሙከራ አይገኙም።"}
              </p>
              <h2>{lang === "en" ? "More sample roles" : "ተጨማሪ የሙከራ ሥራዎች"}</h2>
              {state.jobs
                .filter(
                  (other) =>
                    other.org === job.org &&
                    other.status === "open" &&
                    other.id !== job.id,
                )
                .map((other) => (
                  <Link
                    className="company-role-link"
                    href={`/jobs/${other.id}`}
                    key={other.id}
                  >
                    {other.title}
                    <ArrowUpRight size={16} />
                  </Link>
                ))}
            </div>
          )}
          <Notice>{t("demoNote")}</Notice>
        </article>
        <aside className="detail-aside">
          <div className="panel">
            <p className="eyebrow">{lang === "en" ? "COMPENSATION" : "ደመወዝ"}</p>
            <h3 className="salary-display">{pay(job)}</h3>
            <p className="subtext">{t("month")}</p>
            <div className="divider" />
            {state.role === "seeker" ? (
              <form
                onSubmit={(e) => {
                  e.preventDefault();
                  setState((s) => apply(s, id, cover.trim()));
                  notify(t("applied"));
                }}
              >
                <label>
                  {t("cover")}
                  <textarea
                    value={cover}
                    onChange={(e) => setCover(e.target.value)}
                    placeholder={t("coverHint")}
                    maxLength={5000}
                  />
                </label>
                <button
                  type="button"
                  className="text-button"
                  disabled={applied}
                  onClick={() =>
                    setCover(
                      lang === "en"
                        ? `Dear ${job.org} team,\n\nI am interested in the ${job.title} role. My experience includes ${state.profile.headline}.\n\n[Add a specific example and review before saving.]\n\n${state.name}`
                        : `ለ${job.org} ቡድን፣\n\nበ${job.title} ሥራ ላይ ፍላጎት አለኝ።\n\n[ልምድዎን እና ምሳሌዎችን ያክሉ።]\n\n${state.name}`,
                    )
                  }
                >
                  {t("template")}
                </button>
                <button
                  className="button full"
                  disabled={applied || job.status !== "open"}
                >
                  {applied
                    ? t("applied")
                    : job.status !== "open"
                      ? t("closed")
                      : t("apply")}
                </button>
              </form>
            ) : (
              <>
                <Link className="button full" href="/account?role=seeker">
                  {t("tryCandidate")}
                  <ArrowUpRight size={16} />
                </Link>
                <p className="small-note">{t("notLive")}</p>
              </>
            )}
            <button
              className="button secondary full"
              aria-pressed={state.saved.includes(id)}
              onClick={() =>
                setState((s) => ({
                  ...s,
                  saved: s.saved.includes(id)
                    ? s.saved.filter((n) => n !== id)
                    : [...s.saved, id],
                }))
              }
            >
              <Bookmark size={16} />
              {state.saved.includes(id) ? t("saved") : t("savedTitle")}
            </button>
            <p className="small-note">{t("localOnly")}</p>
          </div>
        </aside>
      </div>
    </div>
  );
}
