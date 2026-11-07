import type { Metadata } from "next";
export const DEFAULT_ORIGIN = "https://sera-dusky-seven.vercel.app";
export function validatedOrigin(
  value: string | undefined,
  customDomainVerified = process.env.SERA_CUSTOM_DOMAIN_VERIFIED === "true",
) {
  if (!value) return DEFAULT_ORIGIN;
  try {
    const url = new URL(value);
    if (
      url.protocol !== "https:" ||
      url.username ||
      url.password ||
      url.pathname !== "/" ||
      url.search ||
      url.hash
    )
      throw Error("Invalid public origin");
    if (
      (url.hostname === "sera.et" || url.hostname.endsWith(".sera.et")) &&
      !customDomainVerified
    )
      throw Error("Custom domain ownership must be verified");
    return url.origin;
  } catch {
    throw Error(
      "SITE_URL must be a verified HTTPS origin without a path or credentials",
    );
  }
}
export const siteOrigin = validatedOrigin(process.env.SITE_URL);
export const searchEnabled =
  process.env.NODE_ENV === "production" &&
  process.env.VERCEL_ENV !== "preview" &&
  process.env.SERA_SEO_INDEXING !== "off";
export const amharicReviewed = process.env.SERA_AM_REVIEWED === "true";
export const publicPages = [
  {
    path: "/",
    title: "Opportunities, Education & Practical Guides in Ethiopia",
    description:
      "Explore Sera.et: practical career and education guides for Ethiopia, with a clearly labelled recruitment demo and a roadmap for student and institution workspaces.",
  },
  {
    path: "/about",
    title: "About Sera.et — Opportunities and Education",
    description:
      "Learn what Sera.et offers today, how its demonstration works, and what remains planned before live recruitment and education services.",
  },
  {
    path: "/how-it-works",
    title: "How Sera.et Works — Explore the Demo",
    description:
      "Understand discovery, saved roles, candidate profiles and employer workflows in the Sera.et browser-only demonstration.",
  },
  {
    path: "/opportunities",
    title: "Career Opportunities & Job-Search Guidance in Ethiopia",
    description:
      "Plan a careful job search, check opportunity details and prepare applications. Read practical guidance and explore the clearly labelled Sera.et demo.",
  },
  {
    path: "/education",
    title: "Education Planning Guides for Students in Ethiopia",
    description:
      "Compare study options, prepare questions about admissions and fees, and plan assignments and projects with Sera.et’s practical student guidance.",
  },
  {
    path: "/institutions",
    title: "University, College & Training Institution Research Guide",
    description:
      "A practical starting point for researching universities, colleges, campuses and training providers. Sera.et does not yet publish a verified institution directory.",
  },
  {
    path: "/guides",
    title: "Career & Education Guides for Ethiopia",
    description:
      "Read original Sera.et checklists for verifying opportunities, comparing study options and organizing academic projects. No paid placement or guaranteed outcomes.",
  },
] as const;
export const languageAlternates = () =>
  amharicReviewed
    ? {
        en: `${siteOrigin}/`,
        am: `${siteOrigin}/am`,
        "x-default": `${siteOrigin}/`,
      }
    : undefined;
export function pageMetadata(
  path: string,
  title: string,
  description: string,
  eligible = false,
): Metadata {
  const index = eligible && searchEnabled;
  return {
    title: {
      absolute: title.includes("Sera.et") ? title : `${title} | Sera.et`,
    },
    description,
    alternates: {
      canonical: `${siteOrigin}${path}`,
      languages:
        path === "/" || path === "/am" ? languageAlternates() : undefined,
    },
    robots: {
      index,
      follow: true,
      googleBot: {
        index,
        follow: true,
        "max-image-preview": "large",
        "max-snippet": -1,
        "max-video-preview": -1,
      },
    },
    openGraph: {
      type: "website",
      siteName: "Sera.et",
      title: `${title} | Sera.et`,
      description,
      url: `${siteOrigin}${path}`,
      locale: path === "/am" ? "am_ET" : "en_ET",
      images: [
        {
          url: `${siteOrigin}/opengraph-image`,
          width: 1200,
          height: 630,
          alt: "Sera.et — opportunities, education and practical guidance",
        },
      ],
    },
    twitter: {
      card: "summary_large_image",
      title: `${title} | Sera.et`,
      description,
      images: [`${siteOrigin}/opengraph-image`],
    },
  };
}
export function publicMetadata(path: string): Metadata {
  const page = publicPages.find((p) => p.path === path);
  if (!page) throw Error("Unknown public page");
  return pageMetadata(path, page.title, page.description, true);
}
export function jsonLd(value: unknown) {
  return JSON.stringify(value).replace(/</g, "\\u003c");
}
export function breadcrumbData(items: { name: string; path: string }[]) {
  return {
    "@context": "https://schema.org",
    "@type": "BreadcrumbList",
    itemListElement: items.map((item, index) => ({
      "@type": "ListItem",
      position: index + 1,
      name: item.name,
      item: `${siteOrigin}${item.path}`,
    })),
  };
}
