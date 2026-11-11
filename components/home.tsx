"use client";
import Link from "next/link";
import { useState } from "react";
import { useRouter } from "next/navigation";
import {
  Search,
  ArrowRight,
  ArrowUpRight,
  MapPin,
  SlidersHorizontal,
  Bookmark,
  BriefcaseBusiness,
  MessagesSquare,
  Sparkles,
  Plus,
  Compass,
} from "lucide-react";
import { useSera } from "./store";
import { Brand, JobCard, Empty } from "./ui";
import { locations, categories, pay } from "@/lib/model";

export function HomePage() {
  const { t, lang, state } = useSera();
  const router = useRouter();
  const [workType, setWorkType] = useState("");
  const openRoles = state.jobs.filter((j) => j.status === "open");
  const visibleRoles = openRoles.filter(
    (j) => !workType || j.type === workType,
  );
  const en = lang === "en";
  return (
    <div className="container home-app">
      <div className="home-intro">
        <div>
          <Brand />
          <p className="home-location">
            <MapPin size={13} />
            {en ? "Your next opportunity, in Ethiopia" : "ቀጣዩ ዕድልዎ፣ በኢትዮጵያ"}
          </p>
        </div>
        <Link href="/account" className="demo-pill">
          <span />
          {t("demo")}
          <ArrowUpRight size={13} />
        </Link>
      </div>

      <section className="search-banner" aria-labelledby="home-title">
        <div className="search-banner-copy">
          <h1 id="home-title">
            <span className="home-title-first">
              {en ? "Find work." : "ሥራ ያግኙ።"}
            </span>
            <br />
            <span>{en ? "Love what’s next." : "ቀጣዩን ምዕራፍ ይውደዱ።"}</span>
          </h1>
          <p>
            {en
              ? "A little more clarity. A lot more possibility. Explore your next move with Sera."
              : "የበለጠ ግልጽነት። ተጨማሪ ዕድል። ቀጣዩን እርምጃዎን ከሥራ ጋር ያስሱ።"}
          </p>
          <form
            className="home-search"
            action="/jobs"
            onSubmit={(e) => {
              e.preventDefault();
              const f = new FormData(e.currentTarget);
              router.push(
                `/jobs?q=${encodeURIComponent(String(f.get("q") || ""))}&loc=${encodeURIComponent(String(f.get("loc") || ""))}`,
              );
            }}
          >
            <div className="home-query">
              <Search size={20} />
              <label className="sr-only" htmlFor="home-query">
                {t("search")}
              </label>
              <input
                id="home-query"
                name="q"
                placeholder={t("search")}
                maxLength={150}
              />
            </div>
            <label className="home-location-select">
              <span className="sr-only">{t("location")}</span>
              <MapPin size={17} />
              <select name="loc" defaultValue="">
                <option value="">{t("allLoc")}</option>
                {locations.map((loc) => (
                  <option key={loc}>{loc}</option>
                ))}
              </select>
            </label>
            <button
              type="submit"
              className="search-submit"
              aria-label={t("searchBtn")}
            >
              <span>{en ? "Search" : "ፈልግ"}</span>
              <ArrowRight size={19} />
            </button>
          </form>
          <div className="popular-searches">
            <span>{en ? "Try" : "ይሞክሩ"}</span>
            <Link href="/jobs?cat=it">{en ? "Technology" : "ቴክኖሎጂ"}</Link>
            <Link href="/jobs?cat=fin">{en ? "Finance" : "ፋይናንስ"}</Link>
            <Link href="/jobs?loc=Remote%20(Ethiopia)">
              {en ? "Remote work" : "ከርቀት ሥራ"}
            </Link>
          </div>
        </div>
        <div className="search-banner-art" aria-hidden="true">
          <div className="blue-orbit" />
          <div className="blue-orbit inner" />
          <div className="opportunity-tile">
            <div className="tile-symbol">
              <BriefcaseBusiness size={31} strokeWidth={1.6} />
            </div>
            <span>SERA · ሥራ</span>
            <strong>{en ? "Your next\nbig thing." : "ቀጣዩ\nትልቅ ዕድልዎ።"}</strong>
            <div className="tile-bottom">
              <span>{en ? "Made for your next move" : "ለቀጣዩ እርምጃዎ"}</span>
              <ArrowUpRight size={21} />
            </div>
          </div>
          <div className="floating-search-symbol">
            <Compass size={24} strokeWidth={1.5} />
          </div>
          <div className="floating-saved-symbol">
            <Bookmark size={21} fill="currentColor" />
          </div>
          <span className="art-dot dot-one" />
          <span className="art-dot dot-two" />
        </div>
      </section>

      <div className="home-toolbar">
        <div className="work-type-pills" role="group" aria-label={t("type")}>
          {[
            ["", en ? "See all" : "ሁሉም"],
            ["Full-time", en ? "Full time" : "የሙሉ ጊዜ"],
            ["Part-time", en ? "Part time" : "የትርፍ ጊዜ"],
            ["Contract", en ? "Contract" : "ኮንትራት"],
          ].map(([value, label]) => (
            <button
              type="button"
              key={value}
              className={workType === value ? "active" : ""}
              aria-pressed={workType === value}
              onClick={() => setWorkType(value)}
            >
              {label}
            </button>
          ))}
        </div>
        <Link
          href="/demo/jobs"
          className="filter-shortcut"
          aria-label={en ? "Open all search filters" : "ሁሉንም ማጣሪያዎች ክፈት"}
        >
          <SlidersHorizontal size={18} />
          <span>{en ? "Filters" : "ማጣሪያዎች"}</span>
        </Link>
      </div>

      <section className="home-section" aria-labelledby="opportunities-title">
        <div className="section-head">
          <div>
            <h2 id="opportunities-title">
              {en ? "Worth a closer look" : "በቅርብ ይመልከቱ"}
              <span className="heading-spark">
                <Sparkles size={17} />
              </span>
            </h2>
            <p className="section-caption">
              {en
                ? "Sample opportunities. Real possibilities to explore."
                : "የሙከራ ዕድሎች። ለማሰስ ብዙ አማራጮች።"}
            </p>
          </div>
          <Link className="inline-link" href="/demo/jobs">
            {en ? "See all" : "ሁሉንም ይመልከቱ"}
            <ArrowUpRight size={16} />
          </Link>
        </div>
        {visibleRoles.length ? (
          <div className="spotlight-grid">
            {visibleRoles.slice(0, 3).map((job, i) => (
              <JobCard
                key={job.id}
                job={job}
                tone={i === 0 ? "cyan" : i === 1 ? "ice" : "default"}
              />
            ))}
          </div>
        ) : (
          <Empty
            text={
              en
                ? "There are no sample roles for this work type yet."
                : "ለዚህ የሥራ ዓይነት እስካሁን የሙከራ ሥራዎች የሉም።"
            }
          />
        )}
      </section>

      <div className="home-lower">
        <section className="home-section" aria-labelledby="all-roles-title">
          <div className="section-head">
            <div>
              <h2 id="all-roles-title">
                {en ? "Explore the collection" : "ስብስቡን ያስሱ"}
              </h2>
              <p className="section-caption">
                {en
                  ? "Find a role that feels like you."
                  : "ለእርስዎ የሚስማማውን ሥራ ያግኙ።"}
              </p>
            </div>
            <Link href="/demo/jobs" className="inline-link">
              {en ? "See all" : "ሁሉንም ይመልከቱ"}
              <ArrowUpRight size={16} />
            </Link>
          </div>
          <div className="role-list">
            {visibleRoles.length === 0 && (
              <p className="empty-inline">
                {en
                  ? "Try another work type to explore the collection."
                  : "ስብስቡን ለማየት ሌላ የሥራ ዓይነት ይሞክሩ።"}
              </p>
            )}
            {visibleRoles.slice(0, 5).map((job) => (
              <Link
                className="compact-role"
                key={job.id}
                href={`/jobs/${job.id}`}
              >
                <span
                  className={`company-mark ${job.empId === 3 ? "alternate" : ""}`}
                  aria-hidden="true"
                >
                  {job.org
                    .split(" ")
                    .map((w) => w[0])
                    .slice(0, 2)
                    .join("")}
                </span>
                <div>
                  <p>{job.org}</p>
                  <h3>{job.title}</h3>
                  <span>
                    {job.loc} · {job.type}
                  </span>
                </div>
                <div className="compact-role-pay">
                  <strong>{pay(job)}</strong>
                  <span>{t("month")}</span>
                </div>
                <ArrowUpRight size={19} />
              </Link>
            ))}
          </div>
        </section>
        <aside className="home-sidebar">
          <section className="organizer-panel">
            <span className="organizer-icon">
              <Bookmark size={21} />
            </span>
            <h2>
              {en ? "Your search.\nAll together." : "የእርስዎ ፍለጋ።\nሁሉም በአንድ ቦታ።"}
            </h2>
            <p>
              {en
                ? "Keep the good ones close and make your next move a little simpler."
                : "ጥሩ ዕድሎችን ያስቀምጡ፣ ቀጣዩን እርምጃዎንም ቀላል ያድርጉ።"}
            </p>
            <Link href="/saved">
              <span>
                <Bookmark size={16} />
                {t("saved")}
              </span>
              <strong>{state.saved.length}</strong>
            </Link>
            <Link href="/applications">
              <span>
                <BriefcaseBusiness size={16} />
                {t("applications")}
              </span>
              <strong>{state.apps.length}</strong>
            </Link>
            <Link href="/messages">
              <span>
                <MessagesSquare size={16} />
                {t("messages")}
              </span>
              <ArrowUpRight size={16} />
            </Link>
          </section>
          <Link className="employer-invite" href="/account?role=employer">
            <span className="invite-icon">
              <Plus size={20} />
            </span>
            <div>
              <h3>{en ? "Hiring great people?" : "ጥሩ ባለሙያ ይፈልጋሉ?"}</h3>
              <p>
                {en ? "Explore the employer workspace" : "የቀጣሪዎችን የሥራ ቦታ ያስሱ"}
              </p>
            </div>
            <ArrowUpRight size={18} />
          </Link>
        </aside>
      </div>

      <section className="home-section sectors-section">
        <div className="section-head">
          <h2>{en ? "Find your field" : "ዘርፍዎን ያግኙ"}</h2>
          <Link className="inline-link" href="/demo/jobs">
            {t("viewAll")}
            <ArrowUpRight size={16} />
          </Link>
        </div>
        <div className="sector-pills">
          {categories.map((category) => (
            <Link key={category.id} href={`/jobs?cat=${category.id}`}>
              {category[lang]}
              <ArrowUpRight size={13} />
            </Link>
          ))}
        </div>
      </section>
      <div className="demo-strip">
        <span className="status-dot" />
        <p>{t("demoNote")}</p>
        <Link href="/account">
          {t("privacy")}
          <ArrowUpRight size={14} />
        </Link>
      </div>
    </div>
  );
}
