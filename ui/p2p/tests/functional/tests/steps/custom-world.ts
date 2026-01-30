import { setWorldConstructor, World } from "@cucumber/cucumber";
import { Browser, BrowserContext, Page, chromium } from "@playwright/test";

// Shared browser instance per worker (for parallel execution)
let sharedBrowser: Browser | null = null;

export class CustomWorld extends World {
  browser!: Browser;
  context!: BrowserContext;
  page!: Page;
  testContext?: any; // For storing test-specific data between steps
  testTickets?: any[]; // For storing ticket data across steps

  async launchBrowser() {
    // Reuse browser if already launched in this worker
    if (sharedBrowser && sharedBrowser.isConnected()) {
      this.browser = sharedBrowser;
      return;
    }
    
    // Use Chromium for better stability in CI/sandbox environments
    sharedBrowser = await chromium.launch({ 
      headless: process.env.PWDEBUG ? false : true,
      slowMo: process.env.PWDEBUG ? 500 : 0,
      args: ['--no-sandbox', '--disable-dev-shm-usage']
    });
    this.browser = sharedBrowser;
  }

  async createPage() {
    this.context = await this.browser.newContext({
      ignoreHTTPSErrors: true,
    });
    this.page = await this.context.newPage();
    
    // Set default timeout
    this.page.setDefaultTimeout(10000);
  }

  async closePage() {
    if (this.page) {
      await this.page.close().catch(() => {});
    }
    if (this.context) {
      await this.context.close().catch(() => {});
    }
  }

  async closeBrowser() {
    // Close the shared browser at the end of the worker
    if (sharedBrowser && sharedBrowser.isConnected()) {
      await sharedBrowser.close().catch(() => {});
      sharedBrowser = null;
    }
  }
}

setWorldConstructor(CustomWorld);