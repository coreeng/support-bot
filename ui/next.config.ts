import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  output: "standalone",

  images: {
    remotePatterns: [
      {
        protocol: "https",
        hostname: "**",
      },
    ],
  },

  experimental: {
    serverActions: {
      // Empty array = same-origin only (Origin must match Host/X-Forwarded-Host)
      // This relies on proper ingress header configuration for vanity domains
      allowedOrigins: [],
    },
  },

  async headers() {
    return [
      {
        source: "/(.*)",
        headers: [
          {
            key: "Content-Security-Policy",
            value:
              "frame-ancestors 'self' https://*.datadoghq.com https://*.datadoghq.eu https://*.datadoghq.dev",
          },
        ],
      },
    ];
  },
};

export default nextConfig;
