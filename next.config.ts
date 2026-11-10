import type { NextConfig } from "next";
const config: NextConfig = {
  poweredByHeader: false,
  devIndicators: false,
  experimental: { serverActions: { bodySizeLimit: "4mb" } },
  async redirects() {
    return [
      { source: "/en", destination: "/", permanent: true },
      { source: "/blog", destination: "/guides", permanent: true },
    ];
  },
};
export default config;
