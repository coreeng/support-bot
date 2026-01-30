// src/lib/hooks/backend.ts
import { useQuery } from '@tanstack/react-query'
import { apiGet, apiPost } from '../api'
import { AssignmentStatus, Escalation, EscalationTeam, PaginatedEscalations, PaginatedTickets, RawPaginatedEscalations, SupportMember, TicketWithLogs } from '@/lib/types'

/**
 * Backend API hooks for Spring Boot services
 * These fetch from the proxied backend API endpoints
 */

// Helper types for backend responses
interface BackendTeam {
    label?: string
    code?: string
    types?: string[]
}

interface BackendEscalation {
    team?: BackendTeam
    [key: string]: unknown
}

// ===== Ticket Hooks =====

// Map a single ticket to frontend shape
const mapTicket = (ticket: Record<string, unknown>) => ({
    ...ticket,
    team: ticket.team && typeof ticket.team === 'object' && ticket.team !== null
        ? {
            name:
                (ticket.team as BackendTeam).code ||
                (ticket.team as BackendTeam).label ||
                (ticket.team as { name?: string }).name,
        }
        : null,
    escalations: Array.isArray(ticket.escalations)
        ? ticket.escalations.map((esc: BackendEscalation) => ({
            ...esc,
            team: esc.team ? { name: esc.team.code || esc.team.label } : null
        }))
        : []
})

export function useTickets(page: number = 0, pageSize: number = 50, from?: string, to?: string) {
    return useQuery<PaginatedTickets>({
        queryKey: ['tickets', page, pageSize, from, to],
        queryFn: async () => {
            const params = new URLSearchParams({
                page: page.toString(),
                pageSize: pageSize.toString()
            })
            if (from) params.append('dateFrom', from)
            if (to) params.append('dateTo', to)

            const data = await apiGet(`/ticket?${params}`)
            if (data.content) {
                data.content = data.content.map(mapTicket)
            }
            return data
        }
    })
}

// Fetch all tickets across pages (larger per-page) for client-side filtering when needed
export function useAllTickets(pageSize: number = 200, from?: string, to?: string, enabled = true) {
    return useQuery<PaginatedTickets>({
        queryKey: ['tickets', 'all', pageSize, from, to],
        enabled,
        queryFn: async () => {
            const params = new URLSearchParams({
                page: '0',
                pageSize: pageSize.toString()
            })
            if (from) params.append('dateFrom', from)
            if (to) params.append('dateTo', to)

            const first = await apiGet(`/ticket?${params}`)
            const firstMappedContent = Array.isArray(first.content) ? first.content.map(mapTicket) : []
            const totalPages = first.totalPages ?? 1
            const pagesToFetch = []
            for (let p = 1; p < totalPages; p++) {
                const pageParams = new URLSearchParams({
                    page: p.toString(),
                    pageSize: pageSize.toString()
                })
                if (from) pageParams.append('dateFrom', from)
                if (to) pageParams.append('dateTo', to)
                pagesToFetch.push(apiGet(`/ticket?${pageParams}`))
            }

            const rest = pagesToFetch.length > 0 ? await Promise.all(pagesToFetch) : []
            const mappedRest = rest.map(res => ({
                ...res,
                content: Array.isArray(res.content) ? res.content.map(mapTicket) : []
            }))
            const allContent = [
                ...firstMappedContent,
                ...mappedRest.flatMap(res => res.content || []),
            ] as TicketWithLogs[]

            return {
                page: 0,
                totalPages,
                totalElements: allContent.length,
                content: allContent,
            }
        }
    })
}

export function useTicket(id: string | undefined) {
    return useQuery({
        queryKey: ['ticket', id],
        queryFn: async () => {
            const ticket = await apiGet(`/ticket/${id}`)
            if (ticket.team) {
                ticket.team = { name: ticket.team.code || ticket.team.label }
            }
            // Also map escalation teams
            if (Array.isArray(ticket.escalations)) {
                ticket.escalations = ticket.escalations.map((esc: BackendEscalation) => ({
                    ...esc,
                    team: esc.team ? { name: esc.team.code || esc.team.label } : null
                }))
            }
            return ticket
        },
        enabled: !!id,
    })
}

