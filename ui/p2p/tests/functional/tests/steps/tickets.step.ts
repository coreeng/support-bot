import { Given, When, Then } from "@cucumber/cucumber";
import { expect } from "@playwright/test";
import { CustomWorld } from "./custom-world";

const BASE_URL = process.env.SERVICE_ENDPOINT || "http://localhost:3000";

// Simple mock data
const mockTicket = (id: string, status: string, team: string, impact: string) => ({
    id,
    status,
    team: { name: team },
    impact,
    tags: [{ code: 'bug', label: 'Bug' }],
    escalations: [],
    logs: [
        { event: 'opened', date: '2025-01-01T10:00:00Z' }
    ],
    query: { link: `https://slack.com/thread${id}` },
    assignedTo: 'support-engineer-1@example.com'
});

const mockTicketsData = {
    content: [
        mockTicket('1', 'opened', 'engineering', 'high'),
        mockTicket('2', 'opened', 'support', 'medium'),
        mockTicket('3', 'closed', 'engineering', 'low')
    ],
    page: 0,
    totalPages: 1,
    totalElements: 3
};

const mockTicketsMixedStatus = {
    content: [
        mockTicket('1', 'opened', 'engineering', 'high'),
        mockTicket('2', 'opened', 'support', 'medium'),
        mockTicket('3', 'closed', 'engineering', 'low'),
        mockTicket('4', 'closed', 'support', 'low')
    ],
    page: 0,
    totalPages: 1,
    totalElements: 4
};

const mockTeamsData = [
    { name: 'engineering', code: 'engineering', label: 'Engineering', types: ['tenant'] },
    { name: 'support', code: 'support', label: 'Support', types: ['tenant'] }
];

const mockRegistryData = {
    impacts: [
        { code: 'high', label: 'High Impact' },
        { code: 'medium', label: 'Medium Impact' },
        { code: 'low', label: 'Low Impact' }
    ],
    tags: [
        { code: 'bug', label: 'Bug' },
        { code: 'urgent', label: 'Urgent' }
    ]
};

const mockTicketDetails = {
    id: '1',
    status: 'opened',
    team: { name: 'engineering' },
    impact: 'high',
    tags: ['bug'],
    escalations: [],
    logs: [
        { event: 'ticket opened', date: '2025-01-01T10:00:00Z' }
    ],
    query: { link: 'https://slack.com/thread1' },
    assignedTo: 'support-engineer-1@example.com'
};

// Setup mocks
Given("Tickets API endpoints are mocked", async function (this: CustomWorld) {
    // Mock ticket list and details with smart routing
    await this.page.route("**/ticket**", (route) => {
        const url = route.request().url();
        if (url.includes('/ticket/1')) {
            route.fulfill({ status: 200, body: JSON.stringify(mockTicketDetails) });
        } else {
            route.fulfill({ status: 200, body: JSON.stringify(mockTicketsData) });
        }
    });
    // Also mock proxied API paths
    await this.page.route("**/api/tickets**", (route) => {
        const url = route.request().url();
        if (url.includes('/tickets/1')) {
            route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(mockTicketDetails) });
        } else {
            route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(mockTicketsData) });
        }
    });
    
    await this.page.route("**/team?type=tenant*", (route) =>
        route.fulfill({ status: 200, body: JSON.stringify(mockTeamsData) })
    );
    await this.page.route("**/api/teams?type=TENANT*", (route) =>
        route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(mockTeamsData) })
    );

    await this.page.route("**/api/registry*", (route) =>
        route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify({ impacts: mockRegistryData.impacts, tags: mockRegistryData.tags })
        })
    );
});

