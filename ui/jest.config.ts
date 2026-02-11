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
  transformIgnorePatterns: [
    "/node_modules/(?!(next-auth|@auth|@panva)/)",
  ],
  // Mock server-only module and auth modules
  moduleNameMapper: {
    "^server-only$": "<rootDir>/__mocks__/server-only.ts",
    "^@/auth$": "<rootDir>/__mocks__/auth.ts",
    "^@/auth.config$": "<rootDir>/__mocks__/auth.config.ts",
  },
};

// createJestConfig is exported this way to ensure that next/jest can load the Next.js config which is async
export default createJestConfig(config);