"use client";
import Link from "next/link";
import { ArrowUpRight, ArrowLeft, Compass } from "lucide-react";
import { useSera } from "./store";
import { Heading } from "./ui";
import { informationPages, type InformationSlug } from "@/lib/information";
import { accountInformationPages } from "@/lib/account-information";
import { categories } from "@/lib/model";
export function InformationPage({ page }: { page: InformationSlug }) {
  const { t, lang } = useSera();
  const content =
    page in accountInformationPages
      ? accountInformationPages[page as keyof typeof accountInformationPages]
      : informationPages[page];
  const i = lang === "am" ? 1 : 0;
  return (
    <div className="container page information-page">
      <Link href="/" className="inline-link">
        <ArrowLeft size={15} />
        {t("home")}
      </Link>
      <Heading
        title={content.title[i]}
        description={content.intro[i]}
      />
      <article className="panel information-article">
        {content.sections.map((section, index) => (
          <section key={index}>
            <h2>{section.title[i]}</h2>
            <p>{section.body[i]}</p>
          </section>
        ))}
      </article>
      <nav className="information-related" aria-label={t("informationGroup")}>
        <Link href="/how-it-works">
          {t("howItWorks")}
          <ArrowUpRight size={14} />
        </Link>
        <Link href="/help">
          {t("help")}
          <ArrowUpRight size={14} />
        </Link>
        <Link href="/contact">
          {t("contact")}
          <ArrowUpRight size={14} />
        </Link>
        <Link href="/privacy">
          {t("privacyPolicy")}
          <ArrowUpRight size={14} />
        </Link>
        <Link href="/terms">
          {t("terms")}
          <ArrowUpRight size={14} />
        </Link>
        <Link href="/dashboard">
          {t("account")}
          <ArrowUpRight size={14} />
        </Link>
      </nav>
    </div>
  );
}
export function CategoriesPage() {
  const { state, t, lang } = useSera();
  return (
    <div className="container page">
      <Heading
        title={t("browseCategories")}
        description={t("featured")}
      />
      <div className="information-categories">
        {categories.map((category) => (
          <Link
            key={category.id}
            className="panel"
            href={`/jobs?cat=${category.id}`}
          >
            <Compass size={23} />
            <h2>{category[lang]}</h2>
            <p>
              {
                state.jobs.filter(
                  (job) => job.cat === category.id && job.status === "open",
                ).length
              }{" "}
              {lang === "en" ? "sample roles" : "የሙከራ ሥራዎች"}
            </p>
            <ArrowUpRight size={17} />
          </Link>
        ))}
      </div>
    </div>
  );
}
