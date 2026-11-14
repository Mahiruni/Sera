import "@fontsource-variable/plus-jakarta-sans";
import "@fontsource-variable/noto-sans-ethiopic";
import type { Metadata, Viewport } from "next";
import { headers } from "next/headers";
import Script from "next/script";
import { Provider } from "@/components/store";
import { SeraVoiceProvider } from "@/components/network/sera-voice-call";
import { memberCard } from "@/lib/activity/members";
import { currentUser,serverClient } from "@/lib/supabase/server";
import { Shell } from "@/components/shell";
import { WebsiteData } from "@/components/public-site";
import { pageMetadata, siteOrigin } from "@/lib/seo";
import "./globals.css";
import "./experience-tokens.css";
import "./footer-x-glass.css";
import "./social.css";
import "./network.css";
import "./activity.css";
import "./page-flow.css";
import "./sera-interaction-system.css";
import "./identity-verification.css";
import "./sera-messaging-excellence.css";
import "./sera-messaging-surfaces.css";
import "./sera-visual-system.css";
export const metadata: Metadata = {
  ...pageMetadata(
    "/",
    "Your Work, Your People, Your Next Chapter",
    "Share your ideas, showcase your work and discover professional opportunities with Sera.",
    false,
  ),
  metadataBase: new URL(siteOrigin),
  title: {
    default: "Sera — Your Work, Your People, Your Next Chapter",
    template: "%s | Sera",
  },
  applicationName: "Sera",
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
  themeColor: [{media:"(prefers-color-scheme: light)",color:"#F6FAF9"},{media:"(prefers-color-scheme: dark)",color:"#0D171A"}],
};
export default async function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const user = await currentUser();
  const [member,ownProfile]=await Promise.all([
    user?memberCard(user.id):null,
    user?.email_confirmed_at?(async()=> (await serverClient()).from("profiles").select("full_name,headline").eq("id",user.id).maybeSingle())():null,
  ]);
  const requestHeaders = await headers();
  const requestLanguage = requestHeaders.get("x-sera-language");
  const language =
    requestLanguage === "am" ? "am" : requestLanguage === "om" ? "om" : "en";
  return (
    <html
      lang={language}
      dir="ltr"
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
        {user?.email_confirmed_at&&<><span hidden id="sera-web-push-account" data-owner={user.id}/><Script src="/sera-web-notifications.js" type="module" strategy="afterInteractive"/></>}
        <Provider initialLocale={language}>
          <SeraVoiceProvider key={user?.email_confirmed_at?user.id:"guest"} userId={user?.email_confirmed_at?user.id:""}>
          <Shell
            profileName={member?.name||ownProfile?.data?.full_name||"SERA member"}
            profileId={user?.email_confirmed_at?user.id:""}
            profileHeadline={ownProfile?.data?.headline||""}
            identityVerified={member?.identityVerified===true}
            profilePhoto={member?.photo||null}
            authenticated={Boolean(user)}
            verified={Boolean(user?.email_confirmed_at)}
          >
            {children}
          </Shell>
          </SeraVoiceProvider>
        </Provider>
      </body>
    </html>
  );
}
