import { notFound } from "next/navigation";
import { PublicLanding, LanguageStatus } from "@/components/public-site";
import { locales, isLocale } from "@/lib/locales";
import { pageMetadata, amharicReviewed } from "@/lib/seo";
export function generateStaticParams() {
  return locales
    .filter((l) => l.code !== "en")
    .map((l) => ({ locale: l.code }));
}
export const dynamicParams = false;
export async function generateMetadata({
  params,
}: {
  params: Promise<{ locale: string }>;
}) {
  const { locale } = await params;
  if (!isLocale(locale) || locale === "en") notFound();
  return locale === "am"
    ? pageMetadata(
        "/am",
        "በኢትዮጵያ ዕድሎች፣ ትምህርት እና ተግባራዊ መመሪያዎች",
        "ስለ ሥራ፣ ትምህርት እና የተማሪ ፕሮጀክቶች የሥራ መመሪያዎችን ያስሱ። ይህ ትርጉም ለኤዲቶሪያል ግምገማ በመጠባበቅ ላይ ነው።",
        amharicReviewed,
      )
    : pageMetadata(
        `/${locale}`,
        `${locales.find((l) => l.code === locale)!.name} Language Availability`,
        "Translation availability and honest English fallback information for Sera.et.",
      );
}
export default async function Page({
  params,
}: {
  params: Promise<{ locale: string }>;
}) {
  const { locale } = await params;
  if (!isLocale(locale) || locale === "en") notFound();
  return locale === "am" ? (
    <PublicLanding language="am" />
  ) : (
    <LanguageStatus locale={locale} />
  );
}