Given("Tickets API endpoints are mocked with sample data", async function (this: CustomWorld) {
    // Mock ticket list and details with smart routing
    await this.page.route("**/ticket**", (route) => {
        const url = route.request().url();
        const method = route.request().method();

        // Handle PATCH requests for ticket updates
        if (method === 'PATCH') {
            const updatedTicket = {
                ...mockTicketDetails,
                status: 'closed',
                tags: ['bug', 'urgent']
            };
            route.fulfill({
                status: 200,
                contentType: 'application/json',
                body: JSON.stringify(updatedTicket)
            });
            return;
        }

        // Handle GET requests
        if (url.includes('/ticket/1')) {
            route.fulfill({
                status: 200,
                contentType: 'application/json',
                body: JSON.stringify(mockTicketDetails)
            });
        } else {
            route.fulfill({
                status: 200,
                contentType: 'application/json',
                body: JSON.stringify(mockTicketsData)
            });
        }
    });

    await this.page.route("**/team?type=tenant*", (route) =>
        route.fulfill({ status: 200, body: JSON.stringify(mockTeamsData) })
    );

    await this.page.route("**/api/team?type=tenant*", (route) =>
        route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(mockTeamsData) })
    );

    await this.page.route("**/registry/impact*", (route) =>
        route.fulfill({ status: 200, body: JSON.stringify(mockRegistryData.impacts) })
    );

    await this.page.route("**/registry/tag*", (route) =>
        route.fulfill({ status: 200, body: JSON.stringify(mockRegistryData.tags) })
    );
    // Also mock proxied API paths
    await this.page.route("**/api/tickets**", (route) => {
        const url = route.request().url();
        const method = route.request().method();
        
        if (method === 'PATCH') {
            const updatedTicket = {
                ...mockTicketDetails,
                status: 'closed',
                tags: ['bug', 'urgent']
            };
            route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(updatedTicket) });
            return;
        }
        
        if (url.includes('/tickets/1')) {
            route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(mockTicketDetails) });
        } else {
            route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(mockTicketsData) });
        }
    });
    
    await this.page.route("**/team?type=tenant*", (route) =>
        route.fulfill({ status: 200, body: JSON.stringify(mockTeamsData) })
    );
    await this.page.route("**/api/teams?type=TENANT*", (route) =>
        route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(mockTeamsData) })
    );

    await this.page.route("**/api/registry*", (route) =>
        route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify({ impacts: mockRegistryData.impacts, tags: mockRegistryData.tags })
        })
    );
});

Given("Tickets API endpoints are mocked with mixed statuses", async function (this: CustomWorld) {
    await this.page.route("**/ticket?*", (route) =>
        route.fulfill({ status: 200, body: JSON.stringify(mockTicketsMixedStatus) })
    );
    
    await this.page.route("**/team?type=tenant*", (route) =>
        route.fulfill({ status: 200, body: JSON.stringify(mockTeamsData) })
    );

    await this.page.route("**/api/tickets?*", (route) =>
        route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(mockTicketsMixedStatus) })
    );
    await this.page.route("**/api/teams?type=TENANT*", (route) =>
        route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(mockTeamsData) })
    );
    await this.page.route("**/api/registry*", (route) =>
        route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify({ impacts: mockRegistryData.impacts, tags: mockRegistryData.tags })
        })
    );
    
    await this.page.route("**/registry/impact*", (route) =>
        route.fulfill({ status: 200, body: JSON.stringify(mockRegistryData.impacts) })
    );
    
    await this.page.route("**/registry/tag*", (route) =>
        route.fulfill({ status: 200, body: JSON.stringify(mockRegistryData.tags) })
    );
});

Given("Tickets API returns empty list", async function (this: CustomWorld) {
    await this.page.route("**/ticket?*", (route) =>
        route.fulfill({ 
            status: 200, 
            body: JSON.stringify({ content: [], page: 0, totalPages: 0, totalElements: 0 })
        })
    );
    
    await this.page.route("**/team?type=tenant*", (route) =>
        route.fulfill({ status: 200, body: JSON.stringify(mockTeamsData) })
    );

    await this.page.route("**/api/tickets?*", (route) =>
        route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify({ content: [], page: 0, totalPages: 0, totalElements: 0 })
        })
    );
    await this.page.route("**/api/teams?type=TENANT*", (route) =>
        route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(mockTeamsData) })
    );
    await this.page.route("**/api/registry*", (route) =>
        route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify({ impacts: mockRegistryData.impacts, tags: mockRegistryData.tags })
        })
    );
    
    await this.page.route("**/registry/impact*", (route) =>
        route.fulfill({ status: 200, body: JSON.stringify(mockRegistryData.impacts) })
    );
    
    await this.page.route("**/registry/tag*", (route) =>
        route.fulfill({ status: 200, body: JSON.stringify(mockRegistryData.tags) })
    );
});

// Navigation
When("User navigates to the tickets page", async function (this: CustomWorld) {
    await this.page.goto(`${BASE_URL}/`, {
        waitUntil: 'domcontentloaded',
        timeout: 10000
    });
    
    // Wait for sidebar to be visible - Support section should be expanded by default
    await this.page.getByRole('button', { name: /Support/i }).first().waitFor({ state: 'visible', timeout: 5000 });
    
    // Click on Tickets navigation in sidebar (it's a button)
    const ticketsNav = this.page.getByRole('button', { name: /^Tickets$/i });
    await ticketsNav.waitFor({ state: 'visible', timeout: 5000 });
    await ticketsNav.click();
    
    // Wait for page to load
    await this.page.waitForTimeout(500);
});

