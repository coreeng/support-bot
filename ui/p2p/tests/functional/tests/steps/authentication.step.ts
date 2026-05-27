import { Given, Then, When } from "@cucumber/cucumber";
import { expect, Route } from "@playwright/test";
import { CustomWorld } from "./custom-world";

const BASE_URL = process.env.SERVICE_ENDPOINT || "http://localhost:3000";

// Setup for no session
Given("I have no active session", async function (this: CustomWorld) {
  // Clear all cookies to simulate no session
  await this.context.clearCookies();

  // Mock session endpoint to return 401
  await this.page.route("**/api/auth/session", async (route: Route) => {
    await route.fulfill({
      status: 401,
      contentType: "application/json",
      body: JSON.stringify({ error: "Unauthorized" }),
    });
  });
});

// Setup for authenticated user
Given("I am logged in as a user", async function (this: CustomWorld) {
  // This is already set up in hooks.ts Before hook
  // Just verify we have the session cookie
  const cookies = await this.context.cookies();
  const hasAuthBypass = cookies.some((c) => c.name === "__e2e_auth_bypass");
  expect(hasAuthBypass).toBe(true);
});

// Navigation Actions
When("I try to access the tickets page", async function (this: CustomWorld) {
  await this.page.goto(`${BASE_URL}/tickets`, { waitUntil: "domcontentloaded" });
  await this.page.waitForTimeout(1000);
});

When("I visit the login page with callback URL {string}", async function (this: CustomWorld, callbackUrl: string) {
  await this.page.goto(`${BASE_URL}/login?callbackUrl=${encodeURIComponent(callbackUrl)}`, {
    waitUntil: "domcontentloaded",
  });
  // Wait longer for client-side redirect to complete
  await this.page.waitForTimeout(2000);
});

When("I try to visit the login page", async function (this: CustomWorld) {
  await this.page.goto(`${BASE_URL}/login`, { waitUntil: "domcontentloaded" });
  await this.page.waitForTimeout(1000);
});

When("I navigate to the home page", async function (this: CustomWorld) {
  await this.page.goto(`${BASE_URL}/`, { waitUntil: "domcontentloaded" });
  await this.page.waitForTimeout(1000);
});

// Assertions
Then("I should be redirected to the login page", async function (this: CustomWorld) {
  // Wait for navigation to complete
  await this.page.waitForTimeout(2000);

  const currentUrl = this.page.url();
  const url = new URL(currentUrl);

  expect(url.pathname).toBe("/login");
});

Then("the callback URL should be {string}", async function (this: CustomWorld, expectedCallback: string) {
  const currentUrl = this.page.url();
  const url = new URL(currentUrl);
  const callbackUrl = url.searchParams.get("callbackUrl");

  expect(callbackUrl).toBe(expectedCallback);
});

Then("I should be redirected to the home page", async function (this: CustomWorld) {
  // Wait for navigation/redirect to complete
  await this.page.waitForTimeout(2000);

  const currentUrl = this.page.url();
  const url = new URL(currentUrl);

  // Should be redirected to home page
  expect(url.pathname).toBe("/");
});

Then("the current URL should contain {string}", async function (this: CustomWorld, expectedSubstring: string) {
  const currentUrl = this.page.url();
  expect(currentUrl).toContain(expectedSubstring);
});

Then("I should see the home page", async function (this: CustomWorld) {
  // Wait for the page to load
  await this.page.waitForTimeout(1000);

  const currentUrl = this.page.url();
  const url = new URL(currentUrl);

  // Should be on home page (root path)
  expect(url.pathname).toBe("/");
});

Then("I should see {string} navigation button", async function (this: CustomWorld, buttonText: string) {
  const button = this.page.getByRole("button", { name: new RegExp(buttonText, "i") });
  await expect(button).toBeVisible({ timeout: 5000 });
});

Then("I should be redirected to {string}", async function (this: CustomWorld, expectedPath: string) {
  // Wait for navigation/redirect to complete
  await this.page.waitForTimeout(2000);

  const currentUrl = this.page.url();
  const url = new URL(currentUrl);

  expect(url.pathname).toBe(expectedPath);
});

Then("the page should load without errors", async function (this: CustomWorld) {
  // Just verify the page loaded and didn't crash
  // The page might redirect (if authenticated) or stay on login (if not)
  // Either way is fine - we just want to ensure no errors
  await this.page.waitForTimeout(500);

  // Check that we're not on an error page
  const currentUrl = this.page.url();
  expect(currentUrl).not.toContain("/error");
  expect(currentUrl).not.toContain("/404");
});
