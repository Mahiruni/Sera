import type { MetadataRoute } from "next";
import {
  publicPages,
  siteOrigin,
  searchEnabled,
  amharicReviewed,
  languageAlternates,
} from "@/lib/seo";
import { guides } from "@/lib/guides";
export default function sitemap(): MetadataRoute.Sitemap {
  if (!searchEnabled) return [];
  return [
    ...publicPages.map((page) => ({
      url: `${siteOrigin}${page.path}`,
      ...(page.path === "/" && amharicReviewed
        ? { alternates: { languages: languageAlternates()! } }
        : {}),
    })),
    ...guides.map((guide) => ({
      url: `${siteOrigin}/guides/${guide.slug}`,
      lastModified: new Date(`${guide.published}T00:00:00Z`),
    })),
    ...(amharicReviewed
      ? [
          {
            url: `${siteOrigin}/am`,
            alternates: { languages: languageAlternates()! },
          },
        ]
      : []),
  ];
}
