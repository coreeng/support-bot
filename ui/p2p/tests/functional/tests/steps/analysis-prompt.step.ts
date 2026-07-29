import { Given, Then } from "@cucumber/cucumber";
import { expect, Route } from "@playwright/test";
import { CustomWorld } from "./custom-world";

// Registered after the hooks.ts defaults, so these take precedence for the same URLs.
Given("the analysis feature is enabled with prompt {string}", async function (this: CustomWorld, prompt: string) {
  // Support engineers trigger an export-status fetch while the analysis-enabled
  // flag is still resolving; an unmocked BFF route 401s and bounces to login.
  await this.page.route("**/api/summary-data/export/**", async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({ running: false, ready: false, error: null }),
    });
  });

  await this.page.route("**/api/knowledge-gaps/enabled", async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({ enabled: true }),
    });
  });

  await this.page.route("**/api/analysis/enabled", async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({ enabled: true }),
    });
  });

  await this.page.route("**/api/analysis/status", async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({ running: false }),
    });
  });

  await this.page.route("**/api/analysis/prompt", async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({ prompt }),
    });
  });

  // The page renders nothing until /api/summary-data/results returns data.
  await this.page.route("**/api/summary-data/results", async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({ knowledgeGaps: [], supportAreas: [] }),
    });
  });
});

Then("the analysis prompt dialog should show {string}", async function (this: CustomWorld, promptText: string) {
  const dialog = this.page.locator('[data-testid="analysis-prompt-dialog"]');
  await expect(dialog).toBeVisible({ timeout: 5000 });
  await expect(dialog).toContainText(promptText);
});
