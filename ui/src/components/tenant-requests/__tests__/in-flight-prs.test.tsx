import React from 'react'
import { render, screen, fireEvent, act } from '@testing-library/react'
import InFlightPrsTab from '../in-flight-prs'
import type { InFlightPr } from '../../../lib/types/dashboard'

const mockUseInFlightPrs = jest.fn()

jest.mock('../../../lib/hooks', () => ({
    useInFlightPrs: (...args: unknown[]) => mockUseInFlightPrs(...args),
}))

// Fixed "now" so SLA time calculations are deterministic
const NOW = new Date('2026-04-02T12:00:00Z').getTime()

function makePr(overrides: Partial<InFlightPr> = {}): InFlightPr {
    return {
        githubRepo: 'org/service-a',
        prNumber: 42,
        prUrl: 'https://github.com/org/service-a/pull/42',
        status: 'OPEN',
        waitingOn: 'TEAM',
        prCreatedAt: new Date(NOW - 2 * 86400 * 1000).toISOString(), // 2 days ago
        slaDeadline: new Date(NOW + 24 * 3600 * 1000).toISOString(), // 24h from now
        slaRemainingSeconds: null,
        lastReviewAt: null,
        owningTeam: 'platform',
        owningTeamLabel: 'Platform Team',
        ticketChannelId: 'C1234567890',
        ticketQueryTs: '1234567890.123456',
        escalatedAt: null,
        hasSla: true,
        ...overrides,
    }
}

function makePrs(count: number): InFlightPr[] {
    return Array.from({ length: count }, (_, i) =>
        makePr({
            githubRepo: `org/service-${String.fromCharCode(97 + i)}`,
            prNumber: i + 1,
            prUrl: `https://github.com/org/service-${String.fromCharCode(97 + i)}/pull/${i + 1}`,
            owningTeam: i % 2 === 0 ? 'platform' : 'payments',
            owningTeamLabel: i % 2 === 0 ? 'Platform Team' : 'Payments Team',
        })
    )
}

