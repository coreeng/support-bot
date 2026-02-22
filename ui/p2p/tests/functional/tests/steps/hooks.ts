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
  
  // Mock authentication by setting NextAuth session cookie
  // This bypasses the login requirement for functional tests
  // User is both Leadership AND Support Engineer to ensure full access to all features
  const mockSessionToken = {
    user: {
      email: "functional-test@example.com",
      name: "Functional Test User",
      teams: [
        { name: "test-support-leadership", groupRefs: [], types: ["leadership"] },
        { name: "test-support-engineers", groupRefs: [], types: ["support"] },
        { name: "team-a", groupRefs: [], types: ["tenant"] }
      ],
      isLeadership: true,
      isEscalationTeam: false,
      isSupportEngineer: true,
      actualEscalationTeams: [],
      escalationGroupName: null,
      escalationGroupRef: null
    },
    expires: new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString() // 24 hours from now
  };
  
  // Set the session cookie that NextAuth uses
  const baseUrl = process.env.SERVICE_ENDPOINT || 'http://localhost:3000';
  const cookieDomain = new URL(baseUrl).hostname || 'localhost';

  await this.context.addCookies([
    {
      name: 'next-auth.session-token',
      value: 'mock-session-token-for-testing',
      domain: cookieDomain,
      path: '/',
      httpOnly: true,
      secure: false,
      sameSite: 'Lax'
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
  
  // Mock backend API endpoints that the app calls for user/team data
  // The /api/team endpoint accepts ?type=escalation or ?type=tenant
  await this.page.route('**/api/team**', async (route) => {
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
      // Return tenant teams — must include `name` to match the Team interface used by useTenantTeams()
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          { name: "team-a", code: "team-a", label: "Team A", types: ["tenant"] }
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

  // Mock /user/support endpoint to return support members list
  await this.page.route('**/user/support', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([
        { userId: "U123456", displayName: "support-engineer-1@example.com" },
        { userId: "U789012", displayName: "support-engineer-2@example.com" }
      ])
    });
  });

  // Mock /assignment/enabled endpoint
  await this.page.route('**/assignment/enabled', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ enabled: true })
    });
  });

  // Mock /user endpoint to align with frontend role/type expectations
  await this.page.route('**/user', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        email: "functional-test@example.com",
        teams: [
          { label: "Test Support Leadership", code: "support-leadership", types: ["leadership"] },
          { label: "Test Support Engineers", code: "support-engineers", types: ["support"] },
          { label: "Team A", code: "team-a", types: ["tenant"] },
        ]
      })
    });
  });
  
  await this.page.route('**/registry/impact*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([{ code: "high", label: "High" }, { code: "medium", label: "Medium" }, { code: "low", label: "Low" }])
    });
  });
  
  await this.page.route('**/registry/tag*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([{ code: "bug", label: "Bug" }])
    });
  });

  // Mock escalation endpoint - required for escalation widgets
  await this.page.route('**/escalation**', async (route) => {
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

  // Mock tickets endpoint - required for StatsPage to render
  await this.page.route('**/ticket*', async (route) => {
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

  // NOTE: Authorization endpoint mocks (/team/leadership/members, /team/support/members, /team?type=escalation)
  // and user-info mocks are test-specific and should be set up in individual test steps
});

After(async function (this: CustomWorld) {
  // Only close the page, browser stays open for next scenario
  await this.closePage();
});