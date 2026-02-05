import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  output: "standalone",

  // Rewrites API calls so the UI calls the API internally within the cluster,
  // avoiding CORS issues with IAP on internal ingresses.
  async rewrites() {
    return [
      {
        source: '/backend/:path*',
        destination: `${process.env.BACKEND_URL || 'http://localhost:8080'}/:path*`,
      },
    ]
  },

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
