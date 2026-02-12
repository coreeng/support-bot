const REQUIRED_ENV_VARS = ["AUTH_SECRET", "NEXTAUTH_URL", "BACKEND_URL"] as const;

const ENV_ALIASES: Record<string, string> = {
  AUTH_SECRET: "NEXTAUTH_SECRET",
};

function getEnvVar(name: string): string | undefined {
  const value = process.env[name];
  if (value) return value;

  const alias = ENV_ALIASES[name];
  if (alias) {
    const aliasValue = process.env[alias];
    if (aliasValue) {
      console.warn(`  Using deprecated ${alias}, please rename to ${name}`);
      return aliasValue;
    }
  }

  return undefined;
}

export async function register() {
  const missing: string[] = [];

  for (const name of REQUIRED_ENV_VARS) {
    if (!getEnvVar(name)) {
      const alias = ENV_ALIASES[name];
      missing.push(alias ? `${name} (or ${alias})` : name);
    }
  }

  if (missing.length > 0) {
    const message = [
      "",
      "=".repeat(60),
      "Missing required environment variables:",
      "=".repeat(60),
      ...missing.map((name) => `   - ${name}`),
      "",
      "Copy .env.example to .env.local and fill in values:",
      "   cp .env.example .env.local",
      "",
      "Generate AUTH_SECRET with:",
      "   openssl rand -base64 32",
      "=".repeat(60),
      "",
    ].join("\n");

    throw new Error(message);
  }
}
