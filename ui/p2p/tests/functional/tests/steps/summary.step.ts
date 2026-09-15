import { Given, Then, When } from "@cucumber/cucumber";
import { expect, Route } from "@playwright/test";
import { CustomWorld } from "./custom-world";

type SummaryWindow = { from: string; to: string };

// Registered after the hooks.ts defaults, so these take precedence for the same URLs.
Given("the summary page is enabled", async function (this: CustomWorld) {
  this.testContext = this.testContext || {};
  this.testContext.summaryRequests = [] as SummaryWindow[];

  await this.page.route("**/api/summary/enabled", async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({ enabled: true }),
    });
  });

  // Echoes the requested window back so the page's window strip reflects what it asked for,
  // and records every window requested so a step can assert on it.
  await this.page.route("**/api/summary?*", async (route: Route) => {
    const params = new URL(route.request().url()).searchParams;
    const window: SummaryWindow = { from: params.get("from") ?? "", to: params.get("to") ?? "" };
    this.testContext.summaryRequests.push(window);
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        ...window,
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
});

// Pins the browser's clock so the presets resolve to known dates. Only `Date` is frozen; timers
// keep running, so polling and React Query behave as usual. Must run before the first navigation.
Given("the current date is {string}", async function (this: CustomWorld, isoDateTime: string) {
  await this.page.clock.setFixedTime(new Date(isoDateTime));
});

When("user selects the {string} summary window", async function (this: CustomWorld, label: string) {
  await this.page.locator('[data-testid="summary-date-filter"]').click();
  await this.page.getByRole("option", { name: label, exact: true }).click();
});

Then("the summary should be requested from {string} to {string}", async function (this: CustomWorld, from: string, to: string) {
  await expect
    .poll(() => (this.testContext?.summaryRequests as SummaryWindow[] | undefined) ?? [], { timeout: 10_000 })
    .toContainEqual({ from, to });
});

Then("the summary window should show {string}", async function (this: CustomWorld, text: string) {
  await expect(this.page.locator('[data-testid="summary-window"]')).toContainText(text, { timeout: 10_000 });
});
