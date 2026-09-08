import { Given, Then, When } from "@cucumber/cucumber";
import { expect, Route } from "@playwright/test";
import { CustomWorld } from "./custom-world";

// Registered after the hooks.ts defaults, so these take precedence for the same URLs.
Given(
  "the summary page is enabled with classification prompt {string} and summary prompt {string}",
  async function (this: CustomWorld, classificationPrompt: string, summaryPrompt: string) {
    await this.page.route("**/api/summary/enabled", async (route: Route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ enabled: true }),
      });
    });

    // The page renders nothing until /api/summary returns; the dialog needs no real figures.
    await this.page.route("**/api/summary?*", async (route: Route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          from: "2026-08-18",
          to: "2026-08-31",
          totalTickets: 0,
          classifiedTickets: 0,
          unclassifiedTickets: 0,
          drivers: [],
          categories: [],
          knowledgeGaps: [],
          features: [],
          teams: [],
          products: [],
          summary: { state: "ready", content: "All quiet in this window." },
        }),
      });
    });

    await this.page.route("**/api/analysis/prompt", async (route: Route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ prompt: classificationPrompt }),
      });
    });

    await this.page.route("**/api/summary/prompt", async (route: Route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ prompt: summaryPrompt }),
      });
    });
  }
);

When("user selects the {string} prompt", async function (this: CustomWorld, promptLabel: string) {
  await this.page.locator('[data-testid="prompt-dialog-kind"]').click();
  await this.page.getByRole("option", { name: promptLabel }).click();
});

Then("the prompt dialog should show {string}", async function (this: CustomWorld, promptText: string) {
  const dialog = this.page.locator('[data-testid="prompt-dialog"]');
  await expect(dialog).toBeVisible({ timeout: 5000 });
  await expect(dialog).toContainText(promptText);
});
