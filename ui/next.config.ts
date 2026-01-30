import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  output: "standalone",

  async headers() {
    return [
      {
        // Apply to all routes
        source: '/(.*)',
        headers: [
          {
            key: 'Content-Security-Policy',
            value: "frame-ancestors 'self' https://*.datadoghq.com https://*.datadoghq.eu https://*.datadoghq.dev"
          }
        ]
      }
    ]
  }
};

export default nextConfig;
