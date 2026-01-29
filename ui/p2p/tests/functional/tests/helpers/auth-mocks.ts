import { Page, Route } from '@playwright/test';

/**
 * Mock backend authorization endpoints for functional tests
 */
export interface AuthMockConfig {
    leadershipEmails?: string[];
    supportEmails?: string[];
    l2Teams?: Array<{ label: string; code: string; types: string[] }>;
}

/**
 * Sets up route mocks for all authorization-related backend endpoints
 * 
 * @param page - Playwright page instance
 * @param config - Configuration for mock responses
 * 
 * @example
 * ```typescript
 * await mockAuthorizationEndpoints(page, {
 *   leadershipEmails: ['leader@example.com'],
 *   supportEmails: ['engineer@example.com'],
 *   l2Teams: [{ label: 'Core-platform', code: 'core-platform', types: ['escalation'] }]
 * });
 * ```
 */
export async function mockAuthorizationEndpoints(
    page: Page,
    config: AuthMockConfig = {}
): Promise<void> {
    const {
        leadershipEmails = [],
        supportEmails = [],
        l2Teams = []
    } = config;

    // Mock /team/leadership/members endpoint
    await page.route('**/team/leadership/members', async (route: Route) => {
        await route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify(leadershipEmails)
        });
    });

    // Mock /team/support/members endpoint
    await page.route('**/team/support/members', async (route: Route) => {
        await route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify(supportEmails)
        });
    });

    // Mock /team?type=escalation endpoint
    // Note: Using URL check to handle query params reliably
    await page.route(url => url.pathname.includes('/team') && url.search.includes('type=escalation'), async (route: Route) => {
        await route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify(l2Teams)
        });
    });

    // Fallback catch-all for any escalation type query (e.g., proxied paths)
    await page.route('**/*type=escalation*', async (route: Route) => {
        await route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify(l2Teams)
        });
    });
}

/**
 * Mock a failed authorization endpoint (returns 500)
 */
export async function mockAuthorizationEndpointsError(page: Page): Promise<void> {
    await page.route('**/team/leadership/members', async (route: Route) => {
        await route.fulfill({ status: 500, body: 'Internal Server Error' });
    });

    await page.route('**/team/support/members', async (route: Route) => {
        await route.fulfill({ status: 500, body: 'Internal Server Error' });
    });

    await page.route(url => url.pathname.includes('/team') && url.search.includes('type=escalation'), async (route: Route) => {
        await route.fulfill({ status: 500, body: 'Internal Server Error' });
    });
}

