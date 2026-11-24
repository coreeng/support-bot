import { Given, Then } from "@cucumber/cucumber";
import { expect, Route } from "@playwright/test";
import { CustomWorld } from "./custom-world";

type AnalysisWindow = { from: string; to: string };

const conflict = {
  ticketId: "101",
  channelId: "C123",
  queryTs: "1710000001.000100",
  raisedAt: "2026-08-25T10:00:00Z",
  classification: "incorrect",
  question: "How do I rotate a deploy key?",
  answerSummary: "Support said only the platform team can rotate keys.",
  tags: ["deploy-key"],
  docPaths: ["guides/keys.md"],
  evidenceQuote: "To rotate a key, run the rotate command.",
  rationale: "The page says tenants rotate keys; support said they cannot.",
  productArea: "guides",
  productAreas: ["guides"],
  docsVersion: "a1b2c3d4e5f60718293a4b5c6d7e8f9012345678",
  analysedAt: "2026-09-09T12:00:00Z",
};

// Registered after the hooks.ts defaults, so these take precedence for the same URLs.
// The first reply is a run that has only just started, as on a first visit to a window; every
// later one has finished it, so the page reaches the results only by polling.
Given("the documentation analysis page is enabled", async function (this: CustomWorld) {
  this.testContext = this.testContext || {};
  this.testContext.docAnalysisRequests = [] as AnalysisWindow[];

  await this.page.route("**/api/documentation-analysis/enabled", async (route: Route) => {
    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ enabled: true }) });
  });

  await this.page.route("**/api/documentation-analysis?*", async (route: Route) => {
    const params = new URL(route.request().url()).searchParams;
    const window: AnalysisWindow = { from: params.get("from") ?? "", to: params.get("to") ?? "" };
    const requests = this.testContext.docAnalysisRequests as AnalysisWindow[];
    requests.push(window);
    const firstVisit = requests.length === 1;
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        ...window,
        counts: firstVisit
          ? { total: 1, correct: 0, missing: 0, incorrect: 0, notApplicable: 0, unclassified: 1, failed: 0 }
          : { total: 1, correct: 0, missing: 0, incorrect: 1, notApplicable: 0, unclassified: 0, failed: 0 },
        productAreas: firstVisit ? [] : [{ area: "guides", total: 1, correct: 0, missing: 0, incorrect: 1, notApplicable: 0 }],
        citedPages: firstVisit ? [] : [{ page: "guides/keys.md", count: 1, correct: 0, incorrect: 1, ticketIds: ["101"] }],
        missingByArea: [],
        results: firstVisit ? [] : [conflict],
        run: firstVisit ? { state: "running", progress: { ...window, done: 0, total: 0 } } : { state: "idle" },
        documentation: { version: conflict.docsVersion, refreshedAt: "2026-09-09T06:00:00Z" },
      }),
    });
  });
});

Then(
  "the documentation analysis should be requested from {string} to {string}",
  async function (this: CustomWorld, from: string, to: string) {
    await expect
      .poll(() => (this.testContext?.docAnalysisRequests as AnalysisWindow[] | undefined) ?? [], { timeout: 10_000 })
      .toContainEqual({ from, to });
  }
);

Then(
  "the documentation analysis should list {string} as {string}",
  async function (this: CustomWorld, question: string, classification: string) {
    const result = this.page.locator('[data-testid="doc-analysis-result"]', { hasText: question });
    await expect(result).toBeVisible({ timeout: 15_000 });
    await expect(result).toContainText(classification);
  }
);

Then("no documentation analysis run should be shown", async function (this: CustomWorld) {
  await expect(this.page.locator('[data-testid="doc-analysis-run"]')).toHaveCount(0, { timeout: 10_000 });
});

Then("the documentation analysis should not have been requested", async function (this: CustomWorld) {
  expect((this.testContext?.docAnalysisRequests as AnalysisWindow[] | undefined) ?? []).toHaveLength(0);
});
