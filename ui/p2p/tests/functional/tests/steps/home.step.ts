import { Then, Given } from "@cucumber/cucumber";
import { expect } from "@playwright/test";
import { CustomWorld } from "./custom-world";

const BASE_URL = process.env.SERVICE_ENDPOINT || "http://localhost:3000";

Given("User navigates to the homepage", async function (this: CustomWorld) {
  await this.page.goto(BASE_URL);
});

Then("It should show the banner {string}", async function (this: CustomWorld, expectedAlt: string) {
    const banner = this.page.getByRole("img", { name: expectedAlt });
    await expect(banner).toBeVisible();
});

Then("It should show navigation tabs", async function (this: CustomWorld) {
    // Sidebar navigation sub-items are now links in the Support section
    await expect(this.page.getByRole("link", { name: /^Home$/i })).toBeVisible();
    await expect(this.page.getByRole("link", { name: /^Tickets$/i })).toBeVisible();
    await expect(this.page.getByRole("link", { name: /^Escalations$/i })).toBeVisible();
});
