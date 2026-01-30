// src/lib/hooks/__tests__/backend.test.tsx
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ReactNode } from 'react'
import {
    useTickets,
    useTicket,
    useEscalationTeams,
    useTenantTeams,
    useEscalations,
    useRatings,
    useRegistry
} from '../backend'
import * as api from '../../api'

// Mock the API module
jest.mock('../../api')

const mockedApiGet = api.apiGet as jest.MockedFunction<typeof api.apiGet>
const mockedApiPost = api.apiPost as jest.MockedFunction<typeof api.apiPost>

describe('Backend Hooks', () => {
    let queryClient: QueryClient

    beforeEach(() => {
        queryClient = new QueryClient({
            defaultOptions: {
                queries: {
                    retry: false,
                },
            },
        })
        jest.clearAllMocks()
    })

    const wrapper = ({ children }: { children: ReactNode }) => (
        <QueryClientProvider client={queryClient}>
            {children}
        </QueryClientProvider>
    )

    describe('useTickets', () => {
        const getMockPaginatedTickets = () => ({
            content: [
                {
                    id: '1',
                    status: 'open',
                    team: { label: 'Team A', code: 'team-a' },
                    escalations: [
                        { id: 'esc-1', team: { label: 'Support Team', code: 'support' } }
                    ]
                },
                {
                    id: '2',
                    status: 'closed',
                    team: { label: 'Team B', code: 'team-b' },
                    escalations: []
                }
            ],
            page: 0,
            totalPages: 1,
            totalElements: 2
        })

        it('should fetch tickets with default parameters', async () => {
            mockedApiGet.mockResolvedValueOnce(getMockPaginatedTickets())

            const { result } = renderHook(() => useTickets(), { wrapper })

            await waitFor(() => expect(result.current.isSuccess).toBe(true))

            expect(mockedApiGet).toHaveBeenCalledWith('/ticket?page=0&pageSize=50')
            expect(result.current.data).toBeDefined()
            expect(result.current.data?.content).toHaveLength(2)
        })

        it('should fetch tickets with custom page and pageSize', async () => {
            mockedApiGet.mockResolvedValueOnce(getMockPaginatedTickets())

            const { result } = renderHook(() => useTickets(2, 25), { wrapper })

            await waitFor(() => expect(result.current.isSuccess).toBe(true))

            expect(mockedApiGet).toHaveBeenCalledWith('/ticket?page=2&pageSize=25')
        })

        it('should fetch tickets with date range', async () => {
            mockedApiGet.mockResolvedValueOnce(getMockPaginatedTickets())

            const { result } = renderHook(
                () => useTickets(0, 50, '2025-01-01', '2025-01-31'),
                { wrapper }
            )

            await waitFor(() => expect(result.current.isSuccess).toBe(true))

            expect(mockedApiGet).toHaveBeenCalledWith(
                '/ticket?page=0&pageSize=50&dateFrom=2025-01-01&dateTo=2025-01-31'
            )
        })

        it('should map team.label to team.name', async () => {
            mockedApiGet.mockResolvedValueOnce(getMockPaginatedTickets())

            const { result } = renderHook(() => useTickets(), { wrapper })

            await waitFor(() => expect(result.current.isSuccess).toBe(true))

            expect(result.current.data?.content[0].team).toEqual({ name: 'team-a' })
            expect(result.current.data?.content[1].team).toEqual({ name: 'team-b' })
        })

        it('should map team.code to team.name when label is missing', async () => {
            const ticketsWithCodeOnly = {
                ...getMockPaginatedTickets(),
                content: [
                    {
                        id: '1',
                        team: { code: 'team-code' }
                    }
                ]
            }
            mockedApiGet.mockResolvedValueOnce(ticketsWithCodeOnly)

            const { result } = renderHook(() => useTickets(), { wrapper })

            await waitFor(() => expect(result.current.isSuccess).toBe(true))

            expect(result.current.data?.content[0].team).toEqual({ name: 'team-code' })
        })

        it('should handle null team', async () => {
            const ticketsWithNullTeam = {
                ...getMockPaginatedTickets(),
                content: [{ id: '1', team: null }]
            }
            mockedApiGet.mockResolvedValueOnce(ticketsWithNullTeam)

            const { result } = renderHook(() => useTickets(), { wrapper })

            await waitFor(() => expect(result.current.isSuccess).toBe(true))

            expect(result.current.data?.content[0].team).toBeNull()
        })

        it('should map escalation teams correctly', async () => {
            mockedApiGet.mockResolvedValueOnce(getMockPaginatedTickets())

            const { result } = renderHook(() => useTickets(), { wrapper })

            await waitFor(() => expect(result.current.isSuccess).toBe(true))

            const ticket = result.current.data?.content[0]
            expect(ticket?.escalations?.[0]?.team).toEqual({ 
                name: 'support' 
            })
        })

        it('should handle empty escalations array', async () => {
            mockedApiGet.mockResolvedValueOnce(getMockPaginatedTickets())

            const { result } = renderHook(() => useTickets(), { wrapper })

            await waitFor(() => expect(result.current.isSuccess).toBe(true))

            expect(result.current.data?.content[1].escalations).toEqual([])
        })

        it('should use correct query key for caching', async () => {
            mockedApiGet.mockResolvedValueOnce(getMockPaginatedTickets())

            const { result } = renderHook(() => useTickets(1, 20, '2025-01-01', '2025-12-31'), { wrapper })

            await waitFor(() => expect(result.current.isSuccess).toBe(true))

            const cachedData = queryClient.getQueryData(['tickets', 1, 20, '2025-01-01', '2025-12-31'])
            expect(cachedData).toBeDefined()
        })

        it('should handle API errors', async () => {
            mockedApiGet.mockRejectedValueOnce(new Error('API Error'))

            const { result } = renderHook(() => useTickets(), { wrapper })

            await waitFor(() => expect(result.current.isError).toBe(true))

            expect(result.current.error).toBeDefined()
        })
    })

    describe('useTicket', () => {
        const getMockTicket = () => ({
            id: '123',
            status: 'open',
            team: { label: 'Team A', code: 'team-a' },
            escalations: [
                { id: 'esc-1', team: { label: 'Support', code: 'support' } }
            ]
        })

        it('should fetch single ticket by id', async () => {
            mockedApiGet.mockResolvedValueOnce(getMockTicket())

            const { result } = renderHook(() => useTicket('123'), { wrapper })

            await waitFor(() => expect(result.current.isSuccess).toBe(true))

            expect(mockedApiGet).toHaveBeenCalledWith('/ticket/123')
            expect(result.current.data).toBeDefined()
        })

        it('should not fetch when id is undefined', () => {
            const { result } = renderHook(() => useTicket(undefined), { wrapper })

            expect(result.current.fetchStatus).toBe('idle')
            expect(mockedApiGet).not.toHaveBeenCalled()
        })

        it('should map team.code to team.name', async () => {
            mockedApiGet.mockResolvedValueOnce(getMockTicket())

            const { result } = renderHook(() => useTicket('123'), { wrapper })

            await waitFor(() => expect(result.current.isSuccess).toBe(true))

            expect(result.current.data?.team).toEqual({ name: 'team-a' })
        })

        it('should map escalation teams', async () => {
            mockedApiGet.mockResolvedValueOnce(getMockTicket())

            const { result } = renderHook(() => useTicket('123'), { wrapper })

            await waitFor(() => expect(result.current.isSuccess).toBe(true))

            expect(result.current.data?.escalations[0].team).toEqual({ name: 'support' })
        })

        it('should use correct query key', async () => {
            mockedApiGet.mockResolvedValueOnce(getMockTicket())

            const { result } = renderHook(() => useTicket('123'), { wrapper })

            await waitFor(() => expect(result.current.isSuccess).toBe(true))

            const cachedData = queryClient.getQueryData(['ticket', '123'])
            expect(cachedData).toBeDefined()
        })
    })

    describe('useEscalationTeams', () => {
        const mockTeams = [
            { label: 'Support Team', code: 'support', types: ['escalation'] },
            { label: 'Dev Team', code: 'dev', types: ['escalation', 'tenant'] }
        ]

        it('should fetch escalation teams', async () => {
            mockedApiGet.mockResolvedValueOnce(mockTeams)

            const { result } = renderHook(() => useEscalationTeams(), { wrapper })

            await waitFor(() => expect(result.current.isSuccess).toBe(true))

            expect(mockedApiGet).toHaveBeenCalledWith('/team?type=escalation')
            expect(result.current.data).toHaveLength(2)
        })

        it('should map code to name', async () => {
            mockedApiGet.mockResolvedValueOnce(mockTeams)

            const { result } = renderHook(() => useEscalationTeams(), { wrapper })

            await waitFor(() => expect(result.current.isSuccess).toBe(true))

            expect(result.current.data?.[0]).toEqual({
                name: 'support',
                types: ['escalation']
            })
        })

        it('should fallback to code when label is missing', async () => {
            const teamsWithoutLabel = [{ code: 'team-code', types: [] }]
            mockedApiGet.mockResolvedValueOnce(teamsWithoutLabel)

            const { result } = renderHook(() => useEscalationTeams(), { wrapper })

            await waitFor(() => expect(result.current.isSuccess).toBe(true))

            expect(result.current.data?.[0].name).toBe('team-code')
        })

        it('should use empty string when both label and code are missing', async () => {
            const teamsWithoutLabelOrCode = [{ types: [] }]
            mockedApiGet.mockResolvedValueOnce(teamsWithoutLabelOrCode)

            const { result } = renderHook(() => useEscalationTeams(), { wrapper })

            await waitFor(() => expect(result.current.isSuccess).toBe(true))

            expect(result.current.data?.[0].name).toBe('')
        })

        it('should use correct unique query key', async () => {
            mockedApiGet.mockResolvedValueOnce(mockTeams)

            const { result } = renderHook(() => useEscalationTeams(), { wrapper })

            await waitFor(() => expect(result.current.isSuccess).toBe(true))

            const cachedData = queryClient.getQueryData(['team', 'escalation'])
            expect(cachedData).toBeDefined()
        })
    })

    describe('useTenantTeams', () => {
        const mockTeams = [
            { label: 'Tenant A', code: 'tenant-a' },
            { label: 'Tenant B', code: 'tenant-b' }
        ]

        it('should fetch tenant teams', async () => {
            mockedApiGet.mockResolvedValueOnce(mockTeams)

            const { result } = renderHook(() => useTenantTeams(), { wrapper })

            await waitFor(() => expect(result.current.isSuccess).toBe(true))

            expect(mockedApiGet).toHaveBeenCalledWith('/team?type=tenant')
            expect(result.current.data).toHaveLength(2)
        })

        it('should map code to name', async () => {
            mockedApiGet.mockResolvedValueOnce(mockTeams)

            const { result } = renderHook(() => useTenantTeams(), { wrapper })

            await waitFor(() => expect(result.current.isSuccess).toBe(true))

            expect(result.current.data?.[0]).toEqual({ name: 'tenant-a' })
        })

        it('should use correct unique query key', async () => {
            mockedApiGet.mockResolvedValueOnce(mockTeams)

            const { result } = renderHook(() => useTenantTeams(), { wrapper })

            await waitFor(() => expect(result.current.isSuccess).toBe(true))

            const cachedData = queryClient.getQueryData(['team', 'tenant'])
            expect(cachedData).toBeDefined()
        })

        it('should not collide with escalation teams cache', async () => {
            const l2Teams = [{ label: 'Escalation Team', code: 'esc' }]
            const tenantTeams = [{ label: 'Tenant Team', code: 'tenant' }]

            mockedApiGet.mockResolvedValueOnce(l2Teams)
            mockedApiGet.mockResolvedValueOnce(tenantTeams)

            const { result: l2Result } = renderHook(() => useEscalationTeams(), { wrapper })
            const { result: tenantResult } = renderHook(() => useTenantTeams(), { wrapper })

            await waitFor(() => {
                expect(l2Result.current.isSuccess).toBe(true)
                expect(tenantResult.current.isSuccess).toBe(true)
            })

            expect(l2Result.current.data?.[0].name).toBe('esc')
            expect(tenantResult.current.data?.[0].name).toBe('tenant')
        })
    })

    describe('useEscalations', () => {
        const mockEscalations = {
            page: 0,
            totalPages: 1,
            totalElements: 2,
            content: [
                {
                    id: { id: 1 },
                    ticketId: { id: 123 },
                    threadLink: 'https://example.com/thread1',
                    openedAt: '2025-01-01T10:00:00Z',
                    resolvedAt: '2025-01-02T10:00:00Z',
                    escalatingTeam: 'Team A',
                    team: { label: 'Support Team', code: 'support' },
                    tags: ['urgent', 'bug']
                },
                {
                    id: 2,
                    ticketId: 456,
                    threadLink: 'https://example.com/thread2',
                    openedAt: '2025-01-03T10:00:00Z',
                    resolvedAt: null,
                    escalatingTeam: 'Team B',
                    team: null,
                    tags: null
                }
            ]
        }

        it('should fetch escalations with default parameters', async () => {
            mockedApiGet.mockResolvedValueOnce(mockEscalations)

            const { result } = renderHook(() => useEscalations(), { wrapper })

            await waitFor(() => expect(result.current.isSuccess).toBe(true))

            expect(mockedApiGet).toHaveBeenCalledWith('/escalation?page=0&pageSize=50&escalated=true')
            expect(result.current.data?.content).toHaveLength(2)
        })

        it('should fetch escalations with custom page and pageSize', async () => {
            mockedApiGet.mockResolvedValueOnce(mockEscalations)

            const { result } = renderHook(() => useEscalations(3, 10), { wrapper })

            await waitFor(() => expect(result.current.isSuccess).toBe(true))

            expect(mockedApiGet).toHaveBeenCalledWith('/escalation?page=3&pageSize=10&escalated=true')
        })

        it('should convert object id to string', async () => {
            mockedApiGet.mockResolvedValueOnce(mockEscalations)

            const { result } = renderHook(() => useEscalations(), { wrapper })

            await waitFor(() => expect(result.current.isSuccess).toBe(true))

            expect(result.current.data?.content[0].id).toBe('1')
            expect(result.current.data?.content[0].ticketId).toBe('123')
        })

        it('should handle numeric ids', async () => {
            mockedApiGet.mockResolvedValueOnce(mockEscalations)

            const { result } = renderHook(() => useEscalations(), { wrapper })

            await waitFor(() => expect(result.current.isSuccess).toBe(true))

            expect(result.current.data?.content[1].id).toBe('2')
            expect(result.current.data?.content[1].ticketId).toBe('456')
        })

        it('should map team.code to team.name', async () => {
            mockedApiGet.mockResolvedValueOnce(mockEscalations)

            const { result } = renderHook(() => useEscalations(), { wrapper })

            await waitFor(() => expect(result.current.isSuccess).toBe(true))

            expect(result.current.data?.content[0].team).toEqual({ name: 'support' })
        })

        it('should handle null team', async () => {
            mockedApiGet.mockResolvedValueOnce(mockEscalations)

            const { result } = renderHook(() => useEscalations(), { wrapper })

            await waitFor(() => expect(result.current.isSuccess).toBe(true))

            expect(result.current.data?.content[1].team).toBeNull()
        })

        it('should handle null tags', async () => {
            mockedApiGet.mockResolvedValueOnce(mockEscalations)

            const { result } = renderHook(() => useEscalations(), { wrapper })

            await waitFor(() => expect(result.current.isSuccess).toBe(true))

            expect(result.current.data?.content[1].tags).toEqual([])
        })

        it('should preserve pagination metadata', async () => {
            mockedApiGet.mockResolvedValueOnce(mockEscalations)

            const { result } = renderHook(() => useEscalations(), { wrapper })

            await waitFor(() => expect(result.current.isSuccess).toBe(true))

            expect(result.current.data?.page).toBe(0)
            expect(result.current.data?.totalPages).toBe(1)
            expect(result.current.data?.totalElements).toBe(2)
        })

        it('should use correct query key', async () => {
            mockedApiGet.mockResolvedValueOnce(mockEscalations)

            const { result } = renderHook(() => useEscalations(1, 25), { wrapper })

            await waitFor(() => expect(result.current.isSuccess).toBe(true))

            const cachedData = queryClient.getQueryData(['escalations', 1, 25])
            expect(cachedData).toBeDefined()
        })
    })

    describe('useRatings', () => {
        const mockStatsResponse = [
            {
                type: 'ticket-ratings',
                values: {
                    average: 4.5,
                    count: 100,
                    weekly: [
                        { weekStart: '2025-01-06', average: 4.8, count: 12 },
                        { weekStart: '2025-01-13', average: 4.2, count: 8 },
                    ],
                },
                weekly: [
                    { weekStart: '2025-02-03', average: 4.9, count: 10 },
                ],
            }
        ]

        it('should fetch ratings without date range', async () => {
            mockedApiPost.mockResolvedValueOnce(mockStatsResponse)

            const { result } = renderHook(() => useRatings(), { wrapper })

            await waitFor(() => expect(result.current.isSuccess).toBe(true))

            expect(mockedApiPost).toHaveBeenCalledWith('/stats', [
                { type: 'ticket-ratings' }
            ])
            expect(result.current.data).toEqual(mockStatsResponse[0].values)
        })

        it('should fetch ratings with date range', async () => {
            mockedApiPost.mockResolvedValueOnce(mockStatsResponse)

            const { result } = renderHook(
                () => useRatings('2025-01-01', '2025-01-31'),
                { wrapper }
            )

            await waitFor(() => expect(result.current.isSuccess).toBe(true))

            expect(mockedApiPost).toHaveBeenCalledWith('/stats', [
                {
                    type: 'ticket-ratings',
                    from: '2025-01-01',
                    to: '2025-01-31'
                }
            ])
        })

        it('should return default values when response is empty', async () => {
            mockedApiPost.mockResolvedValueOnce([])

            const { result } = renderHook(() => useRatings(), { wrapper })

            await waitFor(() => expect(result.current.isSuccess).toBe(true))

            expect(result.current.data).toEqual({ average: null, count: null, weekly: undefined })
        })

        it('should return default values when response has no values', async () => {
            mockedApiPost.mockResolvedValueOnce([{ type: 'ticket-ratings' }])

            const { result } = renderHook(() => useRatings(), { wrapper })

            await waitFor(() => expect(result.current.isSuccess).toBe(true))

            expect(result.current.data).toEqual({ average: null, count: null, weekly: undefined })
        })

        it('should use correct query key with date range', async () => {
            mockedApiPost.mockResolvedValueOnce(mockStatsResponse)

            const { result } = renderHook(
                () => useRatings('2025-01-01', '2025-12-31'),
                { wrapper }
            )

            await waitFor(() => expect(result.current.isSuccess).toBe(true))

            const cachedData = queryClient.getQueryData(['ratings', '2025-01-01', '2025-12-31'])
            expect(cachedData).toBeDefined()
        })
    })

    describe('useRegistry', () => {
        const mockImpacts = [
            { code: 'high', label: 'High Impact' },
            { code: 'low', label: 'Low Impact' }
        ]
        const mockTags = [
            { code: 'bug', label: 'Bug' },
            { code: 'feature', label: 'Feature' }
        ]

        it('should fetch both impacts and tags', async () => {
            mockedApiGet.mockResolvedValueOnce(mockImpacts)
            mockedApiGet.mockResolvedValueOnce(mockTags)

            const { result } = renderHook(() => useRegistry(), { wrapper })

            await waitFor(() => expect(result.current.isSuccess).toBe(true))

            expect(mockedApiGet).toHaveBeenCalledWith('/registry/impact')
            expect(mockedApiGet).toHaveBeenCalledWith('/registry/tag')
            expect(result.current.data).toEqual({
                impacts: mockImpacts,
                tags: mockTags
            })
        })

        it('should use correct query key', async () => {
            mockedApiGet.mockResolvedValueOnce(mockImpacts)
            mockedApiGet.mockResolvedValueOnce(mockTags)

            const { result } = renderHook(() => useRegistry(), { wrapper })

            await waitFor(() => expect(result.current.isSuccess).toBe(true))

            const cachedData = queryClient.getQueryData(['registry'])
            expect(cachedData).toBeDefined()
        })

        it('should handle API errors', async () => {
            mockedApiGet.mockRejectedValueOnce(new Error('Failed to fetch impacts'))

            const { result } = renderHook(() => useRegistry(), { wrapper })

            await waitFor(() => expect(result.current.isError).toBe(true))

            expect(result.current.error).toBeDefined()
        })
    })
})

