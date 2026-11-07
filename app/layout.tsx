import "@fontsource-variable/plus-jakarta-sans";
import "@fontsource-variable/noto-sans-ethiopic";
import type { Metadata, Viewport } from "next";
import { headers } from "next/headers";
import { Provider } from "@/components/store";
import { currentUser } from "@/lib/supabase/server";
import { Shell } from "@/components/shell";
import { WebsiteData } from "@/components/public-site";
import { pageMetadata, siteOrigin } from "@/lib/seo";
import "./globals.css";
export const metadata: Metadata = {
  ...pageMetadata(
    "/",
    "Opportunities & Education in Ethiopia",
    "Practical career and education guidance for Ethiopia, with a clearly labelled recruitment demo.",
    false,
  ),
  metadataBase: new URL(siteOrigin),
  title: {
    default: "Sera.et — Opportunities & Education in Ethiopia",
    template: "%s | Sera.et",
  },
  applicationName: "Sera.et",
  verification: {
    google: process.env.GOOGLE_SITE_VERIFICATION || undefined,
    other: process.env.BING_SITE_VERIFICATION
      ? { "msvalidate.01": process.env.BING_SITE_VERIFICATION }
      : undefined,
  },
};
export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  viewportFit: "cover",
  themeColor: "#f5f8fc",
};
export default async function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const user = await currentUser();
  const requestHeaders = await headers();
  const requestLanguage = requestHeaders.get("x-sera-language");
  const language =
    requestLanguage === "am" ? "am" : requestLanguage === "ar" ? "ar" : "en";
  return (
    <html
      lang={language}
      dir={language === "ar" ? "rtl" : "ltr"}
      data-scroll-behavior="smooth"
    >
      <body>
        <WebsiteData />
        <Provider initialLocale={language}>
          <Shell authenticated={Boolean(user)}>{children}</Shell>
        </Provider>
      </body>
    </html>
  );
}
