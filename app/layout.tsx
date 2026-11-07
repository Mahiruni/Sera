import "@fontsource-variable/dm-sans";
import "@fontsource-variable/manrope";
import "@fontsource-variable/noto-sans-ethiopic";
import type { Metadata, Viewport } from "next";
import { Provider } from "@/components/store";
import { Shell } from "@/components/shell";
import "./globals.css";
export const metadata: Metadata = {
  title: { default: "Sera ሥራ — Your next chapter", template: "%s · Sera ሥራ" },
  description:
    "A thoughtfully built Ethiopian recruitment marketplace. Explore the Sera interactive demonstration in English and Amharic.",
  robots: { index: false, follow: false },
};
export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  viewportFit: "cover",
  themeColor: "#f7f8f2",
};
export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en" data-scroll-behavior="smooth">
      <body>
        <Provider>
          <Shell>{children}</Shell>
        </Provider>
      </body>
    </html>
  );
}
