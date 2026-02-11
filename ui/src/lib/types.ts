// lib/types.ts

// Re-export dashboard types
export * from './types/dashboard'

interface RawEscalation {
    id: number | { id: number }
    ticketId: number | { id: number }
    threadLink: string
    openedAt: string
    resolvedAt: string | null
    escalatingTeam: string
    team: {
        name: string
        types: string[]
    } | null
    tags?: string[]
    impact?: string | null
}

export interface RawPaginatedEscalations {
    content: RawEscalation[]
    page: number
    totalPages: number
    totalElements: number
}


export interface Escalation {
    id: string   // stringify the EscalationId
    ticketId: string
    threadLink: string
    openedAt: string
    resolvedAt: string | null
    team: { name: string } | null
    escalatingTeam: string
    tags: string[]
    impact: string | null
}

export interface PaginatedEscalations {
    content: Escalation[]
    page: number
    totalPages: number
    totalElements: number
}

export type TicketLog = {
    date: string
    event: string
}

export interface ParsedTicketLog extends TicketLog {
    parsedDate: Date
}

export type TicketWithLogs = {
    id: string
    status: string
    team?: { name: string } | null
    query?: {
        link?: string
        text?: string
        date?: string
    }
    impact: string
    tags?: string[]
    escalations?: Escalation[]
    logs?: TicketLog[]
    ratingSubmitted?: boolean
    assignedTo?: string | null
}

export type TicketTeam = {
    name: string
}

export interface EscalationTeam extends TicketTeam {
    types: string[]
}

export type TicketImpact = {
    code: string
    label: string
}

export type TicketTag = {
    code: string
    label: string
}

export interface PaginatedTickets {
    content: TicketWithLogs[]
    page: number
    totalPages: number
    totalElements: number
}
export type TimelineFilter = 'all' | 'year' | 'month' | 'week' | 'today'

export interface AggregatedTicketStats {
    date: string
    opened: number
    closed: number
    escalated: number
}

export type RatingStats = {
  average: number
  count: number
}

export interface SupportMember {
    userId: string      // Slack user ID (used as value in API calls)
    displayName: string // Email (used for display in UI)
}

export interface AssignmentStatus {
    enabled: boolean
}

export interface KnowledgeGapsStatus {
    enabled: boolean
}

export interface BulkReassignRequest {
    ticketIds: string[]  // Array of ticket IDs
    assignedTo: string    // Slack user ID
}

export interface BulkReassignResult {
    successCount: number
    successfulTicketIds: string[]
    message: string
}
