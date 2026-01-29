import { Given, When, Then } from '@cucumber/cucumber';
import { expect, Route } from '@playwright/test';
import { CustomWorld } from './custom-world';
import {
    mockAuthorizationEndpoints,
    mockAuthorizationEndpointsError
} from '../helpers/auth-mocks';
import {
    LeadershipMember,
    SupportEngineer,
    RegularTenant,
    EscalationTeamMember,
    getL2TeamsForPersona,
    getLeadershipEmailsForPersona,
    getSupportEmailsForPersona
} from '../helpers/personas';

const BASE_URL = process.env.SERVICE_ENDPOINT || "http://localhost:3000";

// Background
Given('I am on the home page', async function (this: CustomWorld) {
    await this.page.goto(BASE_URL);
});

// Authorization Setup
Given('the backend returns leadership members including {string}', async function (this: CustomWorld, email: string) {
    // Store for later use in mock setup
    this.testContext = this.testContext || {};
    this.testContext.leadershipEmails = [email];
    // Leadership users typically don't have specific team memberships in AD,
    // but for UI purposes we'll give them a placeholder
    this.testContext.userTeams = [{ name: 'Leadership', groupRefs: [], types: ['leadership'] }];
});

Given('the backend returns support members including {string}', async function (this: CustomWorld, email: string) {
    this.testContext = this.testContext || {};
    this.testContext.supportEmails = [email];
    // Support Engineers typically don't have specific team memberships in AD,
    // but for UI purposes we'll give them a placeholder
    this.testContext.userTeams = [{ name: 'Support Engineers', groupRefs: [], types: ['support'] }];
});

Given('the backend returns L2 support teams', async function (this: CustomWorld) {
    this.testContext = this.testContext || {};
    this.testContext.l2Teams = [
        { label: 'Core-platform', code: 'core-platform', types: ['escalation'] },
        { label: 'Core-networking', code: 'core-networking', types: ['escalation'] }
    ];
});

Given('the backend returns L2 support teams including {string}', async function (this: CustomWorld, teamName: string) {
    this.testContext = this.testContext || {};
    this.testContext.l2Teams = [
        { label: teamName, code: teamName, types: ['escalation'] }
    ];
});

Given('user {string} is NOT in leadership or support lists', async function (this: CustomWorld, email: string) {
    this.testContext = this.testContext || {};
    this.testContext.leadershipEmails = [];
    this.testContext.supportEmails = [];
});

Given('user {string} is member of {string}', async function (this: CustomWorld, email: string, teamName: string) {
    this.testContext = this.testContext || {};
    const l2Labels = (this.testContext.l2Teams || []).map((t: any) => t.label);
    const types = l2Labels.includes(teamName) ? ['escalation'] : ['tenant'];
    this.testContext.userTeams = [{ name: teamName, groupRefs: [], types }];
});

Given('user {string} is member of {string} and {string}', async function (this: CustomWorld, email: string, team1: string, team2: string) {
    this.testContext = this.testContext || {};
    const l2Labels = (this.testContext.l2Teams || []).map((t: any) => t.label);
    const typeFor = (team: string) => l2Labels.includes(team) ? ['escalation'] : ['tenant'];
    this.testContext.userTeams = [
        { name: team1, groupRefs: [], types: typeFor(team1) },
        { name: team2, groupRefs: [], types: typeFor(team2) }
    ];
});

Given('the backend authorization endpoints return 500 error', async function (this: CustomWorld) {
    await mockAuthorizationEndpointsError(this.page);
});

Given('user has no teams', async function (this: CustomWorld) {
    this.testContext = this.testContext || {};
    this.testContext.userEmail = 'notenant@example.com';
    this.testContext.userTeams = [];
    this.testContext.isEscalation = false;
    this.testContext.leadershipEmails = [];
    this.testContext.supportEmails = [];
    this.testContext.l2Teams = [];
});

