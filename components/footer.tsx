"use client";
import Link from "next/link";
import { ArrowUpRight } from "lucide-react";
import { Brand } from "./ui";
import { useSera } from "./store";
import type { Key } from "@/lib/i18n";
const groups: { title: Key; links: [string, Key][] }[] = [
  {
    title: "exploreGroup",
    links: [
      ["/opportunities", "opportunities"],
      ["/education", "education"],
      ["/guides", "guides"],
      ["/employer", "employer"],
    ],
  },
  {
    title: "supportGroup",
    links: [
      ["/how-it-works", "howItWorks"],
      ["/help", "help"],
      ["/contact", "contact"],
    ],
  },
  {
    title: "informationGroup",
    links: [
      ["/about", "aboutSera"],
      ["/privacy", "privacyPolicy"],
      ["/terms", "terms"],
    ],
  },
];
export function SiteFooter() {
  const { t, lang } = useSera();
  const year = new Intl.DateTimeFormat("en", {
    year: "numeric",
    timeZone: "Africa/Addis_Ababa",
  }).format(new Date());
  return (
    <footer className="information-footer" lang={lang} dir="ltr">
      <div className="footer-main">
        <div className="footer-intro">
          <Brand small />
          <p>{t("footerDescription")}</p>
          <Link className="footer-demo" href="/demo">
            <span className="status-dot" />
            {t("demo")}
            <ArrowUpRight size={13} />
          </Link>
        </div>
        {groups.map((group) => (
          <nav key={group.title} aria-label={t(group.title)}>
            <h2>{t(group.title)}</h2>
            {group.links.map(([href, key]) => (
              <Link href={href} key={href}>
                {t(key)}
              </Link>
            ))}
          </nav>
        ))}
      </div>
      <div className="footer-bottom">
        <p>© {year} Sera</p>
        <p>{t("footer")}</p>
        <div>
          <Link href="/privacy">{t("privacyPolicy")}</Link>
          <span aria-hidden="true">·</span>
          <Link href="/terms">{t("terms")}</Link>
        </div>
      </div>
    </footer>
  );
}
