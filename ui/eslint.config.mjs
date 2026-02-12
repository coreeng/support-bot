import nextConfig from "eslint-config-next/core-web-vitals";

const eslintConfig = [
  ...nextConfig,
  {
    ignores: ["p2p/**"],
  },
];

export default eslintConfig;