describe('InFlightPrsTab', () => {
    beforeEach(() => {
        jest.clearAllMocks()
        jest.spyOn(Date, 'now').mockReturnValue(NOW)
        mockUseInFlightPrs.mockReturnValue({ data: [], isLoading: false, error: null })
    })

    afterEach(() => {
        jest.restoreAllMocks()
    })

    describe('Rendering', () => {
        it('should render section heading', () => {
            render(<InFlightPrsTab />)

            expect(screen.getByRole('heading', { name: 'In-Flight PRs' })).toBeInTheDocument()
        })

        it('should show loading indicator', () => {
            mockUseInFlightPrs.mockReturnValue({ data: undefined, isLoading: true, error: null })

            render(<InFlightPrsTab />)

            expect(screen.getByText('Loading...')).toBeInTheDocument()
        })

        it('should show error message on fetch failure', () => {
            mockUseInFlightPrs.mockReturnValue({ data: undefined, isLoading: false, error: new Error('Network error') })

            render(<InFlightPrsTab />)

            expect(screen.getByText('Failed to load data — please try again')).toBeInTheDocument()
        })

        it('should show empty message when no PRs', () => {
            render(<InFlightPrsTab />)

            expect(screen.getByText('No in-flight PRs')).toBeInTheDocument()
        })

        it('should show search-specific empty message when filter has no matches', () => {
            mockUseInFlightPrs.mockReturnValue({ data: [makePr()], isLoading: false, error: null })

            render(<InFlightPrsTab />)

            fireEvent.change(screen.getByPlaceholderText('Filter PRs, repos or teams...'), {
                target: { value: 'nonexistent' },
            })

            expect(screen.getByText('No PRs match your search')).toBeInTheDocument()
        })

        it('should render table headers', () => {
            mockUseInFlightPrs.mockReturnValue({ data: [makePr()], isLoading: false, error: null })

            render(<InFlightPrsTab />)

            ;['PR', 'Status', 'Waiting On', 'SLA', 'Age', 'Last Review', 'Team', 'Slack'].forEach(header => {
                expect(screen.getByText(header)).toBeInTheDocument()
            })
        })
    })

    describe('PR Row', () => {
        it('should render PR link with short repo name and number', () => {
            mockUseInFlightPrs.mockReturnValue({ data: [makePr()], isLoading: false, error: null })

            render(<InFlightPrsTab />)

            const link = screen.getByRole('link', { name: /service-a#42/ })
            expect(link).toHaveAttribute('href', 'https://github.com/org/service-a/pull/42')
        })

        it('should render OPEN status badge', () => {
            mockUseInFlightPrs.mockReturnValue({ data: [makePr({ status: 'OPEN' })], isLoading: false, error: null })

            render(<InFlightPrsTab />)

            expect(screen.getByText('Open')).toBeInTheDocument()
        })

        it('should render CHANGES_REQUESTED status as human-readable label', () => {
            mockUseInFlightPrs.mockReturnValue({
                data: [makePr({ status: 'CHANGES_REQUESTED' })],
                isLoading: false,
                error: null,
            })

            render(<InFlightPrsTab />)

            expect(screen.getByText('Changes Requested')).toBeInTheDocument()
        })

        it('should render APPROVED status badge', () => {
            mockUseInFlightPrs.mockReturnValue({ data: [makePr({ status: 'APPROVED' })], isLoading: false, error: null })

            render(<InFlightPrsTab />)

            expect(screen.getByText('Approved')).toBeInTheDocument()
        })

        it('should render ESCALATED status badge', () => {
            mockUseInFlightPrs.mockReturnValue({ data: [makePr({ status: 'ESCALATED' })], isLoading: false, error: null })

            render(<InFlightPrsTab />)

            expect(screen.getByText('Escalated')).toBeInTheDocument()
        })

        it('should render Slack thread link when channelId and queryTs are present', () => {
            mockUseInFlightPrs.mockReturnValue({
                data: [makePr({ ticketChannelId: 'C999', ticketQueryTs: '111.222' })],
                isLoading: false,
                error: null,
            })

            render(<InFlightPrsTab />)

            const link = screen.getByRole('link', { name: /Thread/ })
            expect(link).toHaveAttribute('href', 'https://slack.com/archives/C999/p111222')
        })

        it('should render dash when Slack link fields are absent', () => {
            mockUseInFlightPrs.mockReturnValue({
                data: [makePr({ ticketChannelId: '', ticketQueryTs: '' })],
                isLoading: false,
                error: null,
            })

            render(<InFlightPrsTab />)

            expect(screen.queryByRole('link', { name: /Thread/ })).not.toBeInTheDocument()
        })

        it('should render owner team label', () => {
            mockUseInFlightPrs.mockReturnValue({
                data: [makePr({ owningTeamLabel: 'Core Infrastructure' })],
                isLoading: false,
                error: null,
            })

            render(<InFlightPrsTab />)

            expect(screen.getAllByText('Core Infrastructure').length).toBeGreaterThanOrEqual(1)
        })
    })

    describe('SLA Display', () => {
        it('should show green style for deadline more than 4 hours away', () => {
            // 24h from NOW
            mockUseInFlightPrs.mockReturnValue({
                data: [makePr({ slaDeadline: new Date(NOW + 24 * 3600 * 1000).toISOString(), slaRemainingSeconds: null })],
                isLoading: false,
                error: null,
            })

            const { container } = render(<InFlightPrsTab />)

            expect(container.querySelector('.text-emerald-700')).toBeInTheDocument()
        })

        it('should show amber style for deadline within 4 hours', () => {
            // 2h from NOW
            mockUseInFlightPrs.mockReturnValue({
                data: [makePr({ slaDeadline: new Date(NOW + 2 * 3600 * 1000).toISOString(), slaRemainingSeconds: null })],
                isLoading: false,
                error: null,
            })

            const { container } = render(<InFlightPrsTab />)

            expect(container.querySelector('.text-amber-700')).toBeInTheDocument()
        })

        it('should show breached label for past deadline', () => {
            // 2 days ago
            mockUseInFlightPrs.mockReturnValue({
                data: [makePr({ slaDeadline: new Date(NOW - 2 * 86400 * 1000).toISOString(), slaRemainingSeconds: null })],
                isLoading: false,
                error: null,
            })

            render(<InFlightPrsTab />)

            expect(screen.getByText(/Breached 2d ago/)).toBeInTheDocument()
        })

        it('should show paused label when slaRemainingSeconds is set and slaDeadline is null', () => {
            mockUseInFlightPrs.mockReturnValue({
                data: [makePr({ slaRemainingSeconds: 3600 * 5, slaDeadline: null })],
                isLoading: false,
                error: null,
            })

            render(<InFlightPrsTab />)

            expect(screen.getByText(/Paused \(5\.0h remaining\)/)).toBeInTheDocument()
        })

        it('should show No SLA badge for a no-SLA PR', () => {
            mockUseInFlightPrs.mockReturnValue({
                data: [makePr({ hasSla: false, slaDeadline: null, slaRemainingSeconds: null })],
                isLoading: false,
                error: null,
            })

            render(<InFlightPrsTab />)

            // "No SLA" appears in the stat card label AND the table SLA cell badge
            expect(screen.getAllByText('No SLA').length).toBeGreaterThanOrEqual(2)
        })

        it('should show SLA data missing badge when hasSla=true but both SLA fields are null', () => {
            jest.spyOn(console, 'error').mockImplementation(() => {})
            mockUseInFlightPrs.mockReturnValue({
                data: [makePr({ hasSla: true, slaDeadline: null, slaRemainingSeconds: null })],
                isLoading: false,
                error: null,
            })

            render(<InFlightPrsTab />)

            expect(screen.getByText('SLA data missing')).toBeInTheDocument()
        })

        it('should not count hasSla=true with null SLA fields as a breach', () => {
            jest.spyOn(console, 'error').mockImplementation(() => {})
            mockUseInFlightPrs.mockReturnValue({
                data: [makePr({ hasSla: true, slaDeadline: null, slaRemainingSeconds: null })],
                isLoading: false,
                error: null,
            })

            const { container } = render(<InFlightPrsTab />)

            // SLA Breached card must stay green (from-emerald), not turn red (from-rose)
            // rose gradient only appears when breached > 0
            expect(container.querySelector('[class*="from-rose"]')).not.toBeInTheDocument()
        })
    })

    describe('Stat Cards', () => {
        it('should count waiting-on totals correctly', () => {
            mockUseInFlightPrs.mockReturnValue({
                data: [
                    makePr({ waitingOn: 'TEAM' }),
                    makePr({ waitingOn: 'TEAM' }),
                    makePr({ waitingOn: 'TENANT' }),
                    makePr({ waitingOn: 'MERGE' }),
                ],
                isLoading: false,
                error: null,
            })

            render(<InFlightPrsTab />)

            const statValues = screen.getAllByText(/^\d+$/).filter(el => el.className.includes('text-3xl'))
            const values = statValues.map(el => Number(el.textContent))
            expect(values).toContain(4) // total
            expect(values).toContain(2) // waiting on team
            expect(values).toContain(1) // waiting on tenant
            expect(values).toContain(1) // waiting on merge
        })

        it('should count SLA breached correctly', () => {
            mockUseInFlightPrs.mockReturnValue({
                data: [
                    makePr({ slaDeadline: new Date(NOW - 3600 * 1000).toISOString() }), // breached
                    makePr({ slaDeadline: new Date(NOW + 3600 * 1000).toISOString() }), // not breached
                ],
                isLoading: false,
                error: null,
            })

            render(<InFlightPrsTab />)

            const statValues = screen.getAllByText(/^\d+$/).filter(el => el.className.includes('text-3xl'))
            const values = statValues.map(el => Number(el.textContent))
            expect(values).toContain(1) // 1 breached
        })

        it('should show red gradient on SLA Breached card when breached > 0', () => {
            mockUseInFlightPrs.mockReturnValue({
                data: [makePr({ slaDeadline: new Date(NOW - 3600 * 1000).toISOString() })],
                isLoading: false,
                error: null,
            })

            const { container } = render(<InFlightPrsTab />)

            expect(container.querySelector('[class*="from-rose"]')).toBeInTheDocument()
        })

        it('should count no-SLA PRs in the No SLA stat card', () => {
            mockUseInFlightPrs.mockReturnValue({
                data: [
                    makePr({ hasSla: true, slaDeadline: new Date(NOW + 24 * 3600 * 1000).toISOString() }),
                    makePr({ hasSla: false, slaDeadline: null, slaRemainingSeconds: null }),
                    makePr({ hasSla: false, slaDeadline: null, slaRemainingSeconds: null }),
                ],
                isLoading: false,
                error: null,
            })

            render(<InFlightPrsTab />)

            const statValues = screen.getAllByText(/^\d+$/).filter(el => el.className.includes('text-3xl'))
            expect(statValues.length).toBeGreaterThan(0) // guard: stat card selector must match
            const values = statValues.map(el => Number(el.textContent))
            expect(values).toContain(2) // noSla count
        })
    })

    describe('Team Filter', () => {
        it('should populate select with unique teams from data', () => {
            mockUseInFlightPrs.mockReturnValue({
                data: [
                    makePr({ owningTeam: 'platform', owningTeamLabel: 'Platform Team' }),
                    makePr({ owningTeam: 'payments', owningTeamLabel: 'Payments Team' }),
                    makePr({ owningTeam: 'platform', owningTeamLabel: 'Platform Team' }), // duplicate
                ],
                isLoading: false,
                error: null,
            })

            render(<InFlightPrsTab />)

            expect(screen.getByRole('option', { name: 'All Teams' })).toBeInTheDocument()
            expect(screen.getByRole('option', { name: 'Platform Team' })).toBeInTheDocument()
            expect(screen.getByRole('option', { name: 'Payments Team' })).toBeInTheDocument()
        })

        it('should filter table rows when a team is selected', () => {
            mockUseInFlightPrs.mockReturnValue({
                data: [
                    makePr({ githubRepo: 'org/alpha', owningTeam: 'platform', owningTeamLabel: 'Platform Team' }),
                    makePr({ githubRepo: 'org/beta', owningTeam: 'payments', owningTeamLabel: 'Payments Team' }),
                ],
                isLoading: false,
                error: null,
            })

            render(<InFlightPrsTab />)

            fireEvent.change(screen.getByRole('combobox'), { target: { value: 'platform' } })

            expect(screen.getByText(/alpha#/)).toBeInTheDocument()
            expect(screen.queryByText(/beta#/)).not.toBeInTheDocument()
        })
    })

    describe('Search', () => {
        it('should filter by repo name', () => {
            mockUseInFlightPrs.mockReturnValue({
                data: [
                    makePr({ githubRepo: 'org/alpha', prNumber: 1, prUrl: 'https://github.com/org/alpha/pull/1' }),
                    makePr({ githubRepo: 'org/beta', prNumber: 2, prUrl: 'https://github.com/org/beta/pull/2' }),
                ],
                isLoading: false,
                error: null,
            })

            render(<InFlightPrsTab />)

            fireEvent.change(screen.getByPlaceholderText('Filter PRs, repos or teams...'), {
                target: { value: 'alpha' },
            })

            expect(screen.getByText(/alpha#1/)).toBeInTheDocument()
            expect(screen.queryByText(/beta#2/)).not.toBeInTheDocument()
        })

        it('should filter by PR number', () => {
            mockUseInFlightPrs.mockReturnValue({
                data: [
                    makePr({ githubRepo: 'org/svc', prNumber: 101, prUrl: 'https://github.com/org/svc/pull/101' }),
                    makePr({ githubRepo: 'org/svc', prNumber: 202, prUrl: 'https://github.com/org/svc/pull/202' }),
                ],
                isLoading: false,
                error: null,
            })

            render(<InFlightPrsTab />)

            fireEvent.change(screen.getByPlaceholderText('Filter PRs, repos or teams...'), {
                target: { value: '202' },
            })

            expect(screen.queryByText(/svc#101/)).not.toBeInTheDocument()
            expect(screen.getByText(/svc#202/)).toBeInTheDocument()
        })

        it('should be case-insensitive', () => {
            mockUseInFlightPrs.mockReturnValue({
                data: [makePr({ githubRepo: 'org/MyService', prNumber: 1, prUrl: 'https://github.com/org/MyService/pull/1' })],
                isLoading: false,
                error: null,
            })

            render(<InFlightPrsTab />)

            fireEvent.change(screen.getByPlaceholderText('Filter PRs, repos or teams...'), {
                target: { value: 'MYSERVICE' },
            })

            expect(screen.getByText(/MyService#1/)).toBeInTheDocument()
        })
    })

    describe('Sorting', () => {
        it('should default sort by severity descending (higher severity number first)', () => {
            // STATUS_SEVERITY: ESCALATED=0, OPEN=1, CHANGES_REQUESTED=2, APPROVED=3
            // 'desc' sort puts highest number first: APPROVED → OPEN → ESCALATED
            mockUseInFlightPrs.mockReturnValue({
                data: [
                    makePr({ githubRepo: 'org/open-pr', prNumber: 1, status: 'OPEN' }),
                    makePr({ githubRepo: 'org/escalated-pr', prNumber: 2, status: 'ESCALATED' }),
                    makePr({ githubRepo: 'org/approved-pr', prNumber: 3, status: 'APPROVED' }),
                ],
                isLoading: false,
                error: null,
            })

            render(<InFlightPrsTab />)

            const rows = screen.getAllByRole('row').slice(1) // skip header
            expect(rows[0]).toHaveTextContent('approved-pr')
            expect(rows[1]).toHaveTextContent('open-pr')
            expect(rows[2]).toHaveTextContent('escalated-pr')
        })

        it('should sort by PR column ascending when clicked', () => {
            mockUseInFlightPrs.mockReturnValue({
                data: [
                    makePr({ githubRepo: 'org/zebra', prNumber: 1 }),
                    makePr({ githubRepo: 'org/alpha', prNumber: 2 }),
                ],
                isLoading: false,
                error: null,
            })

            render(<InFlightPrsTab />)

            fireEvent.click(screen.getByText('PR'))

            const rows = screen.getAllByRole('row').slice(1)
            expect(rows[0]).toHaveTextContent('alpha')
            expect(rows[1]).toHaveTextContent('zebra')
        })
    })

    describe('Clock Tick Recompute', () => {
        // This covers the core reason the component wires a 60s setInterval into clockTick:
        // a PR whose deadline passes mid-session must re-classify from "not breached" to
        // "breached" without a user refresh. If the setInterval, clockTick state, or the
        // `dataTimestamp` memo dependency on clockTick regresses, breach counts freeze in
        // place and the Breached card silently under-reports until a data refetch.
        it('should recompute breach status when clock advances past the deadline', () => {
            jest.useFakeTimers()
            jest.setSystemTime(NOW)

            // Deadline is 30s in the future at mount — not yet breached.
            const pr = makePr({ slaDeadline: new Date(NOW + 30 * 1000).toISOString(), slaRemainingSeconds: null })
            mockUseInFlightPrs.mockReturnValue({ data: [pr], isLoading: false, error: null })

            const { container } = render(<InFlightPrsTab />)

            // At mount: not breached — Breached card must be green, not rose.
            expect(container.querySelector('[class*="from-rose"]')).not.toBeInTheDocument()

            // Advance the real clock past the deadline and fire the 60s clockTick interval.
            act(() => {
                jest.setSystemTime(NOW + 120 * 1000)
                jest.advanceTimersByTime(60_000)
            })

            // After the tick: the same PR is now breached. Breached card gradient flips to rose
            // and the table cell renders the red "Breached today" badge.
            expect(container.querySelector('[class*="from-rose"]')).toBeInTheDocument()
            expect(screen.getByText(/Breached today/)).toBeInTheDocument()

            jest.useRealTimers()
        })
    })

    describe('API/UI Version Skew', () => {
        // Guards the backward-compatibility contract: the UI consumes tenants still running an
        // older API that predates V15 and does not serialize `hasSla`. A PR with `hasSla` absent
        // (JSON field missing → undefined after deserialization) must be treated as "unknown" —
        // NOT as no-SLA (which would hide breaches behind the amber "No SLA" badge) and NOT
        // throw. slaInfo's `hasSla === false` check and totals' `hasSla !== false` check both
        // hinge on this; a regression like `hasSla !== true` would misclassify skewed rows.
        it('should treat hasSla=undefined as SLA-tracked, not as no-SLA, and warn in console', () => {
            jest.spyOn(console, 'warn').mockImplementation(() => {})
            // Deadline 1h in the past → breached. If `hasSla === undefined` were treated like
            // `hasSla === false`, this row would render "No SLA" amber and fall out of the
            // breached count. We want it to render as Breached AND count toward the stat card.
            const skewedPr = makePr({
                hasSla: undefined as unknown as boolean,
                slaDeadline: new Date(NOW - 3600 * 1000).toISOString(),
                slaRemainingSeconds: null,
            })
            mockUseInFlightPrs.mockReturnValue({ data: [skewedPr], isLoading: false, error: null })

            const { container } = render(<InFlightPrsTab />)

            // Table cell: rendered as Breached, not as "No SLA"
            expect(screen.getByText(/Breached today/)).toBeInTheDocument()
            expect(screen.queryAllByText('No SLA')).toHaveLength(1) // only the empty stat card label

            // Stat card: breached count flips the gradient to rose (proving the row was counted).
            expect(container.querySelector('[class*="from-rose"]')).toBeInTheDocument()

            // Diagnostic emitted so the skew is observable in devtools.
            expect(console.warn).toHaveBeenCalledWith(
                expect.stringContaining('missing hasSla')
            )
        })
    })

    describe('Pagination', () => {
        it('should not show pagination for 20 or fewer PRs', () => {
            mockUseInFlightPrs.mockReturnValue({ data: makePrs(15), isLoading: false, error: null })

            render(<InFlightPrsTab />)

            expect(screen.queryByText(/Page/)).not.toBeInTheDocument()
        })

        it('should show pagination for more than 20 PRs', () => {
            mockUseInFlightPrs.mockReturnValue({ data: makePrs(25), isLoading: false, error: null })

            render(<InFlightPrsTab />)

            expect(screen.getByText('Page 1 of 2')).toBeInTheDocument()
            expect(screen.getByText('1–20 of 25')).toBeInTheDocument()
        })

        it('should navigate to next page', () => {
            mockUseInFlightPrs.mockReturnValue({ data: makePrs(25), isLoading: false, error: null })

            render(<InFlightPrsTab />)

            fireEvent.click(screen.getByLabelText('Next page'))

            expect(screen.getByText('Page 2 of 2')).toBeInTheDocument()
            expect(screen.getByText('21–25 of 25')).toBeInTheDocument()
        })

        it('should disable prev button on first page', () => {
            mockUseInFlightPrs.mockReturnValue({ data: makePrs(25), isLoading: false, error: null })

            render(<InFlightPrsTab />)

            expect(screen.getByLabelText('Previous page')).toBeDisabled()
        })
    })
})
