import Link from "next/link";
import {
  ArrowRight,
  ArrowUpRight,
  BookOpen,
  Compass,
  GraduationCap,
  Building2,
} from "lucide-react";
import { guides } from "@/lib/guides";
import { breadcrumbData, jsonLd, siteOrigin } from "@/lib/seo";
import { locales, type LocaleCode } from "@/lib/locales";
export function Breadcrumbs({
  items,
}: {
  items: { name: string; path: string }[];
}) {
  return (
    <>
      <nav className="public-breadcrumbs" aria-label="Breadcrumb">
        <Link href="/">Sera.et</Link>
        {items.map((item) => (
          <span key={item.path}>
            <span aria-hidden="true">/</span>
            <Link href={item.path}>{item.name}</Link>
          </span>
        ))}
      </nav>
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{
          __html: jsonLd(
            breadcrumbData([{ name: "Sera.et", path: "/" }, ...items]),
          ),
        }}
      />
    </>
  );
}
export function PublicLanding({ language = "en" }: { language?: "en" | "am" }) {
  const am = language === "am";
  return (
    <div className="container public-home" lang={language}>
      <div className="public-intro">
        <h1>
          {am ? "ቀጣዩን እርምጃዎን" : "Your next move,"}
          <br />
          <span>{am ? "በግልጽነት ያቅዱ።" : "with a clearer direction."}</span>
        </h1>
        <p>
          {am
            ? "በቀጣሪዎች የተለጠፉ ሥራዎችን ያስሱ፣ በግል ያመልክቱ እና በሙያና በትምህርት መመሪያዎች ቀጣዩን እርምጃ ያቅዱ።"
            : "Explore employer-posted jobs, introduce yourself privately, and plan your next step with practical career and education guidance."}
        </p>
        <div className="public-actions">
          <Link className="button" href="/jobs">
            {am ? "ሥራዎችን ያግኙ" : "Explore jobs"}
            <ArrowRight size={16} />
          </Link>
          <Link className="button secondary" href="/employer">
            {am ? "ሥራ ይለጥፉ" : "For employers"}
            <ArrowUpRight size={16} />
          </Link>
        </div>
        <p className="public-status">
          {am
            ? "መለያዎች፣ በቀጣሪዎች የተለጠፉ ሥራዎች እና የግል ማመልከቻዎች ቀጥታ ይሠራሉ። የትምህርት የሥራ ቦታዎች ገና በእቅድ ላይ ናቸው።"
            : "Accounts, employer-posted jobs and private applications are live. Academic workspaces are still planned."}
        </p>
      </div>
      <section
        className="public-paths"
        aria-label={am ? "ያስሱ" : "Explore Sera.et"}
      >
        {[
          {
            href: "/opportunities",
            Icon: Compass,
            title: am ? "ሥራ እና ዕድሎች" : "Careers & opportunities",
            body: am
              ? "ዕድሎችን ለመፈተሽ እና ማመልከቻ ለማዘጋጀት ግልጽ መነሻ።"
              : "A careful starting point for checking vacancies and preparing focused applications.",
          },
          {
            href: "/education",
            Icon: GraduationCap,
            title: am ? "ትምህርት እና ተማሪዎች" : "Education & students",
            body: am
              ? "የትምህርት ምርጫዎችን ያወዳድሩ፣ ፕሮጀክቶችን እና የጥናት እቅዶችን ያደራጁ።"
              : "Compare study options and organize assignments, revision and student projects.",
          },
          {
            href: "/institutions",
            Icon: Building2,
            title: am ? "ኮሌጆች እና ተቋማት" : "Colleges & institutions",
            body: am
              ? "ዩኒቨርሲቲዎችን፣ ካምፓሶችን እና የስልጠና አቅራቢዎችን በጥንቃቄ ያጥኑ።"
              : "Research universities, campuses and training providers using current official information.",
          },
        ].map(({ href, Icon, title, body }) => (
          <Link href={href} className="panel" key={href}>
            <Icon size={25} />
            <h2>{title}</h2>
            <p>{body}</p>
            <ArrowUpRight size={17} />
          </Link>
        ))}
      </section>
      <section className="public-editorial">
        <h2>
          {am
            ? "ከእርምጃ በፊት ጥሩ ጥያቄዎችን ይጠይቁ።"
            : "Better questions before bigger decisions."}
        </h2>
        <p>
          {am
            ? "የሥራ ማስታወቂያን ሲያነቡ ወይም የትምህርት አቅራቢን ሲያወዳድሩ፣ የመጀመሪያውን ምንጭ እና ቀን ያስቀምጡ። ጊዜ የሚገድበውን መረጃ ከኦፊሴላዊ ምንጭ እንደገና ያረጋግጡ። ሥራ በአሁኑ ጊዜ የተረጋገጡ ክፍት ሥራዎችን፣ የተቋማት ክፍያዎችን ወይም የመግቢያ ቀናትን አያትምም።"
            : "Whether you are reading a vacancy or comparing a course, keep the original source and its date. Recheck deadlines and costs with the responsible organization before acting. Sera.et does not currently publish verified vacancies, institution fees or admissions deadlines. Our guides help you organize the questions and evidence you need."}
        </p>
      </section>
      <GuideCards am={am} />
      <section className="public-release">
        <h2>
          {am
            ? "የሚገኘው እና በእቅድ ላይ ያለው"
            : "Available today. Clear about what comes next."}
        </h2>
        <p>
          {am
            ? "የግል መለያዎች፣ በቀጣሪዎች የተለጠፉ ሥራዎች፣ የተቀመጡ ሥራዎች፣ ማመልከቻዎች እና የማመልከቻ መልዕክቶች አሉ። የሙከራ የሥራ ቦታዎች ለየብቻ ናቸው። የተቋም አስተዳደር እና የትምህርት ሂደቶች ገና በእቅድ ላይ ናቸው።"
            : "Available: private accounts, employer-posted jobs, saved roles, applications and in-app application conversations. Sample workspaces remain separate demonstrations. Institutional management, academic workflows, calling, file conversion and exam handling are still planned."}
        </p>
        <Link className="inline-link" href="/how-it-works">
          {am ? "ሙከራውን ይረዱ" : "Understand the demo"}
          <ArrowRight size={15} />
        </Link>
      </section>
      {am && (
        <p className="translation-draft" lang="en">
          Amharic public content is a translation draft pending native-language
          editorial review. It is not submitted for search indexing.
        </p>
      )}
    </div>
  );
}
export function GuideCards({ am = false }: { am?: boolean }) {
  const titles = [
    "ከማመልከትዎ በፊት የሥራ ዕድልን እንዴት መፈተሽ ይቻላል?",
    "ዩኒቨርሲቲ፣ ኮሌጅ እና የስልጠና ምርጫዎችን ማወዳደር",
    "ለምደባዎች፣ ለፈተናዎች እና ለተማሪ ፕሮጀክቶች ተግባራዊ እቅድ",
  ];
  return (
    <section className="public-guides">
      <div className="section-head">
        <h2>{am ? "ተግባራዊ መመሪያዎች" : "Practical guides"}</h2>
        <Link href="/guides" className="inline-link">
          {am ? "ሁሉንም ይመልከቱ" : "All guides"}
          <ArrowUpRight size={15} />
        </Link>
      </div>
      <div className="guide-grid">
        {guides.map((guide, i) => (
          <Link
            href={`/guides/${guide.slug}`}
            key={guide.slug}
            className="panel"
          >
            <BookOpen size={21} />
            <span>
              {guide.section}
              {am ? " · English" : ""}
            </span>
            <h3>{am ? titles[i] : guide.title}</h3>
            <p>{am ? "ይህ መመሪያ በአሁኑ ጊዜ በእንግሊዝኛ ይገኛል።" : guide.description}</p>
            <ArrowUpRight size={17} />
          </Link>
        ))}
      </div>
    </section>
  );
}
export function LanguageStatus({ locale }: { locale: LocaleCode }) {
  const item = locales.find((l) => l.code === locale)!;
  const arabic = locale === "ar";
  return (
    <div
      className="container page language-status"
      dir={arabic ? "rtl" : "ltr"}
      lang={arabic ? "ar" : "en"}
    >
      <h1>
        {arabic
          ? "العربية قيد الإعداد"
          : `${item.name} translation is in progress`}
      </h1>
      <p className="subtext">
        {arabic
          ? "الترجمة الكاملة ليست جاهزة بعد. المحتوى الحالي متاح بالإنجليزية والأمهرية. لا نعرض المحتوى الإنجليزي على أنه ترجمة عربية مكتملة."
          : `You can choose ${item.name} as your language preference. Full ${item.name} content has not been published or reviewed yet. The app currently falls back to English rather than labelling untranslated content as a completed translation.`}
      </p>
      <div className="public-actions">
        <Link href="/" className="button" lang="en">
          Continue in English
          <ArrowRight size={16} />
        </Link>
        <Link href="/am" className="button secondary" lang="am">
          በአማርኛ ይቀጥሉ
        </Link>
      </div>
      <Link href="/languages" className="inline-link" lang="en">
        All language statuses
        <ArrowUpRight size={15} />
      </Link>
    </div>
  );
}
export function WebsiteData() {
  return (
    <script
      type="application/ld+json"
      dangerouslySetInnerHTML={{
        __html: jsonLd({
          "@context": "https://schema.org",
          "@type": "WebSite",
          "@id": `${siteOrigin}/#website`,
          url: `${siteOrigin}/`,
          name: "Sera.et",
          description:
            "A professional community for sharing ideas, showcasing work and discovering career opportunities.",
          inLanguage: ["en"],
        }),
      }}
    />
  );
}
