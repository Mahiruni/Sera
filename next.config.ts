import type { NextConfig } from "next";
const config: NextConfig = {
  poweredByHeader: false,
  devIndicators: false,
  async redirects() {
    return [
      { source: "/en", destination: "/", permanent: true },
      { source: "/blog", destination: "/guides", permanent: true },
    ];
  },
};
export default config;