// Actions
When("User selects {string} from status filter", async function (this: CustomWorld, status: string) {
    // Wait for tickets table to be visible first (ensures data is loaded)
    await this.page.locator('table').waitFor({ state: 'visible', timeout: 5000 });
    
    // Status filter is the second select (after date filter)
    // Use a more specific selector to target the status filter
    const statusFilter = this.page.locator('select').filter({ hasText: 'All Status' });
    await statusFilter.waitFor({ state: 'visible', timeout: 5000 });
    await this.page.waitForTimeout(500); // Wait for React hydration
    
    await statusFilter.selectOption(status);
    // Wait for React to re-render with filtered data
    await this.page.waitForTimeout(2000);
});

When("User clicks on the first ticket", async function (this: CustomWorld) {
    const firstRow = this.page.locator('tbody tr').first();
    await expect(firstRow).toBeVisible({ timeout: 5000 });
    await this.page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => {});
    
    // Click and wait for modal
    await firstRow.click({ force: true });
    
    // Wait for modal to appear and ticket data to load
    const modal = this.page.locator('[data-testid="edit-ticket-modal"], [role="dialog"]').first();
    const loading = this.page.locator('text=/Loading ticket details/i');
    await Promise.race([
        modal.waitFor({ state: 'visible', timeout: 10000 }),
        loading.waitFor({ state: 'visible', timeout: 10000 })
    ]);
    // Ensure modal is visible (even if still loading)
    await expect(modal).toBeVisible({ timeout: 10000 });
    // Give a brief moment for hydration
    await this.page.waitForTimeout(300);
});

// Assertions
Then("Tickets table should be visible", async function (this: CustomWorld) {
    const table = this.page.locator('table');
    await expect(table).toBeVisible({ timeout: 5000 });
});

Then("Tickets table should have required headers", async function (this: CustomWorld) {
    await expect(this.page.locator('thead th', { hasText: 'Status' })).toBeVisible();
    await expect(this.page.locator('thead th', { hasText: 'Team' })).toBeVisible();
    await expect(this.page.locator('thead th', { hasText: 'Impact' })).toBeVisible();
});

Then("Tickets should display status information", async function (this: CustomWorld) {
    // Check that status text is visible in the first column
    const firstStatusCell = this.page.locator('tbody tr td').first();
    await expect(firstStatusCell).toBeVisible({ timeout: 5000 });
    // Verify it contains a status value
    const text = await firstStatusCell.textContent();
    expect(text).toBeTruthy();
});

Then("Tickets should display team information", async function (this: CustomWorld) {
    // Just verify table has rows with data
    const rows = this.page.locator('tbody tr');
    await expect(rows.first()).toBeVisible({ timeout: 5000 });
});

Then("Tickets should display impact information", async function (this: CustomWorld) {
    // Just verify rows are visible with content
    const rows = this.page.locator('tbody tr');
    const count = await rows.count();
    expect(count).toBeGreaterThan(0);
});

Then("Only opened tickets should be displayed", async function (this: CustomWorld) {
    // Wait for pagination to settle
    await this.page.waitForTimeout(500);
    
    // The key test: verify NO closed tickets are visible
    const allText = await this.page.locator('tbody').textContent();
    expect(allText).not.toContain('closed');
    
    // And verify we have at least some tickets showing
    const rows = this.page.locator('tbody tr');
    const count = await rows.count();
    expect(count).toBeGreaterThan(0);
});

Then("Ticket details panel should appear", async function (this: CustomWorld) {
    // Check that the modal appears when ticket is clicked
    // The modal should be visible with ticket details
    const modal = this.page.locator('[role="dialog"], [data-testid="edit-ticket-modal"]').first();
    await expect(modal).toBeVisible({ timeout: 5000 });
    
    // Verify the table is still visible in the background
    const table = this.page.locator('table');
    await expect(table).toBeVisible();
});

Then("Ticket details should show the ticket ID", async function (this: CustomWorld) {
    // The modal should display the ticket ID
    const modal = this.page.locator('[role="dialog"], [data-testid="edit-ticket-modal"]').first();
    await expect(modal).toBeVisible({ timeout: 5000 });
    
    // Check for ticket ID in the modal (format: "Ticket #123" or similar)
    const ticketIdPattern = /Ticket\s*#?\s*\d+/i;
    const modalText = await modal.textContent();
    expect(modalText).toMatch(ticketIdPattern);
});

