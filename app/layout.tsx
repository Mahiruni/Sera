import "@fontsource-variable/plus-jakarta-sans";
import "@fontsource-variable/noto-sans-ethiopic";
import type { Metadata, Viewport } from "next";
import { headers } from "next/headers";
import { Provider } from "@/components/store";
import { memberCards } from "@/lib/activity/members";
import { currentUser } from "@/lib/supabase/server";
import { Shell } from "@/components/shell";
import { WebsiteData } from "@/components/public-site";
import { pageMetadata, siteOrigin } from "@/lib/seo";
import "./globals.css";
import "./experience-tokens.css";
import "./footer-x-glass.css";
import "./social.css";
import "./network.css";
import "./activity.css";
export const metadata: Metadata = {
  ...pageMetadata(
    "/",
    "Your Work, Your People, Your Next Chapter",
    "Share your ideas, showcase your work and discover professional opportunities with Sera.et.",
    false,
  ),
  metadataBase: new URL(siteOrigin),
  title: {
    default: "Sera.et — Your Work, Your People, Your Next Chapter",
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
  const identity = user?.email_confirmed_at
    ? (await memberCards([user.id])).get(user.id)
    : undefined;
  const requestHeaders = await headers();
  const requestLanguage = requestHeaders.get("x-sera-language");
  const language =
    requestLanguage === "am" ? "am" : requestLanguage === "ar" ? "ar" : "en";
  return (
    <html
      lang={language}
      dir={language === "ar" ? "rtl" : "ltr"}
      data-scroll-behavior="smooth"
      suppressHydrationWarning
    >
      <head>
        <script
          dangerouslySetInnerHTML={{
            __html: `try{var seraThemeChoice=localStorage.getItem("sera-theme");document.documentElement.dataset.seraTheme=seraThemeChoice==="dark"?"dark":"light"}catch(e){}`,
          }}
        />
      </head>
      <body>
        <WebsiteData />
        <Provider initialLocale={language}>
          <Shell
            authenticated={Boolean(user)}
            identity={identity}
            verified={Boolean(user?.email_confirmed_at)}
          >
            {children}
          </Shell>
        </Provider>
      </body>
    </html>
  );
}
