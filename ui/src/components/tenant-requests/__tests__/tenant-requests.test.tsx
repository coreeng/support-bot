import React from 'react'
import { render, screen, fireEvent } from '@testing-library/react'
import TenantRequestsPage from '../tenant-requests'
import type { RepoInsights } from '../../../lib/types/dashboard'

const mockUseTenantInsightsStats = jest.fn()

jest.mock('../../../lib/hooks', () => ({
    useTenantInsightsStats: (...args: unknown[]) => mockUseTenantInsightsStats(...args),
}))

jest.mock('../../../lib/utils/format', () => ({
    formatDuration: (seconds: number) => {
        if (seconds >= 3600) return `${(seconds / 3600).toFixed(1)}h`
        if (seconds >= 60) return `${Math.round(seconds / 60)}m`
        return `${Math.round(seconds)}s`
    },
}))

function makeRepo(overrides: Partial<RepoInsights> = {}): RepoInsights {
    return {
        repo: 'org/service-a',
        owningTeam: 'platform',
        prCount: 10,
        openCount: 2,
        escalatedCount: 1,
        breachedCount: 0,
        p50Seconds: 3600,
        p90Seconds: 14400,
        p99Seconds: 86400,
        ...overrides,
    }
}

function makeRepos(count: number): RepoInsights[] {
    return Array.from({ length: count }, (_, i) =>
        makeRepo({
            repo: `org/service-${String.fromCharCode(97 + i)}`,
            owningTeam: i % 2 === 0 ? 'platform' : 'payments',
            prCount: count - i,
            escalatedCount: i % 3 === 0 ? 1 : 0,
            breachedCount: i % 5 === 0 ? 1 : 0,
        })
    )
}