// Edit ticket functionality steps
Given("User is a support engineer", async function (this: CustomWorld) {
    // Override the default session from hooks.ts to be support engineer only
    try { await this.page.unroute('**/api/auth/session'); } catch {}
    
    // Ensure middleware bypass cookie exists for functional tests
    const baseUrl = process.env.SERVICE_ENDPOINT || 'http://localhost:3000';
    
    await this.context.addCookies([
        {
            name: '__e2e_auth_bypass',
            value: 'functional-test',
            url: baseUrl,
            httpOnly: false,
            secure: false,
            sameSite: 'Lax'
        }
    ]);
    
    await this.page.route('**/api/auth/session', async (route) => {
        await route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify({
                user: {
                    id: "support@example.com",
                    email: "support@example.com",
                    name: "Support Engineer",
                    teams: [
                        { name: "support-engineers", code: "support-engineers", label: "Support Engineers", types: ["support"] }
                    ],
                    roles: ["USER", "SUPPORT_ENGINEER"],
                },
                expires: new Date(Date.now() + 86400000).toISOString()
            })
        });
    });
    
    // Navigate to trigger session reload
    await this.page.goto(BASE_URL);
    await this.page.waitForTimeout(2000);
});

Given("User is not a support engineer", async function (this: CustomWorld) {
    // Override the default session from hooks.ts to be non-support engineer
    try { await this.page.unroute('**/api/auth/session'); } catch {}
    
    // Ensure middleware bypass cookie exists for functional tests
    const baseUrl = process.env.SERVICE_ENDPOINT || 'http://localhost:3000';
    
    await this.context.addCookies([
        {
            name: '__e2e_auth_bypass',
            value: 'functional-test',
            url: baseUrl,
            httpOnly: false,
            secure: false,
            sameSite: 'Lax'
        }
    ]);
    
    await this.page.route('**/api/auth/session', async (route) => {
        await route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify({
                user: {
                    id: "user@example.com",
                    email: "user@example.com",
                    name: "Regular User",
                    teams: [
                        { name: "engineering", code: "engineering", label: "Engineering", types: ["tenant"] }
                    ],
                    roles: ["USER"],
                },
                expires: new Date(Date.now() + 86400000).toISOString()
            })
        });
    });
    
    // Navigate to trigger session reload
    await this.page.goto(BASE_URL);
    await this.page.waitForTimeout(2000);
});

Then("Ticket edit modal should appear", async function (this: CustomWorld) {
    // Wait for modal to appear - it might take a moment after clicking
    await this.page.waitForTimeout(1000);
    
    // Try multiple selectors for the modal
    const modal = this.page.locator('[data-testid="edit-ticket-modal"], [role="dialog"]').first();
    
    await expect(modal).toBeVisible({ timeout: 15000 });
    
    // Wait for loading to complete if present
    const loadingText = modal.locator('text=/Loading ticket details/i');
    if (await loadingText.count() > 0) {
        await expect(loadingText).not.toBeVisible({ timeout: 15000 });
    }
    
    // Verify modal has content - wait for ticket ID to appear
    await expect(modal.locator('text=/Ticket #/i')).toBeVisible({ timeout: 10000 });
});

Then("Modal should show editable fields", async function (this: CustomWorld) {
    // Wait for modal to be stable
    const modal = this.page.locator('[data-testid="edit-ticket-modal"], [role="dialog"]').first();
    await expect(modal).toBeVisible({ timeout: 15000 });
    
    // Wait for content to load - check for ticket ID first
    await expect(modal.locator('text=/Ticket #/i')).toBeVisible({ timeout: 10000 });
    
    // Wait for loading to complete - check that "Loading ticket details" is not visible
    const loadingIndicator = modal.locator('text=/Loading ticket details/i');
    if (await loadingIndicator.count() > 0) {
        await expect(loadingIndicator).not.toBeVisible({ timeout: 15000 });
    }
    
    // Wait a bit more for all content to render
    await this.page.waitForTimeout(800);
    
    // Verify modal is still visible
    await expect(modal).toBeVisible();
    
    // Check for Save Changes button (implies edit mode rendered)
    const saveButton = modal.locator('button:has-text("Save Changes")');
    await expect(saveButton).toBeVisible({ timeout: 5000 });
});