// ===== Team Hooks =====

export function useEscalationTeams(enabled: boolean = true) {
    return useQuery<EscalationTeam[]>({
        queryKey: ['team', 'escalation'],  // Unique key for escalation teams
        enabled,  // Only fetch when enabled
        queryFn: async () => {
            const teams = await apiGet('/team?type=escalation')
            // Map backend response: use code as canonical identifier to match session
            return teams.map((team: BackendTeam) => ({
                name: team.code || team.label || '',  // Use code as name to match session
                types: team.types || []
            }))
        },
    })
}

export function useTenantTeams() {
    return useQuery({
        queryKey: ['team', 'tenant'],  // Unique key for tenant teams
        queryFn: async () => {
            const teams = await apiGet('/team?type=tenant')
            return teams.map((team: BackendTeam) => ({
                name: team.code || team.label || ''  // Use code as name to match session
            }))
        }
    })
}

// ===== User Hooks =====

export function useSupportMembers() {
    return useQuery<SupportMember[]>({
        queryKey: ['user', 'support'],
        queryFn: async () => {
            const members = await apiGet('/user/support')
            return members || []
        }
    })
}

// ===== Assignment Hooks =====

export function useAssignmentEnabled() {
    return useQuery<boolean>({
        queryKey: ['assignment', 'enabled'],
        queryFn: async () => {
            const response: AssignmentStatus = await apiGet('/assignment/enabled')
            return response.enabled
        },
        staleTime: 5 * 60 * 1000, // Cache for 5 minutes since this rarely changes
    })
}

// ===== Escalation Hooks =====

export function useEscalations(page: number = 0, pageSize: number = 50) {
    return useQuery<PaginatedEscalations>({
        queryKey: ['escalations', page, pageSize],
        queryFn: async () => {
            const res = await apiGet(`/escalation?page=${page}&pageSize=${pageSize}&escalated=true`) as RawPaginatedEscalations

            const mapped: PaginatedEscalations = {
                page: res.page,
                totalPages: res.totalPages,
                totalElements: res.totalElements,
                content: res.content.map((e): Escalation => {
                    const teamData = e.team as BackendTeam | null | undefined
                    return {
                        id: typeof e.id === 'object' ? e.id.id.toString() : e.id.toString(),
                        ticketId: typeof e.ticketId === 'object' ? e.ticketId.id.toString() : e.ticketId.toString(),
                        threadLink: e.threadLink,
                        openedAt: e.openedAt,
                        resolvedAt: e.resolvedAt,
                        escalatingTeam: e.escalatingTeam,
                        team: teamData ? { name: teamData.code || teamData.label || '' } : null,
                        tags: e.tags ?? [],
                        impact: e.impact ?? null,
                    }
                })
            }

            return mapped
        }
    })
}

// ===== Registry & Ratings Hooks =====

export type RatingWeekly = { weekStart: string; average: number | null; count: number | null }
export type RatingsResult = { average: number | null; count: number | null; weekly?: RatingWeekly[] }

export const useRatings = (from?: string, to?: string) => {
    return useQuery({
        queryKey: ['ratings', from, to],
        queryFn: async (): Promise<RatingsResult> => {
            const request: Record<string, unknown> = { type: 'ticket-ratings' }
            if (from) request.from = from
            if (to) request.to = to

            const response = await apiPost('/stats', [request])

            const first = response && response.length > 0 ? response[0] : null
            if (first?.values) {
                // weekly may come either under values or as a top-level field (backend change)
                const values = first.values as RatingsResult
                const rootWeekly = (first as RatingsResult).weekly
                const weekly = Array.isArray(values.weekly) ? values.weekly : Array.isArray(rootWeekly) ? rootWeekly : undefined
                const average = values.average ?? null
                const count = values.count ?? null
                return {
                    average,
                    count,
                    weekly,
                }
            }

            return { average: null, count: null, weekly: undefined }
        },
    })
}

export function useRegistry() {
    return useQuery({
        queryKey: ['registry'],
        queryFn: async () => {
            const impacts = await apiGet('/registry/impact')
            const tags = await apiGet('/registry/tag')
            return { impacts, tags }
        }
    })
}

