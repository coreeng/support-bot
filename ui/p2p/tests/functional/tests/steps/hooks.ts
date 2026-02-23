import { After, Before, setDefaultTimeout } from "@cucumber/cucumber";
import { CustomWorld } from "./custom-world";

// Set default timeout to 15 seconds for all steps
setDefaultTimeout(15000);

Before(async function (this: CustomWorld) {
  // Launch browser if not already launched (reuses within worker)
  await this.launchBrowser();
  // Create a new page/context for this scenario
  await this.createPage();
  
  // Clear any route handlers from previous scenarios
  await this.page.unroute('**/*');
  
  // Allow Next.js internal routes to pass through
  await this.page.route('**/_next/**', route => route.continue());
  
  // Mock authentication for functional tests.
  // Default persona: Leadership + Support Engineer = full access to all features.
  const mockSessionToken = {
    user: {
      id: "functional-test@example.com",
      email: "functional-test@example.com",
      name: "Functional Test User",
      teams: [
        { name: "test-support-leadership", code: "test-support-leadership", label: "Test Support Leadership", types: ["leadership"] },
        { name: "test-support-engineers", code: "test-support-engineers", label: "Test Support Engineers", types: ["support"] },
        { name: "team-a", code: "team-a", label: "Team A", types: ["tenant"] }
      ],
      roles: ["USER", "LEADERSHIP", "SUPPORT_ENGINEER"],
    },
    expires: new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString()
  };

  const baseUrl = process.env.SERVICE_ENDPOINT || 'http://localhost:3000';

  // Bypass the server-side JWE validation in middleware so Playwright's
  // browser-level /api/auth/session mock can take effect.
  await this.context.addCookies([
    {
      name: '__e2e_auth_bypass',
      value: 'functional-test',
      url: baseUrl,
      httpOnly: false,
      secure: false,
      sameSite: 'Lax' as const
    }
  ]);
  
  // Mock the NextAuth session API endpoint to return our mock session
  // This is the PROPER way to handle auth in functional tests
  // No need for NODE_ENV=test bypasses in the app code
  await this.page.route('**/api/auth/session', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(mockSessionToken)
    });
  });
  
  // Mock backend API endpoints that the app calls for user/team data.
  // The /api/teams endpoint accepts ?type=escalation or ?type=tenant.
  await this.page.route('**/api/teams**', async (route) => {
    const url = new URL(route.request().url());
    const typeParam = url.searchParams.get('type');
    
    if (typeParam === 'escalation') {
      // Return empty escalation teams for default scenarios (overridden in specific tests)
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([])
      });
    } else if (typeParam === 'tenant') {
      // Return tenant teams with code and label fields
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          { code: "team-a", label: "Team A", types: ["tenant"] }
        ])
      });
    } else {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([])
      });
    }
  });

  // Mock /api/users/support endpoint to return support members list
  await this.page.route('**/api/users/support', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([
        { userId: "U123456", displayName: "support-engineer-1@example.com" },
        { userId: "U789012", displayName: "support-engineer-2@example.com" }
      ])
    });
  });

  // Mock /api/assignment/enabled endpoint
  await this.page.route('**/api/assignment/enabled', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ enabled: true })
    });
  });

  await this.page.route('**/api/knowledge-gaps/enabled', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ enabled: false })
    });
  });

  await this.page.route('**/api/registry', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        impacts: [{ code: "high", label: "High" }, { code: "medium", label: "Medium" }, { code: "low", label: "Low" }],
        tags: [{ code: "bug", label: "Bug" }]
      })
    });
  });

  await this.page.route('**/api/escalations**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        content: [],
        page: 0,
        totalPages: 0,
        totalElements: 0
      })
    });
  });

  await this.page.route('**/api/tickets**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        content: [
          {
            id: 1,
            ticketId: 'ticket-1',
            team: { name: 'Core-platform' },
            status: 'opened',
            impact: 'high',
            escalations: [],
            assignedTo: 'support-engineer-1@example.com'
          },
          {
            id: 2,
            ticketId: 'ticket-2',
            team: { name: 'Team A' },
            status: 'closed',
            impact: 'medium',
            escalations: [],
            assignedTo: null
          }
        ],
        page: 0,
        totalPages: 1,
        totalElements: 2
      })
    });
  });
});

After(async function (this: CustomWorld) {
  // Only close the page, browser stays open for next scenario
  await this.closePage();
});