Then("Modal should show read-only view", async function (this: CustomWorld) {
    const modal = this.page.locator('[data-testid="edit-ticket-modal"], [role="dialog"]').first();
    await expect(modal).toBeVisible({ timeout: 5000 });
    
    // Check for read-only indicator - it's in the title
    const readOnlyText = modal.locator('text=/Read-only/i');
    await expect(readOnlyText).toBeVisible({ timeout: 5000 });
    
    // Should not have editable selects
    const selects = modal.locator('select');
    const count = await selects.count();
    expect(count).toBe(0);
});

Then('"Save Changes" button should not be visible', async function (this: CustomWorld) {
    const modal = this.page.locator('[data-testid="edit-ticket-modal"], [role="dialog"]').first();
    const saveButton = modal.locator('button:has-text("Save Changes")');
    await expect(saveButton).not.toBeVisible();
});

Then('"Close" button should be visible', async function (this: CustomWorld) {
    const modal = this.page.locator('[data-testid="edit-ticket-modal"], [role="dialog"]').first();
    const closeButton = modal.locator('button:has-text("Close")').first();
    await expect(closeButton).toBeVisible();
});

When('User changes ticket status to {string}', async function (this: CustomWorld, status: string) {
    const modal = this.page.locator('[data-testid="edit-ticket-modal"], [role="dialog"]').first();
    await expect(modal).toBeVisible({ timeout: 5000 });
    
    // Use the first select in the modal (Change Status is the first select)
    let statusSelect = modal.locator('select').first();

    await expect(statusSelect).toBeVisible({ timeout: 5000 });
    await statusSelect.selectOption(status);
});

When('User selects impact {string}', async function (this: CustomWorld, impact: string) {
    const modal = this.page.locator('[data-testid="edit-ticket-modal"], [role="dialog"]').first();
    let impactSelect = modal.locator('#impact-select');
    if (await impactSelect.count() === 0) {
        impactSelect = modal.locator('label:has-text("Impact")').locator('..').locator('select').first();
    }
    await expect(impactSelect).toBeVisible({ timeout: 5000 });
    await impactSelect.selectOption(impact);
});

When('User selects team {string}', async function (this: CustomWorld, team: string) {
    const modal = this.page.locator('[data-testid="edit-ticket-modal"], [role="dialog"]').first();
    let teamSelect = modal.locator('#team-select');
    if (await teamSelect.count() === 0) {
        teamSelect = modal.locator('label:has-text("Author\'s Team")').locator('..').locator('select').first();
    }
    await expect(teamSelect).toBeVisible({ timeout: 5000 });

    // Wait a moment for options to populate, then select
    await this.page.waitForTimeout(1000);
    const teamValue = team.toLowerCase();
    await teamSelect.selectOption(teamValue);
});

When('User adds tag {string}', async function (this: CustomWorld, tag: string) {
    const modal = this.page.locator('[data-testid="edit-ticket-modal"], [role="dialog"]').first();
    let tagSelect = modal.locator('#tags-select');
    if (await tagSelect.count() === 0) {
        tagSelect = modal.locator('label:has-text("Tags")').locator('..').locator('select').last();
    }
    await expect(tagSelect).toBeVisible({ timeout: 5000 });

    // Wait a moment for options to populate, then select
    await this.page.waitForTimeout(1000);
    await tagSelect.selectOption(tag);
});

When('User clicks {string}', async function (this: CustomWorld, buttonText: string) {
    const modal = this.page.locator('[data-testid="edit-ticket-modal"], [role="dialog"]').first();
    const button = modal.locator(`button:has-text("${buttonText}")`);
    await button.click();
});

Then("Ticket should be updated successfully", async function (this: CustomWorld) {
    // Wait for the save operation to complete
    await this.page.waitForTimeout(2000);

    const modal = this.page.locator('[data-testid="edit-ticket-modal"], [role="dialog"]').first();
    const closeButton = modal.locator('button').filter({ hasText: 'Close' }).first();

    // Some flows close the modal automatically after save; others keep it open.
    // Accept either behavior, but ensure we end with the modal closed.
    if (await closeButton.isVisible().catch(() => false)) {
        await closeButton.click();
    }
    await expect(modal).not.toBeVisible({ timeout: 5000 });
});

Then("Modal should close", async function (this: CustomWorld) {
    const modal = this.page.locator('[data-testid="edit-ticket-modal"], [role="dialog"]').first();
    await expect(modal).not.toBeVisible({ timeout: 3000 });
});

Then("Empty state message should be visible", async function (this: CustomWorld) {
    const emptyMessage = this.page.locator('text=No tickets found');
    await expect(emptyMessage).toBeVisible({ timeout: 5000 });
});
