// src/lib/hooks/__tests__/dashboard.test.ts
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ReactNode } from 'react'
import {
    useIncomingVsResolvedRate,
    useWeeklyTicketCounts,
    useFirstResponsePercentiles,
    useUnattendedQueriesCount,
    useResolutionTimeByTag,
    useTicketResolutionDurationDistribution
} from '../dashboard'

// Create a wrapper with QueryClient for all tests
const createWrapper = () => {
    const queryClient = new QueryClient({
        defaultOptions: {
            queries: {
                retry: false, // Don't retry failed queries in tests
            },
        },
    })
    function Wrapper({ children }: { children: ReactNode }) {
        return (
            <QueryClientProvider client={queryClient}>
                {children}
            </QueryClientProvider>
        )
    }
    return Wrapper
}

describe('Dashboard Hooks', () => {
    beforeEach(() => {
        // Clear all mocks before each test
        jest.clearAllMocks()
        // Reset fetch mock
        global.fetch = jest.fn()
    })

    afterEach(() => {
        jest.restoreAllMocks()
    })

    describe('useIncomingVsResolvedRate', () => {
        const mockData = [
            { time: '2024-11-01T00:00:00Z', incoming: 5, resolved: 3 },
            { time: '2024-11-01T01:00:00Z', incoming: 8, resolved: 6 },
            { time: '2024-11-01T02:00:00Z', incoming: 12, resolved: 10 },
        ]

        it('should fetch data when enabled', async () => {
            (global.fetch as jest.Mock).mockResolvedValueOnce({
                ok: true,
                json: async () => mockData,
            })

            const { result } = renderHook(
                () => useIncomingVsResolvedRate(true),
                { wrapper: createWrapper() }
            )

            await waitFor(() => expect(result.current.isSuccess).toBe(true))

            expect(result.current.data).toEqual(mockData)
            expect(global.fetch).toHaveBeenCalledWith('/api/db/dashboard/incoming-vs-resolved-rate')
        })

        it('should not fetch when disabled', async () => {
            const { result } = renderHook(
                () => useIncomingVsResolvedRate(false),
                { wrapper: createWrapper() }
            )

            // Wait a bit to ensure no fetch happens
            await new Promise(resolve => setTimeout(resolve, 100))

            expect(result.current.data).toBeUndefined()
            expect(global.fetch).not.toHaveBeenCalled()
        })

        it('should pass date parameters correctly', async () => {
            (global.fetch as jest.Mock).mockResolvedValueOnce({
                ok: true,
                json: async () => mockData,
            })

            const { result } = renderHook(
                () => useIncomingVsResolvedRate(true, '2024-11-01', '2024-11-07'),
                { wrapper: createWrapper() }
            )

            await waitFor(() => expect(result.current.isSuccess).toBe(true))

            expect(global.fetch).toHaveBeenCalledWith(
                '/api/db/dashboard/incoming-vs-resolved-rate?dateFrom=2024-11-01&dateTo=2024-11-07'
            )
        })

        it('should handle empty data gracefully', async () => {
            (global.fetch as jest.Mock).mockResolvedValueOnce({
                ok: true,
                json: async () => [],
            })

            const { result } = renderHook(
                () => useIncomingVsResolvedRate(true),
                { wrapper: createWrapper() }
            )

            await waitFor(() => expect(result.current.isSuccess).toBe(true))

            expect(result.current.data).toEqual([])
        })

        it('should handle fetch errors', async () => {
            (global.fetch as jest.Mock).mockResolvedValueOnce({
                ok: false,
                status: 500,
            })

            const { result } = renderHook(
                () => useIncomingVsResolvedRate(true),
                { wrapper: createWrapper() }
            )

            await waitFor(() => expect(result.current.isError).toBe(true))

            expect(result.current.error).toBeTruthy()
        })

        it('should handle network errors', async () => {
            (global.fetch as jest.Mock).mockRejectedValueOnce(new Error('Network error'))

            const { result } = renderHook(
                () => useIncomingVsResolvedRate(true),
                { wrapper: createWrapper() }
            )

            await waitFor(() => expect(result.current.isError).toBe(true))

            expect(result.current.error).toBeInstanceOf(Error)
        })
    })

    describe('useWeeklyTicketCounts', () => {
        const mockData = [
            { week: '2024-01-01', opened: 10, closed: 8, escalated: 2, stale: 1 },
            { week: '2024-01-08', opened: 15, closed: 12, escalated: 3, stale: 2 },
        ]

        it('should fetch weekly data when enabled', async () => {
            (global.fetch as jest.Mock).mockResolvedValueOnce({
                ok: true,
                json: async () => mockData,
            })

            const { result } = renderHook(
                () => useWeeklyTicketCounts(true),
                { wrapper: createWrapper() }
            )

            await waitFor(() => expect(result.current.isSuccess).toBe(true))

            expect(result.current.data).toEqual(mockData)
            expect(global.fetch).toHaveBeenCalledWith('/api/db/dashboard/weekly-ticket-counts')
        })

        it('should not fetch when disabled', async () => {
            const { result } = renderHook(
                () => useWeeklyTicketCounts(false),
                { wrapper: createWrapper() }
            )

            await new Promise(resolve => setTimeout(resolve, 100))

            expect(result.current.data).toBeUndefined()
            expect(global.fetch).not.toHaveBeenCalled()
        })
    })

    describe('useFirstResponsePercentiles', () => {
        const mockData = { p50: 120, p90: 300 }

        it('should fetch percentiles with date parameters', async () => {
            (global.fetch as jest.Mock).mockResolvedValueOnce({
                ok: true,
                json: async () => mockData,
            })

            const { result } = renderHook(
                () => useFirstResponsePercentiles(true, '2024-10-01', '2024-10-31'),
                { wrapper: createWrapper() }
            )

            await waitFor(() => expect(result.current.isSuccess).toBe(true))

            expect(result.current.data).toEqual(mockData)
            expect(global.fetch).toHaveBeenCalledWith(
                '/api/db/dashboard/first-response-percentiles?dateFrom=2024-10-01&dateTo=2024-10-31'
            )
        })

        it('should handle missing date parameters', async () => {
            (global.fetch as jest.Mock).mockResolvedValueOnce({
                ok: true,
                json: async () => mockData,
            })

            const { result } = renderHook(
                () => useFirstResponsePercentiles(true),
                { wrapper: createWrapper() }
            )

            await waitFor(() => expect(result.current.isSuccess).toBe(true))

            expect(global.fetch).toHaveBeenCalledWith('/api/db/dashboard/first-response-percentiles')
        })
    })

    describe('useUnattendedQueriesCount', () => {
        it('should fetch count with correct structure', async () => {
            const mockData = { count: 42 };

            (global.fetch as jest.Mock).mockResolvedValueOnce({
                ok: true,
                json: async () => mockData,
            })

            const { result } = renderHook(
                () => useUnattendedQueriesCount(true, '2024-01-01', '2024-12-31'),
                { wrapper: createWrapper() }
            )

            await waitFor(() => expect(result.current.isSuccess).toBe(true))

            expect(result.current.data).toEqual(mockData)
            expect(result.current.data?.count).toBe(42)
        })

        it('should handle zero count', async () => {
            const mockData = { count: 0 };

            (global.fetch as jest.Mock).mockResolvedValueOnce({
                ok: true,
                json: async () => mockData,
            })

            const { result } = renderHook(
                () => useUnattendedQueriesCount(true),
                { wrapper: createWrapper() }
            )

            await waitFor(() => expect(result.current.isSuccess).toBe(true))

            expect(result.current.data?.count).toBe(0)
        })
    })

    describe('useResolutionTimeByTag', () => {
        const mockData = [
            { tag: 'bug', p50: 3600, p90: 7200 },
            { tag: 'feature', p50: 7200, p90: 14400 },
        ]

        it('should fetch resolution time by tag', async () => {
            (global.fetch as jest.Mock).mockResolvedValueOnce({
                ok: true,
                json: async () => mockData,
            })

            const { result } = renderHook(
                () => useResolutionTimeByTag(true, '2024-01-01', '2024-12-31'),
                { wrapper: createWrapper() }
            )

            await waitFor(() => expect(result.current.isSuccess).toBe(true))

            expect(result.current.data).toEqual(mockData)
            expect(result.current.data).toHaveLength(2)
        })

        it('should handle empty tag list', async () => {
            (global.fetch as jest.Mock).mockResolvedValueOnce({
                ok: true,
                json: async () => [],
            })

            const { result } = renderHook(
                () => useResolutionTimeByTag(true),
                { wrapper: createWrapper() }
            )

            await waitFor(() => expect(result.current.isSuccess).toBe(true))

            expect(result.current.data).toEqual([])
        })
    })

    describe('Query invalidation and refetch', () => {
        it('should support refetch functionality', async () => {
            const mockData1 = [{ time: '2024-11-01T00:00:00Z', incoming: 5, resolved: 3 }];
            const mockData2 = [{ time: '2024-11-01T00:00:00Z', incoming: 10, resolved: 8 }];

            (global.fetch as jest.Mock)
                .mockResolvedValueOnce({
                    ok: true,
                    json: async () => mockData1,
                })
                .mockResolvedValueOnce({
                    ok: true,
                    json: async () => mockData2,
                })

            const { result } = renderHook(
                () => useIncomingVsResolvedRate(true),
                { wrapper: createWrapper() }
            )

            await waitFor(() => expect(result.current.isSuccess).toBe(true))
            expect(result.current.data).toEqual(mockData1)

            // Trigger refetch
            result.current.refetch()

            await waitFor(() => expect(result.current.data).toEqual(mockData2))
            expect(global.fetch).toHaveBeenCalledTimes(2)
        })
    })

    describe('Loading states', () => {
        it('should show loading state initially', async () => {
            (global.fetch as jest.Mock).mockImplementation(
                () => new Promise(resolve => setTimeout(() => resolve({
                    ok: true,
                    json: async () => [],
                }), 100))
            )

            const { result } = renderHook(
                () => useIncomingVsResolvedRate(true),
                { wrapper: createWrapper() }
            )

            expect(result.current.isLoading).toBe(true)
            expect(result.current.data).toBeUndefined()

            await waitFor(() => expect(result.current.isSuccess).toBe(true))

            expect(result.current.isLoading).toBe(false)
            expect(result.current.data).toEqual([])
        })
    })

    describe('useTicketResolutionDurationDistribution', () => {
        const bucketed = [
            { label: '< 15 min', count: 2, minMinutes: 0, maxMinutes: 15 },
            { label: '15-30 min', count: 1, minMinutes: 15, maxMinutes: 30 },
        ]

        it('fetches bucketed distribution with dates', async () => {
            (global.fetch as jest.Mock).mockResolvedValueOnce({
                ok: true,
                json: async () => bucketed,
            })

            const { result } = renderHook(
                () => useTicketResolutionDurationDistribution(true, '2024-01-01', '2024-02-01'),
                { wrapper: createWrapper() }
            )

            await waitFor(() => expect(result.current.isSuccess).toBe(true))
            expect(result.current.data).toEqual(bucketed)
            expect(global.fetch).toHaveBeenCalledWith(
                '/api/db/dashboard/resolution-duration-distribution?dateFrom=2024-01-01&dateTo=2024-02-01'
            )
        })

        it('does not fetch when disabled', async () => {
            const { result } = renderHook(
                () => useTicketResolutionDurationDistribution(false),
                { wrapper: createWrapper() }
            )

            await new Promise(res => setTimeout(res, 50))
            expect(result.current.data).toBeUndefined()
            expect(global.fetch).not.toHaveBeenCalled()
        })

        it('propagates errors', async () => {
            (global.fetch as jest.Mock).mockResolvedValueOnce({
                ok: false,
                status: 500,
            })

            const { result } = renderHook(
                () => useTicketResolutionDurationDistribution(true),
                { wrapper: createWrapper() }
            )

            await waitFor(() => expect(result.current.isError).toBe(true))
            expect(result.current.error).toBeTruthy()
        })
    })
})

