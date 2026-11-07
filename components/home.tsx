"use client";
import Link from "next/link";
import { useRouter } from "next/navigation";
import {
  Search,
  ArrowUpRight,
  ArrowRight,
  MapPin,
  BriefcaseBusiness,
  Sparkles,
  Check,
  MoveUpRight,
  Globe2,
  Code2,
  ChartNoAxesCombined,
  Factory,
  HeartPulse,
  Truck,
} from "lucide-react";
import { useSera } from "./store";
import { Brand, JobCard, LinkButton } from "./ui";
import { categories } from "@/lib/model";
const catIcons = [
  ChartNoAxesCombined,
  Code2,
  Globe2,
  Factory,
  HeartPulse,
  Truck,
];
export function HomePage() {
  const { t, lang, state } = useSera();
  const router = useRouter();
  return (
    <>
      <section className="hero">
        <div className="hero-text">
          <Brand />
          <div className="hero-eyebrow">
            <span />
            {lang === "en"
              ? "GOOD WORK STARTS WITH A CONNECTION"
              : "ጥሩ ሥራ ከግንኙነት ይጀምራል"}
          </div>
          <h1>
            {t("heroTop")}
            <br />
            <em>{t("heroBottom")}</em>
          </h1>
          <p className="hero-description">{t("heroSub")}</p>
          <form
            className="hero-search"
            action="/jobs"
            onSubmit={(e) => {
              e.preventDefault();
              const data = new FormData(e.currentTarget);
              router.push(
                `/jobs?q=${encodeURIComponent(String(data.get("q") || ""))}`,
              );
            }}
          >
            <Search size={20} />
            <label className="sr-only" htmlFor="hero-query">
              {t("search")}
            </label>
            <input
              name="q"
              id="hero-query"
              placeholder={t("search")}
              maxLength={150}
            />
            <button type="submit" aria-label={t("searchBtn")}>
              <ArrowRight size={20} />
            </button>
          </form>
          <div className="hero-links">
            <span>{lang === "en" ? "YOUR NEXT MOVE" : "ቀጣዩ እርምጃዎ"}</span>
            <Link href="/jobs?cat=it">
              {lang === "en" ? "Technology" : "ቴክኖሎጂ"}
            </Link>
            <Link href="/jobs?cat=fin">
              {lang === "en" ? "Finance" : "ፋይናንስ"}
            </Link>
            <Link href="/jobs?loc=Remote%20(Ethiopia)">
              {lang === "en" ? "Remote" : "ከርቀት"}
            </Link>
          </div>
        </div>
        <div className="hero-art" aria-hidden="true">
          <div className="art-grid" />
          <div className="art-orbit orbit-one" />
          <div className="art-orbit orbit-two" />
          <div className="art-caption">
            {lang === "en" ? "POTENTIAL, MEET POSSIBILITY." : "ችሎታ፣ ዕድልን ይገናኙ።"}
          </div>
          <div className="monument">
            <div className="arch arch-back" />
            <div className="arch arch-front" />
            <div className="gold-sphere" />
            <div className="arch-ground" />
          </div>
          <div className="floating-card opportunity">
            <span className="float-icon">
              <BriefcaseBusiness size={18} />
            </span>
            <div>
              <strong>{lang === "en" ? "A better fit." : "የተሻለ ተዛማጅ።"}</strong>
              <span>
                {lang === "en" ? "For what comes next." : "ለቀጣዩ ምዕራፍ።"}
              </span>
            </div>
            <MoveUpRight size={18} />
          </div>
          <div className="floating-card potential">
            <span className="tiny-check">
              <Check size={12} />
            </span>
            {lang === "en"
              ? "Built around your potential"
              : "በእርስዎ ችሎታ ዙሪያ የተገነባ"}
          </div>
          <span className="art-corner">ሥራ</span>
          <span className="art-bottom">ADDIS ABABA · ETHIOPIA</span>
        </div>
      </section>
      <div className="container">
        <div className="demo-strip">
          <span className="status-dot" />
          <strong>{t("demo")}</strong>
          <p>{t("demoNote")}</p>
          <Link href="/account" aria-label={t("privacy")}>
            <ArrowUpRight size={17} />
          </Link>
        </div>
        <section className="section">
          <div className="section-head">
            <div>
              <p className="eyebrow">
                {lang === "en" ? "FIND YOUR DIRECTION" : "አቅጣጫዎን ያግኙ"}
              </p>
              <h2>
                {t("explore")}
                <span className="gold-dot">.</span>
              </h2>
            </div>
            <Link className="inline-link" href="/jobs">
              {t("viewAll")}
              <ArrowUpRight size={17} />
            </Link>
          </div>
          <div className="category-grid">
            {categories.slice(0, 6).map((c, i) => {
              const Icon = catIcons[i];
              return (
                <Link
                  key={c.id}
                  href={`/jobs?cat=${c.id}`}
                  className="category-card"
                >
                  <span className={`category-icon category-${i}`}>
                    <Icon size={21} />
                  </span>
                  <strong>{c[lang]}</strong>
                  <span className="category-count">
                    {
                      state.jobs.filter(
                        (j) => j.cat === c.id && j.status === "open",
                      ).length
                    }{" "}
                    {lang === "en" ? "sample roles" : "የሙከራ ሥራዎች"}
                  </span>
                  <ArrowUpRight size={15} className="category-arrow" />
                </Link>
              );
            })}
          </div>
        </section>
        <section className="section roles-section">
          <div className="section-head">
            <div>
              <p className="eyebrow">
                {lang === "en" ? "THE OPPORTUNITY EDIT" : "የዕድል ስብስብ"}
              </p>
              <h2>{t("curated")}</h2>
              <p className="subtext">{t("featured")}</p>
            </div>
            <Link className="inline-link" href="/jobs">
              {t("viewAll")}
              <ArrowUpRight size={17} />
            </Link>
          </div>
          <div className="jobs-grid">
            {state.jobs
              .filter((j) => j.status === "open")
              .slice(0, 3)
              .map((j) => (
                <JobCard key={j.id} job={j} />
              ))}
          </div>
        </section>
        <section className="path-section">
          <div className="path-copy">
            <span className="small-star">
              <Sparkles size={20} />
            </span>
            <p className="eyebrow">
              {lang === "en"
                ? "MORE HUMAN. MORE POSSIBILITY."
                : "የበለጠ ሰብአዊ። ተጨማሪ ዕድል።"}
            </p>
            <h2>
              {lang === "en"
                ? "Good people.\nGreat beginnings."
                : "ጥሩ ሰዎች።\nታላቅ መጀመሪያ።"}
            </h2>
            <p>
              {lang === "en"
                ? "A clearer path from where you are to where you want to be. Explore both sides of a meaningful connection."
                : "ካሉበት ወደ ሚፈልጉበት የሚወስድ ግልጽ መንገድ። ትርጉም ያለውን ግንኙነት ከሁለቱም ወገን ይሞክሩ።"}
            </p>
          </div>
          <div className="path-options">
            <Link href="/account?role=seeker" className="path-option">
              <span className="path-number">01</span>
              <div>
                <h3>{t("candidate")}</h3>
                <p>
                  {lang === "en"
                    ? "Bring your skills. Find your direction."
                    : "ክህሎትዎን ያምጡ። አቅጣጫዎን ያግኙ።"}
                </p>
              </div>
              <ArrowUpRight size={21} />
            </Link>
            <Link href="/account?role=employer" className="path-option">
              <span className="path-number">02</span>
              <div>
                <h3>{t("employerRole")}</h3>
                <p>
                  {lang === "en"
                    ? "Make room for your next great hire."
                    : "ለቀጣዩ ታላቅ ባለሙያዎ ቦታ ያዘጋጁ።"}
                </p>
              </div>
              <ArrowUpRight size={21} />
            </Link>
          </div>
        </section>
        <section className="closing">
          <p className="eyebrow">SERA · ሥራ</p>
          <h2>
            {lang === "en"
              ? "Your next chapter starts with you."
              : "ቀጣዩ ምዕራፍዎ በእርስዎ ይጀምራል።"}
          </h2>
          <LinkButton href="/jobs">{t("searchBtn")}</LinkButton>
          <div className="closing-detail">
            <MapPin size={13} />
            {lang === "en"
              ? "Rooted in Ethiopia. Built for possibility."
              : "በኢትዮጵያ ሥር የሰደደ። ለዕድል የተገነባ።"}
          </div>
        </section>
      </div>
    </>
  );
}