describe('TenantRequestsPage', () => {
    beforeEach(() => {
        jest.clearAllMocks()
        mockUseTenantInsightsStats.mockReturnValue({ data: [], isLoading: false, error: null })
    })

    describe('Rendering', () => {
        it('should render page title and section header', () => {
            render(<TenantRequestsPage />)

            expect(screen.getByText('Tenant Requests')).toBeInTheDocument()
            expect(screen.getByText('PR Activity & SLA Health')).toBeInTheDocument()
        })

        it('should render all date preset buttons', () => {
            render(<TenantRequestsPage />)

            expect(screen.getByText('7 Days')).toBeInTheDocument()
            expect(screen.getByText('30 Days')).toBeInTheDocument()
            expect(screen.getByText('90 Days')).toBeInTheDocument()
            expect(screen.getByText('1 Year')).toBeInTheDocument()
            expect(screen.getByText('Custom')).toBeInTheDocument()
        })

        it('should render stat cards with aggregated totals', () => {
            const repos = [
                makeRepo({ prCount: 10, openCount: 3, escalatedCount: 2, breachedCount: 1 }),
                makeRepo({ repo: 'org/service-b', prCount: 5, openCount: 1, escalatedCount: 0, breachedCount: 0 }),
            ]
            mockUseTenantInsightsStats.mockReturnValue({ data: repos, isLoading: false })

            render(<TenantRequestsPage />)

            // Stat card values are the bold white text inside gradient cards
            const statValues = screen.getAllByText(/^\d+$/).filter(
                el => el.className.includes('text-3xl')
            )
            const values = statValues.map(el => el.textContent)
            expect(values).toContain('2')   // repositories
            expect(values).toContain('15')  // total PRs
            expect(values).toContain('4')   // open
        })

        it('should render table headers', () => {
            mockUseTenantInsightsStats.mockReturnValue({ data: [makeRepo()], isLoading: false })

            render(<TenantRequestsPage />)

            const headers = screen.getAllByRole('columnheader')
            const headerTexts = headers.map(h => h.textContent?.trim())
            expect(headerTexts).toEqual(
                expect.arrayContaining(['Repository', 'Team', 'PRs', 'Open', 'Escalated', 'Breached', 'p50', 'p90', 'p99'])
            )
        })

        it('should render repo row with formatted durations', () => {
            mockUseTenantInsightsStats.mockReturnValue({
                data: [makeRepo({ p50Seconds: 7200, p90Seconds: 28800, p99Seconds: 172800 })],
                isLoading: false,
            })

            render(<TenantRequestsPage />)

            expect(screen.getByText('org/service-a')).toBeInTheDocument()
            expect(screen.getByText('platform')).toBeInTheDocument()
            expect(screen.getByText('2.0h')).toBeInTheDocument()   // p50
            expect(screen.getByText('8.0h')).toBeInTheDocument()   // p90
            expect(screen.getByText('48.0h')).toBeInTheDocument()  // p99
        })
    })

    describe('Loading State', () => {
        it('should show loading indicator in table area', () => {
            mockUseTenantInsightsStats.mockReturnValue({ data: undefined, isLoading: true })

            render(<TenantRequestsPage />)

            expect(screen.getByText('Loading...')).toBeInTheDocument()
        })

        it('should not render table rows while loading', () => {
            mockUseTenantInsightsStats.mockReturnValue({ data: undefined, isLoading: true })

            render(<TenantRequestsPage />)

            expect(screen.queryByText('Repository')).not.toBeInTheDocument()
        })
    })

    describe('Empty State', () => {
        it('should show empty message when no data', () => {
            render(<TenantRequestsPage />)

            expect(screen.getByText('No PR data for this period')).toBeInTheDocument()
        })

        it('should show search-specific empty message when filter has no matches', () => {
            mockUseTenantInsightsStats.mockReturnValue({ data: [makeRepo()], isLoading: false, error: null })

            render(<TenantRequestsPage />)

            const input = screen.getByPlaceholderText('Filter repos or teams...')
            fireEvent.change(input, { target: { value: 'nonexistent' } })

            expect(screen.getByText('No repos match your search')).toBeInTheDocument()
        })
    })

    describe('Error State', () => {
        it('should show error message when fetch fails', () => {
            mockUseTenantInsightsStats.mockReturnValue({
                data: undefined,
                isLoading: false,
                error: new Error('Network error'),
            })

            render(<TenantRequestsPage />)

            expect(screen.getByText('Failed to load data — please try again')).toBeInTheDocument()
        })

        it('should show error instead of empty message', () => {
            mockUseTenantInsightsStats.mockReturnValue({
                data: undefined,
                isLoading: false,
                error: new Error('500'),
            })

            render(<TenantRequestsPage />)

            expect(screen.queryByText('No PR data for this period')).not.toBeInTheDocument()
        })
    })

    describe('Date Presets', () => {
        it('should default to 30 Days preset', () => {
            render(<TenantRequestsPage />)

            const activeButton = screen.getByText('30 Days')
            expect(activeButton.className).toContain('bg-white')
        })

        it('should switch active preset on click', () => {
            render(<TenantRequestsPage />)

            fireEvent.click(screen.getByText('7 Days'))

            expect(screen.getByText('7 Days').className).toContain('bg-white')
            expect(screen.getByText('30 Days').className).not.toContain('bg-white')
        })

        it('should call hook with updated dates when preset changes', () => {
            render(<TenantRequestsPage />)

            fireEvent.click(screen.getByText('90 Days'))

            const lastCall = mockUseTenantInsightsStats.mock.calls.at(-1)
            expect(lastCall[0]).toBeDefined() // dateFrom
            expect(lastCall[1]).toBeDefined() // dateTo
        })

        it('should show date pickers when Custom is selected', () => {
            const { container } = render(<TenantRequestsPage />)

            fireEvent.click(screen.getByText('Custom'))

            const dateInputs = container.querySelectorAll('input[type="date"]')
            expect(dateInputs).toHaveLength(2)
        })

        it('should not show date pickers for non-custom presets', () => {
            const { container } = render(<TenantRequestsPage />)

            const dateInputs = container.querySelectorAll('input[type="date"]')
            expect(dateInputs).toHaveLength(0)
        })

        it('should show invalid range message when dateFrom > dateTo', () => {
            const { container } = render(<TenantRequestsPage />)

            fireEvent.click(screen.getByText('Custom'))
            const dateInputs = container.querySelectorAll('input[type="date"]')

            fireEvent.change(dateInputs[0], { target: { value: '2026-03-25' } })
            fireEvent.change(dateInputs[1], { target: { value: '2026-03-01' } })

            expect(screen.getByText('Invalid range')).toBeInTheDocument()
        })
    })

    describe('Search', () => {
        it('should filter repos by name', () => {
            mockUseTenantInsightsStats.mockReturnValue({
                data: [
                    makeRepo({ repo: 'org/alpha' }),
                    makeRepo({ repo: 'org/beta' }),
                ],
                isLoading: false,
            })

            render(<TenantRequestsPage />)

            const input = screen.getByPlaceholderText('Filter repos or teams...')
            fireEvent.change(input, { target: { value: 'alpha' } })

            expect(screen.getByText('org/alpha')).toBeInTheDocument()
            expect(screen.queryByText('org/beta')).not.toBeInTheDocument()
        })

        it('should filter repos by team name', () => {
            mockUseTenantInsightsStats.mockReturnValue({
                data: [
                    makeRepo({ repo: 'org/svc-1', owningTeam: 'platform' }),
                    makeRepo({ repo: 'org/svc-2', owningTeam: 'payments' }),
                ],
                isLoading: false,
            })

            render(<TenantRequestsPage />)

            const input = screen.getByPlaceholderText('Filter repos or teams...')
            fireEvent.change(input, { target: { value: 'payments' } })

            expect(screen.getByText('org/svc-2')).toBeInTheDocument()
            expect(screen.queryByText('org/svc-1')).not.toBeInTheDocument()
        })

        it('should be case-insensitive', () => {
            mockUseTenantInsightsStats.mockReturnValue({
                data: [makeRepo({ repo: 'org/MyService' })],
                isLoading: false,
            })

            render(<TenantRequestsPage />)

            const input = screen.getByPlaceholderText('Filter repos or teams...')
            fireEvent.change(input, { target: { value: 'MYSERVICE' } })

            expect(screen.getByText('org/MyService')).toBeInTheDocument()
        })

        it('should show filtered count', () => {
            mockUseTenantInsightsStats.mockReturnValue({
                data: [
                    makeRepo({ repo: 'org/alpha' }),
                    makeRepo({ repo: 'org/beta' }),
                    makeRepo({ repo: 'org/alpha-two' }),
                ],
                isLoading: false,
            })

            render(<TenantRequestsPage />)

            const input = screen.getByPlaceholderText('Filter repos or teams...')
            fireEvent.change(input, { target: { value: 'alpha' } })

            expect(screen.getByText('2 of 3 repos')).toBeInTheDocument()
        })
    })

    describe('Sorting', () => {
        const repos = [
            makeRepo({ repo: 'org/zebra', prCount: 5, breachedCount: 0, escalatedCount: 0 }),
            makeRepo({ repo: 'org/alpha', prCount: 20, breachedCount: 3, escalatedCount: 1 }),
            makeRepo({ repo: 'org/middle', prCount: 10, breachedCount: 1, escalatedCount: 2 }),
        ]

        beforeEach(() => {
            mockUseTenantInsightsStats.mockReturnValue({ data: repos, isLoading: false })
        })

        it('should default sort by severity (breached desc)', () => {
            render(<TenantRequestsPage />)

            const rows = screen.getAllByRole('row').slice(1) // skip header
            expect(rows[0]).toHaveTextContent('org/alpha')   // 3 breached
            expect(rows[1]).toHaveTextContent('org/middle')  // 1 breached
            expect(rows[2]).toHaveTextContent('org/zebra')   // 0 breached
        })

        it('should sort by repo name ascending when column clicked', () => {
            render(<TenantRequestsPage />)

            fireEvent.click(screen.getByText('Repository'))

            const rows = screen.getAllByRole('row').slice(1)
            expect(rows[0]).toHaveTextContent('org/alpha')
            expect(rows[1]).toHaveTextContent('org/middle')
            expect(rows[2]).toHaveTextContent('org/zebra')
        })

        it('should toggle sort direction on double click', () => {
            render(<TenantRequestsPage />)

            fireEvent.click(screen.getByText('Repository'))  // asc
            fireEvent.click(screen.getByText('Repository'))  // desc

            const rows = screen.getAllByRole('row').slice(1)
            expect(rows[0]).toHaveTextContent('org/zebra')
            expect(rows[2]).toHaveTextContent('org/alpha')
        })

        it('should sort by PRs descending when column clicked', () => {
            render(<TenantRequestsPage />)

            fireEvent.click(screen.getByText('PRs'))

            const rows = screen.getAllByRole('row').slice(1)
            expect(rows[0]).toHaveTextContent('org/alpha')  // 20
            expect(rows[2]).toHaveTextContent('org/zebra')  // 5
        })

        it('should reset to page 0 when sort changes', () => {
            // 25 repos to have 2 pages
            mockUseTenantInsightsStats.mockReturnValue({ data: makeRepos(25), isLoading: false, error: null })

            render(<TenantRequestsPage />)

            // Go to page 2
            fireEvent.click(screen.getByLabelText('Next page'))

            expect(screen.getByText(/Page 2/)).toBeInTheDocument()

            // Sort by repo — should reset to page 1
            fireEvent.click(screen.getByText('Repository'))

            expect(screen.getByText(/Page 1/)).toBeInTheDocument()
        })
    })

    describe('Pagination', () => {
        it('should not show pagination for 20 or fewer repos', () => {
            mockUseTenantInsightsStats.mockReturnValue({ data: makeRepos(15), isLoading: false })

            render(<TenantRequestsPage />)

            expect(screen.queryByText(/Page/)).not.toBeInTheDocument()
        })

        it('should show pagination for more than 20 repos', () => {
            mockUseTenantInsightsStats.mockReturnValue({ data: makeRepos(25), isLoading: false })

            render(<TenantRequestsPage />)

            expect(screen.getByText('Page 1 of 2')).toBeInTheDocument()
            expect(screen.getByText('1–20 of 25')).toBeInTheDocument()
        })

        it('should navigate to next page', () => {
            mockUseTenantInsightsStats.mockReturnValue({ data: makeRepos(25), isLoading: false, error: null })

            render(<TenantRequestsPage />)

            fireEvent.click(screen.getByLabelText('Next page'))

            expect(screen.getByText('Page 2 of 2')).toBeInTheDocument()
            expect(screen.getByText('21–25 of 25')).toBeInTheDocument()
        })

        it('should navigate back to previous page', () => {
            mockUseTenantInsightsStats.mockReturnValue({ data: makeRepos(25), isLoading: false, error: null })

            render(<TenantRequestsPage />)

            fireEvent.click(screen.getByLabelText('Next page'))
            fireEvent.click(screen.getByLabelText('Previous page'))

            expect(screen.getByText('Page 1 of 2')).toBeInTheDocument()
        })

        it('should disable prev button on first page', () => {
            mockUseTenantInsightsStats.mockReturnValue({ data: makeRepos(25), isLoading: false, error: null })

            render(<TenantRequestsPage />)

            expect(screen.getByLabelText('Previous page')).toBeDisabled()
        })

        it('should disable next button on last page', () => {
            mockUseTenantInsightsStats.mockReturnValue({ data: makeRepos(25), isLoading: false, error: null })

            render(<TenantRequestsPage />)

            fireEvent.click(screen.getByLabelText('Next page'))

            expect(screen.getByLabelText('Next page')).toBeDisabled()
        })

        it('should reset to page 0 when search changes', () => {
            mockUseTenantInsightsStats.mockReturnValue({ data: makeRepos(25), isLoading: false, error: null })

            render(<TenantRequestsPage />)

            fireEvent.click(screen.getByLabelText('Next page'))
            expect(screen.getByText(/Page 2/)).toBeInTheDocument()

            const input = screen.getByPlaceholderText('Filter repos or teams...')
            fireEvent.change(input, { target: { value: 'service' } })

            expect(screen.queryByText(/Page 2/)).not.toBeInTheDocument()
        })
    })

    describe('Badge Colouring', () => {
        it('should show muted zero for escalated=0', () => {
            mockUseTenantInsightsStats.mockReturnValue({
                data: [makeRepo({ escalatedCount: 0, breachedCount: 0 })],
                isLoading: false,
            })

            const { container } = render(<TenantRequestsPage />)

            // Zeros render as plain text (no badge pill)
            const mutedZeros = container.querySelectorAll('.text-slate-300')
            expect(mutedZeros.length).toBeGreaterThanOrEqual(2) // escalated + breached
        })

        it('should show amber badge for escalated > 0', () => {
            mockUseTenantInsightsStats.mockReturnValue({
                data: [makeRepo({ escalatedCount: 3 })],
                isLoading: false,
            })

            const { container } = render(<TenantRequestsPage />)

            const amberBadge = container.querySelector('.bg-amber-50')
            expect(amberBadge).toBeInTheDocument()
            expect(amberBadge!.textContent).toBe('3')
        })

        it('should show red badge for breached > 0', () => {
            mockUseTenantInsightsStats.mockReturnValue({
                data: [makeRepo({ breachedCount: 2 })],
                isLoading: false,
            })

            const { container } = render(<TenantRequestsPage />)

            const redBadge = container.querySelector('.bg-red-50')
            expect(redBadge).toBeInTheDocument()
            expect(redBadge!.textContent).toBe('2')
        })
    })

    describe('Duration Pill Colouring', () => {
        it('should show green for durations under 4 hours', () => {
            mockUseTenantInsightsStats.mockReturnValue({
                data: [makeRepo({ p50Seconds: 3600 })], // 1h
                isLoading: false,
            })

            const { container } = render(<TenantRequestsPage />)

            const greenPill = container.querySelector('.bg-emerald-50')
            expect(greenPill).toBeInTheDocument()
        })

        it('should show amber for durations between 4-24 hours', () => {
            mockUseTenantInsightsStats.mockReturnValue({
                data: [makeRepo({ p50Seconds: 28800 })], // 8h
                isLoading: false,
            })

            const { container } = render(<TenantRequestsPage />)

            const amberPill = container.querySelector('.text-amber-700')
            expect(amberPill).toBeInTheDocument()
        })

        it('should show red for durations over 24 hours', () => {
            mockUseTenantInsightsStats.mockReturnValue({
                data: [makeRepo({ p99Seconds: 172800 })], // 48h
                isLoading: false,
            })

            const { container } = render(<TenantRequestsPage />)

            const redPill = container.querySelector('.text-red-700')
            expect(redPill).toBeInTheDocument()
        })
    })

    describe('Stat Card Colour Logic', () => {
        it('should show green gradient when escalated is 0', () => {
            mockUseTenantInsightsStats.mockReturnValue({
                data: [makeRepo({ escalatedCount: 0, breachedCount: 0 })],
                isLoading: false,
            })

            const { container } = render(<TenantRequestsPage />)

            const greenCards = container.querySelectorAll('[class*="from-emerald"]')
            expect(greenCards.length).toBe(2) // escalated + breached
        })

        it('should show amber gradient when escalated > 0', () => {
            mockUseTenantInsightsStats.mockReturnValue({
                data: [makeRepo({ escalatedCount: 1, breachedCount: 0 })],
                isLoading: false,
            })

            const { container } = render(<TenantRequestsPage />)

            const amberCard = container.querySelector('[class*="from-amber"]')
            expect(amberCard).toBeInTheDocument()
        })

        it('should show red gradient when breached > 0', () => {
            mockUseTenantInsightsStats.mockReturnValue({
                data: [makeRepo({ escalatedCount: 0, breachedCount: 1 })],
                isLoading: false,
            })

            const { container } = render(<TenantRequestsPage />)

            const redCard = container.querySelector('[class*="from-rose"]')
            expect(redCard).toBeInTheDocument()
        })
    })
})
