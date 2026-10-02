import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  output: "standalone",

  // Next 16.3 type-checks every file tsconfig.json includes, test files among them; the build
  // checks the app only, as 16.2 did. Jest and the editor still use tsconfig.json.
  typescript: {
    tsconfigPath: "tsconfig.build.json",
  },

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
            value: "frame-ancestors 'self' https://*.datadoghq.com https://*.datadoghq.eu https://*.datadoghq.dev",
          },
        ],
      },
    ];
  },
};

export default nextConfig;
