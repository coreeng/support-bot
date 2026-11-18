import type { Config } from "jest";
import nextJest from "next/jest.js";

const createJestConfig = nextJest({
  // Provide the path to your Next.js app to load next.config.js and .env files in your test environment
  dir: "./",
});

// Add any custom config to be passed to Jest
const config: Config = {
  coverageProvider: "v8",
  testEnvironment: "jsdom",
  setupFilesAfterEnv: ["<rootDir>/jest.setup.ts"],
  slowTestThreshold: 3,
  silent: true,
  // Transform next-auth since it uses ESM
  transformIgnorePatterns: ["/node_modules/(?!(next-auth|@auth|@panva)/)"],
  // Mock server-only module and auth modules
  moduleNameMapper: {
    "^server-only$": "<rootDir>/__mocks__/server-only.ts",
    "^@/auth$": "<rootDir>/__mocks__/auth.ts",
    "^@/auth.config$": "<rootDir>/__mocks__/auth.config.ts",
  },
};

// next/jest puts its own node_modules ignore pattern first, which would win over the one above, so
// it is replaced here. assistant-ui and its runtime dependencies ship ESM only.
const ESM_PACKAGES = "/node_modules/(?!(next-auth|@auth|@panva|@assistant-ui|assistant-stream|assistant-cloud|safe-content-frame|nanoid)/)";

export default async function jestConfig(): Promise<Config> {
  const resolved = await createJestConfig(config)();
  const otherPatterns = (resolved.transformIgnorePatterns ?? []).filter((pattern) => !pattern.startsWith("/node_modules/"));
  return { ...resolved, transformIgnorePatterns: [ESM_PACKAGES, ...otherPatterns] };
}