// User Actions
When('user {string} logs in', async function (this: CustomWorld, email: string) {
    // Set up authorization endpoint mocks
    const testContext = this.testContext || {};

    // Remove default session route from hooks to allow scenario-specific session
    try { await this.page.unroute('**/api/auth/session'); } catch {}
    await this.context.clearCookies();
    
    await mockAuthorizationEndpoints(this.page, {
        leadershipEmails: testContext.leadershipEmails || [],
        supportEmails: testContext.supportEmails || [],
        l2Teams: testContext.l2Teams || []
    });

    // Mock the /user endpoint
    await this.page.route('**/user*', async (route: Route) => {
        await route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify({
                email,
                teams: (testContext.userTeams || [{ name: 'team-a', groupRefs: [], types: ['tenant'] }]).map((t: any) => {
                    const hasTypes = Array.isArray(t.types) && t.types.length > 0
                    // Compare codes with codes (user teams are now in code format)
                    const l2TeamCodes = (testContext.l2Teams || []).map((lt: any) => lt.code)
                    const inferredTypes = hasTypes
                        ? t.types
                        : l2TeamCodes.includes(t.name)
                            ? ['escalation']
                            : (isLeadership ? ['leadership'] : isSupportEngineer ? ['support'] : ['tenant'])
                    return { ...t, types: inferredTypes }
                })
            })
        });
    });

    // Mock escalations endpoint (even if empty) so escalation widgets render
    await this.page.route('**/escalation*', async (route: Route) => {
        await route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify({ content: [], page: 0, totalPages: 1, totalElements: 0 })
        })
    })

    // Mock NextAuth session
    const isLeadership = (testContext.leadershipEmails || []).includes(email);
    const isSupportEngineer = (testContext.supportEmails || []).includes(email);
    const userTeamNames = (testContext.userTeams || [{ name: 'team-a' }]).map((t: any) => t.name);
    // Compare codes with codes (user teams are now in code format)
    const l2TeamCodes = (testContext.l2Teams || []).map((t: any) => t.code);
    const isEscalation = userTeamNames.some((team: string) => l2TeamCodes.includes(team));

    await this.context.addCookies([
        {
            name: 'next-auth.session-token',
            value: 'mock-token',
            domain: 'localhost',
            path: '/',
            httpOnly: true,
            sameSite: 'Lax',
            expires: Math.floor(Date.now() / 1000) + 86400
        }
    ]);

    await this.page.route('**/api/auth/session', async (route: Route) => {
        await route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify({
                user: {
                    email,
                    name: 'Test User',
                    teams: (testContext.userTeams || [{ name: 'team-a', groupRefs: [], types: ['tenant'] }]).map((t: any) => {
                        const hasTypes = Array.isArray(t.types) && t.types.length > 0
                        // Compare codes with codes (user teams are now in code format)
                        const l2TeamCodes = (testContext.l2Teams || []).map((lt: any) => lt.code)
                        const inferredTypes = hasTypes
                            ? t.types
                            : l2TeamCodes.includes(t.name)
                                ? ['escalation']
                                : (isLeadership ? ['leadership'] : isSupportEngineer ? ['support'] : ['tenant'])
                        return { ...t, types: inferredTypes }
                    }),
                    isLeadership,
                    isEscalation,
                    isSupportEngineer
                },
                expires: new Date(Date.now() + 86400000).toISOString()
            })
        });
    });

    // Navigate to trigger session load
    await this.page.goto(BASE_URL);
    await this.page.waitForTimeout(1500); // Wait for session and context to fully load
    
    // Ensure Support menu is expanded (it should be by default, but verify)
    const supportButton = this.page.getByRole('button', { name: /Support/i }).first();
    await supportButton.waitFor({ state: 'visible', timeout: 5000 });
});

When('user selects {string} from team dropdown', async function (this: CustomWorld, teamName: string) {
    // Try to locate the team selector; if not present, skip (user may already be scoped)
    // Use data-testid to reliably find the team selector (not the date filter dropdown)
    const dropdown = this.page.locator('select[data-testid="team-selector"]');
    const found = await dropdown.count();
    if (found === 0) return;

    const rendered = await dropdown.isVisible();
    if (!rendered) return;

    await dropdown.selectOption(teamName);
    await this.page.waitForTimeout(3000);

    const selectedValue = await dropdown.inputValue();
    if (selectedValue !== teamName) {
        throw new Error(`Team selection failed: expected ${teamName}, got ${selectedValue}`);
    }
});

When('user navigates to {string}', async function (this: CustomWorld, tabName: string) {
    const button = this.page.getByRole('button', { name: new RegExp(tabName, 'i') });
    await button.click();
    await this.page.waitForTimeout(500);
});

// Assertions
Then('user should see {string} navigation button', async function (this: CustomWorld, buttonText: string) {
    const button = this.page.getByRole('button', { name: new RegExp(buttonText, 'i') });
    await expect(button).toBeVisible({ timeout: 5000 });
});

Then('user should NOT see {string} navigation button', async function (this: CustomWorld, buttonText: string) {
    const button = this.page.getByRole('button', { name: new RegExp(buttonText, 'i') });
    const isVisible = await button.isVisible().catch(() => false);
    expect(isVisible).toBe(false);
});

Then('user should see {string} section', async function (this: CustomWorld, sectionText: string) {
    // Allow data fetch/render time for stats/escalations widgets
    await this.page.waitForTimeout(2500);
    // Debug helpers
    const sessionData = await this.page.evaluate(() => fetch('/api/auth/session').then(r => r.json()).catch(() => null));
    const selectedTeam = await this.page.locator('select[data-testid="team-selector"]').evaluate((el: HTMLSelectElement) => el.value).catch(() => null);
    const pageContent = await this.page.content();
    const escalationsTeamsResp = await this.page.evaluate(() => fetch('/team?type=escalation').then(r => r.json()).catch(() => null));
    const section = this.page.getByText(sectionText);
    await expect(section).toBeVisible({ timeout: 10000 });
});

Then('total tickets count should be {string}', async function (this: CustomWorld, expected: string) {
    // Wait briefly for cards to render
    await this.page.waitForTimeout(1500);
    const card = this.page.getByText(/Total Tickets/i).first();
    await card.waitFor({ state: 'visible', timeout: 5000 });
    const cardText = (await card.textContent()) || '';
    const match = cardText.match(/(\d+)/);
    const value = match ? parseInt(match[1], 10) : 0;
    expect(value).toBe(parseInt(expected, 10));
});

Then('user should NOT see {string} section', async function (this: CustomWorld, sectionText: string) {
    // Wait for any UI updates to complete after team selection
    await this.page.waitForTimeout(1000);
    
    const section = this.page.getByText(sectionText);
    const isVisible = await section.isVisible().catch(() => false);
    expect(isVisible).toBe(false);
});

Then('user should see {string} table', async function (this: CustomWorld, tableTitle: string) {
    // Wait for data to load (L2 teams and escalations)
    await this.page.waitForTimeout(3000);
    
    // Debug: Check session data
    const sessionData = await this.page.evaluate(() => {
        return fetch('/api/auth/session').then(r => r.json()).catch(() => null);
    });
    
    // Debug: Check selected team from dropdown
    const selectedTeam = await this.page.locator('select[data-testid="team-selector"]').evaluate((el: HTMLSelectElement) => el.value).catch(() => null);
    
    // Debug: Check if component rendered at all
    const pageContent = await this.page.content();
    const hasText = pageContent.includes(tableTitle);
    
    // Also check for loading/error states
    const loadingText = await this.page.getByText('Loading...').isVisible().catch(() => false);
    const errorText = await this.page.getByText('Error loading escalations').isVisible().catch(() => false);
    
    // Debug: Check if "Escalations We Are Handling" section exists (related component)
    const hasEscalationsSection = pageContent.includes('Escalations We Are Handling');
    
    const table = this.page.getByText(tableTitle);
    await expect(table).toBeVisible({ timeout: 10000 });
});

Then('user should NOT see {string} table', async function (this: CustomWorld, tableTitle: string) {
    const table = this.page.getByText(tableTitle);
    const isVisible = await table.isVisible().catch(() => false);
    expect(isVisible).toBe(false);
});

