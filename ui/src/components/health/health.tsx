'use client'

import {useMemo, useState} from 'react'
import {useRatings, useRegistry, useTickets, useSupportMembers, useAssignmentEnabled} from '@/lib/hooks'
import {ClipboardList, Star, AlertTriangle, Headphones, ChevronDown} from 'lucide-react'
import LoadingSkeleton from '@/components/LoadingSkeleton'
import {useQueryClient} from '@tanstack/react-query'
import {
    Bar,
    BarChart,
    CartesianGrid,
    Cell,
    Legend,
    Line,
    LineChart,
    ResponsiveContainer,
    Tooltip,
    XAxis,
    YAxis,
} from 'recharts'
import {
    AggregatedTicketStats,
    BulkReassignRequest,
    BulkReassignResult,
    Escalation,
    ParsedTicketLog,
    TicketImpact,
    TicketLog,
    TicketWithLogs,
    SupportMember
} from "@/lib/types";

export default function HealthPage() {
    const {data: registryData} = useRegistry()
    const {data: supportMembers} = useSupportMembers()
    const {data: isAssignmentEnabled} = useAssignmentEnabled()
    const queryClient = useQueryClient()

    const tabs = [
        {key: 'tickets' as const, label: 'Activity Trends', icon: ClipboardList, color: 'blue'},
        {key: 'ratings' as const, label: 'Ratings', icon: Star, color: 'yellow'},
        {key: 'workbench' as const, label: 'Ticket Workbench', icon: Headphones, color: 'purple'},
    ]

    const [dateFilterMode, setDateFilterMode] = useState<'week' | 'month' | 'year' | 'custom'>('week')
    const [startDate, setStartDate] = useState<string>(() => {
        const date = new Date()
        date.setMonth(date.getMonth() - 1)
        return date.toISOString().split('T')[0]
    })
    const [endDate, setEndDate] = useState<string>(() => new Date().toISOString().split('T')[0])
    const [inquiringTeamFilter, setInquiringTeamFilter] = useState('')
    const [escalatedTeamFilter, setEscalatedTeamFilter] = useState('')
    const [statusFilter, setStatusFilter] = useState('')
    const [ratedFilter, setRatedFilter] = useState('')
    const [assigneeFilter, setAssigneeFilter] = useState('')
    const [bulkReassignFrom, setBulkReassignFrom] = useState('')
    const [bulkReassignTo, setBulkReassignTo] = useState('')
    const [isReassigning, setIsReassigning] = useState(false)
    const [reassignMessage, setReassignMessage] = useState<{type: 'success' | 'error', text: string} | null>(null)
    const [showConfirmation, setShowConfirmation] = useState(false)
    const [confirmationDetails, setConfirmationDetails] = useState<{from: string, to: string, count: number, tickets: TicketWithLogs[]} | null>(null)
    const [bulkReassignExpanded, setBulkReassignExpanded] = useState(false)
    const [capacityInsightsExpanded, setCapacityInsightsExpanded] = useState(false)
    const [activeTab, setActiveTab] = useState<'tickets' | 'ratings' | 'workbench'>('tickets')
    const [engineersOnRota, setEngineersOnRota] = useState<number>(2) // Default to 2, configurable
    const [ticketsPerEngineerCapacity, setTicketsPerEngineerCapacity] = useState<number>(5) // Default to 5, configurable
    
    const now = useMemo(() => new Date(), [])
    const isDateRangeValid = startDate <= endDate

    // Calculate date range based on filter mode (aligned to SLA dashboard style)
    // When switching to "custom", preserve the current range until custom dates are set
    const dateRange = useMemo(() => {
        if (dateFilterMode === 'custom') {
            // If custom dates are not set or invalid, preserve the previous filter's range
            if (!isDateRangeValid || !startDate || !endDate) {
                // Calculate the current range based on week (default)
                const toDate = new Date()
                const fromDate = new Date(toDate)
                fromDate.setDate(toDate.getDate() - 7)
                const from = fromDate.toISOString().split('T')[0]
                const to = toDate.toISOString().split('T')[0]
                return { from, to }
            }
            return { from: startDate || undefined, to: endDate || undefined }
        }

        const toDate = new Date()
        const fromDate = new Date(toDate)

        if (dateFilterMode === 'week') {
            fromDate.setDate(toDate.getDate() - 7)
        } else if (dateFilterMode === 'month') {
            fromDate.setMonth(toDate.getMonth() - 1)
        } else if (dateFilterMode === 'year') {
            fromDate.setFullYear(toDate.getFullYear() - 1)
        }

        const from = fromDate.toISOString().split('T')[0]
        const to = toDate.toISOString().split('T')[0]
        return { from, to }
    }, [dateFilterMode, startDate, endDate, isDateRangeValid])
    
    const {data: tickets, isLoading: ticketsLoading} = useTickets(0, 1000, dateRange.from, dateRange.to)
    const {data: ratingStats, isLoading: ratingsLoading} = useRatings(dateRange.from, dateRange.to)
    const [currentPage, setCurrentPage] = useState(1)
    const ticketsPerPage = 10
    const COLORS = ['#0088FE', '#00C49F', '#FFBB28', '#FF8042', '#A28EFF', '#FF6699', '#33CC99']
    const statusColors: Record<string, string> = {
        opened: 'bg-blue-100 text-blue-800',
        closed: 'bg-green-100 text-green-800',
    }

    function getOpenedClosed(ticket: { logs?: TicketLog[] }) {
        const logs = Array.isArray(ticket.logs) ? ticket.logs.slice() : []

        if (!logs.length) return {opened: null, closed: null}

        // Parse and filter logs
        const parsed: ParsedTicketLog[] = logs
            .map((log): ParsedTicketLog | null => {
                const date = log?.date ? new Date(log.date) : null
                return date && !isNaN(date.getTime()) ? {...log, parsedDate: date} : null
            })
            .filter((log): log is ParsedTicketLog => log !== null)

        if (!parsed.length) return {opened: null, closed: null}

        // Sort by parsed date ascending
        parsed.sort((a, b) => a.parsedDate.getTime() - b.parsedDate.getTime())

        const opened = parsed[0].parsedDate

        // Find the last log that indicates closing/resolving
        const closedLog = [...parsed].reverse().find(log =>
            log.event.toLowerCase().includes('close') ||
            log.event.toLowerCase().includes('resolve') ||
            log.event.toLowerCase().includes('closed')
        )

        const closed = closedLog ? closedLog.parsedDate : null

        return {opened, closed}
    }


    const ensureKey = (map: Record<string, AggregatedTicketStats>, key: string): AggregatedTicketStats => {
        if (!map[key]) {
            map[key] = {
                date: key,
                opened: 0,
                closed: 0,
                escalated: 0,
            }
        }
        return map[key]
    }


    // Tickets are now filtered by date on the server side
    const filteredTickets = useMemo(() => {
        if (!tickets?.content) return []

        return tickets.content.filter((t: TicketWithLogs) => {
            const escalations = Array.isArray(t.escalations) ? t.escalations : []

            const inquiringMatch = inquiringTeamFilter
                ? t.team?.name === inquiringTeamFilter
                : true

            const escalatedMatch = escalatedTeamFilter
                ? escalations.some((e: Escalation) => {
                    if (!e.team?.name) return false
                    // Case-insensitive matching for robustness
                    return e.team.name.trim().toLowerCase() === escalatedTeamFilter.trim().toLowerCase()
                })
                : true

            const statusMatch = statusFilter
                ? t.status?.toLowerCase() === statusFilter.toLowerCase()
                : true

            const ratedMatch = ratedFilter
                ? (ratedFilter === 'yes' ? t.ratingSubmitted : !t.ratingSubmitted)
                : true

            const assigneeMatch = assigneeFilter
                ? (assigneeFilter === 'unassigned' ? !t.assignedTo : t.assignedTo === assigneeFilter)
                : true

            return inquiringMatch && escalatedMatch && statusMatch && ratedMatch && assigneeMatch
        })
    }, [tickets, inquiringTeamFilter, escalatedTeamFilter, statusFilter, ratedFilter, assigneeFilter])


    // --- Metrics ---
    // Tickets are already filtered by date from server
    const openedTickets = useMemo(() => {
        return filteredTickets.filter((t: TicketWithLogs) => {
            const { opened } = getOpenedClosed(t)
            return opened !== null
        })
    }, [filteredTickets])

    const closedTickets = useMemo(() => {
        return filteredTickets.filter((t: TicketWithLogs) => {
            const { closed } = getOpenedClosed(t)
            return closed !== null
        })
    }, [filteredTickets])


    const staleTicketsCount = useMemo(() => {
        return filteredTickets.filter((t: TicketWithLogs) => t.status === 'stale').length
    }, [filteredTickets])

    // Backend now calculates average rating and weekly breakdown
    const avgRating = ratingStats?.average || 0
    const totalRatings = ratingStats?.count || 0
    const weeklyRatings = useMemo(() => ratingStats?.weekly || [], [ratingStats])


    const avgResolutionTimeSecs = useMemo(() => {
        if (!filteredTickets.length) return 0
        const totalSecs = filteredTickets.reduce((acc, t) => {
            const {opened, closed} = getOpenedClosed(t)
            if (!opened || !closed) return acc
            return acc + (closed.getTime() - opened.getTime()) / 1000
        }, 0)
        return totalSecs / filteredTickets.length
    }, [filteredTickets])

    const largestActiveTicketSecs = useMemo(() => {
        if (!filteredTickets.length) return 0
        return Math.max(...filteredTickets.map(t => {
            const {opened, closed} = getOpenedClosed(t)
            if (!opened) return 0
            return ((closed || now).getTime() - opened.getTime()) / 1000
        }))
    }, [filteredTickets, now])

    const percentageRated = useMemo(() => {
        if (!filteredTickets.length) return 0
        return Math.round((filteredTickets.filter(t => t.ratingSubmitted).length / filteredTickets.length) * 100)
    }, [filteredTickets])

    // --- Assignment data: average tickets per support engineer by day ---
    const assignmentsByDay = useMemo(() => {
        if (!isAssignmentEnabled || !supportMembers || supportMembers.length === 0) {
            return []
        }

        const map: Record<string, { date: string; totalAssignments: number; engineerCount: number }> = {}

        filteredTickets.forEach(t => {
            if (t.assignedTo) {
                // Use the opened date as the assignment date
                const {opened} = getOpenedClosed(t)
                if (opened) {
                    const dateKey = opened.toISOString().split('T')[0]
                    if (!map[dateKey]) {
                        map[dateKey] = { date: dateKey, totalAssignments: 0, engineerCount: supportMembers.length }
                    }
                    map[dateKey].totalAssignments++
                }
            }
        })

        // Calculate average and sort by date
        const values = Object.values(map).map(entry => ({
            date: entry.date,
            avgAssignments: entry.engineerCount > 0 
                ? parseFloat((entry.totalAssignments / entry.engineerCount).toFixed(2))
                : 0,
            totalAssignments: entry.totalAssignments
        })).sort((a, b) => new Date(a.date).getTime() - new Date(b.date).getTime())

        return values
    }, [filteredTickets, isAssignmentEnabled, supportMembers])

    // --- Timeline data: aggregate by ISO date (YYYY-MM-DD) ---
    const timelineData = useMemo(() => {
        const map: Record<string, { date: string; opened: number; closed: number; escalated: number }> = {}

        filteredTickets.forEach(t => {
            const {opened, closed} = getOpenedClosed(t)
            const escalations = Array.isArray(t.escalations) ? t.escalations : []

            if (opened) ensureKey(map, opened.toISOString().split('T')[0]).opened++
            if (closed) ensureKey(map, closed.toISOString().split('T')[0]).closed++
            escalations.forEach((esc: Escalation) => {
                const escDate = esc?.openedAt ? new Date(esc.openedAt) : null
                if (escDate && !isNaN(escDate.getTime())) {
                    ensureKey(map, escDate.toISOString().split('T')[0]).escalated++
                }
            })
        })

        // If there's no data (but there are tickets overall), produce a single point for today (helps avoid empty charts)
        const values = Object.values(map).sort((a, b) => new Date(a.date).getTime() - new Date(b.date).getTime())
        if (values.length === 0 && filteredTickets.length > 0) {
            const todayKey = now.toISOString().split('T')[0]
            return [{date: todayKey, opened: 0, closed: 0, escalated: 0}]
        }
        return values
    }, [filteredTickets, now])

    // --- Pagination for tickets table ---
    const paginatedTickets = useMemo(() => {
        const start = (currentPage - 1) * ticketsPerPage
        return filteredTickets.slice(start, start + ticketsPerPage)
    }, [filteredTickets, currentPage])

    // Ratings per week from backend (fallback to empty)
    const ratingsByWeek = useMemo(() => {
        return (weeklyRatings || [])
            .map(({ weekStart, count }) => ({
                weekStart,
                count: count ?? 0,
            }))
            .sort((a, b) => new Date(a.weekStart).getTime() - new Date(b.weekStart).getTime())
    }, [weeklyRatings])

    // --- Tickets opened by requesting team (Top 10) ---
    const ticketsByTeam = useMemo(() => {
        const counts: Record<string, number> = {}
        filteredTickets.forEach(t => {
            const teamName = t.team?.name || 'Unassigned Team'
            counts[teamName] = (counts[teamName] || 0) + 1
        })
        return Object.entries(counts)
            .map(([name, value]) => ({ name, value }))
            .sort((a, b) => b.value - a.value)
            .slice(0, 10) // Top 10 only
    }, [filteredTickets])

    // --- Current active tickets per engineer ---
    const activeTicketsPerEngineer = useMemo(() => {
        if (!isAssignmentEnabled || !supportMembers || supportMembers.length === 0) {
            return []
        }
        
        const counts: Record<string, number> = {}
        const openTickets = filteredTickets.filter(t => t.status?.toLowerCase() === 'opened')
        
        openTickets.forEach(t => {
            if (t.assignedTo) {
                counts[t.assignedTo] = (counts[t.assignedTo] || 0) + 1
            }
        })
        
        // Include all engineers, even if they have 0 tickets
        return supportMembers.map(member => ({
            name: member.displayName,
            tickets: counts[member.displayName] || 0
        })).sort((a, b) => b.tickets - a.tickets)
    }, [filteredTickets, isAssignmentEnabled, supportMembers])

    // --- Tickets opened by hour of day (7AM-7PM only, using ticket time) ---
    const ticketsByHour = useMemo(() => {
        const counts: Record<number, number> = {}
        // Only track hours 7-19 (7AM to 7PM)
        for (let i = 7; i <= 19; i++) {
            counts[i] = 0
        }
        
        filteredTickets.forEach(t => {
            const {opened} = getOpenedClosed(t)
            if (opened) {
                const hour = opened.getHours() // Uses ticket's opened time
                // Only count hours between 7AM and 7PM
                if (hour >= 7 && hour <= 19) {
                    counts[hour] = (counts[hour] || 0) + 1
                }
            }
        })
        
        return Object.entries(counts)
            .map(([hour, count]) => ({
                hour: parseInt(hour),
                hourLabel: `${String(hour).padStart(2, '0')}:00`,
                count
            }))
            .sort((a, b) => a.hour - b.hour)
    }, [filteredTickets])

    // --- Busiest periods heatmap (day of week × hour) - Weekdays only, 7AM-7PM, using ticket time ---
    const busiestPeriods = useMemo(() => {
        const days = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday']
        const dayIndexMap: Record<number, string> = {
            1: 'Monday',
            2: 'Tuesday',
            3: 'Wednesday',
            4: 'Thursday',
            5: 'Friday'
        }
        const heatmap: Record<string, Record<number, number>> = {}
        
        days.forEach(day => {
            heatmap[day] = {}
            // Only track hours 7-19 (7AM to 7PM)
            for (let hour = 7; hour <= 19; hour++) {
                heatmap[day][hour] = 0
            }
        })
        
        filteredTickets.forEach(t => {
            const {opened} = getOpenedClosed(t)
            if (opened) {
                const dayOfWeek = opened.getDay() // 0 = Sunday, 1 = Monday, ..., 6 = Saturday
                const dayName = dayIndexMap[dayOfWeek]
                if (dayName) { // Only include weekdays
                    const hour = opened.getHours() // Uses ticket's opened time
                    // Only count hours between 7AM and 7PM
                    if (hour >= 7 && hour <= 19) {
                        heatmap[dayName][hour] = (heatmap[dayName][hour] || 0) + 1
                    }
                }
            }
        })
        
        // Convert to array format for visualization
        return days.map(day => {
            const row: { day: string; [key: number]: number } = { day }
            for (let hour = 7; hour <= 19; hour++) {
                row[hour] = heatmap[day][hour]
            }
            return row
        })
    }, [filteredTickets])

    // --- Capacity Insights by 2-Hour Blocks ---
    const capacityInsights = useMemo(() => {
        if (!isAssignmentEnabled || !busiestPeriods.length) {
            return []
        }

        const rotaCount = Math.max(1, engineersOnRota)
        const capacityPerEngineer = Math.max(1, ticketsPerEngineerCapacity)
        const currentCapacity = rotaCount * capacityPerEngineer

        // Count actual weekdays that have tickets by checking busiestPeriods data
        // This avoids iterating through filteredTickets again (important for scalability)
        // A weekday has tickets if any hour in that day has tickets > 0
        const weekdaysWithTickets = new Set<string>()
        busiestPeriods.forEach(row => {
            const dayName = row.day
            // Check if this day has any tickets in any hour (7-19)
            for (let hour = 7; hour <= 19; hour++) {
                if ((row[hour] as number) > 0) {
                    weekdaysWithTickets.add(dayName)
                    break // Found tickets for this day, no need to check other hours
                }
            }
        })
        const actualWeekdaysCount = Math.max(1, weekdaysWithTickets.size) // At least 1 to avoid division by zero

        // Group hours into 2-hour blocks: 7-9, 9-11, 11-13, 13-15, 15-17, 17-19
        const timeBlocks: Array<{ start: number; end: number; label: string }> = []
        for (let start = 7; start < 19; start += 2) {
            const end = start + 2
            const formatHour = (h: number) => {
                if (h < 12) return `${h} AM`
                if (h === 12) return '12 PM'
                return `${h - 12} PM`
            }
            timeBlocks.push({
                start,
                end,
                label: `${formatHour(start)} - ${formatHour(end)}`
            })
        }

        const insights: Array<{
            timeBlock: string
            avgTickets: number
            currentCapacity: number
            utilization: number
            recommendedRange: string
            status: 'over' | 'near' | 'under'
        }> = []

        timeBlocks.forEach(block => {
            // Aggregate tickets across all weekdays for this 2-hour block
            let totalTicketsInBlock = 0
            busiestPeriods.forEach(row => {
                for (let hour = block.start; hour < block.end; hour++) {
                    totalTicketsInBlock += row[hour] as number
                }
            })

            // Average per weekday (across 2 hours) - only count weekdays that actually have tickets
            const avgTickets = actualWeekdaysCount > 0 
                ? parseFloat((totalTicketsInBlock / actualWeekdaysCount).toFixed(2))
                : 0
            
            // Capacity for 2-hour block (same as single hour since it's concurrent capacity)
            const utilization = currentCapacity > 0 
                ? Math.round((avgTickets / currentCapacity) * 100)
                : 0

            // Calculate recommended engineers (as range)
            const minRecommended = Math.ceil(avgTickets / capacityPerEngineer)
            const maxRecommended = Math.ceil((avgTickets * 1.2) / capacityPerEngineer) // Add 20% buffer
            const recommendedRange = minRecommended === maxRecommended
                ? `${minRecommended}`
                : `${minRecommended}-${maxRecommended}`

            // Determine status
            let status: 'over' | 'near' | 'under' = 'under'
            if (utilization > 100) {
                status = 'over'
            } else if (utilization > 80) {
                status = 'near'
            }

            insights.push({
                timeBlock: block.label,
                avgTickets,
                currentCapacity,
                utilization,
                recommendedRange,
                status
            })
        })

        return insights
    }, [busiestPeriods, isAssignmentEnabled, engineersOnRota, ticketsPerEngineerCapacity])

    // --- Capacity vs Demand ---
    const capacityVsDemand = useMemo(() => {
        if (!isAssignmentEnabled) {
            return null
        }
        
        const openTickets = filteredTickets.filter(t => t.status?.toLowerCase() === 'opened')
        const ticketsCount = openTickets.length
        
        // Use engineersOnRota instead of total support members
        const rotaCount = Math.max(1, engineersOnRota) // Ensure at least 1
        
        const capacityPerEngineer = Math.max(1, ticketsPerEngineerCapacity) // Ensure at least 1
        const totalCapacity = rotaCount * capacityPerEngineer
        
        return {
            engineersOnRota: rotaCount,
            totalEngineers: supportMembers?.length || 0,
            openTickets: ticketsCount,
            ticketsPerEngineer: rotaCount > 0 
                ? parseFloat((ticketsCount / rotaCount).toFixed(2))
                : 0,
            capacityPerEngineer: capacityPerEngineer,
            totalCapacity: totalCapacity,
            capacityUtilization: totalCapacity > 0
                ? Math.round((ticketsCount / totalCapacity) * 100) // Can exceed 100% when over capacity
                : 0
        }
    }, [filteredTickets, isAssignmentEnabled, engineersOnRota, ticketsPerEngineerCapacity, supportMembers])

    const avgRatingsByWeek = useMemo(() => {
        return (weeklyRatings || [])
            .map(({ weekStart, average }) => ({
                weekStart,
                average: average ?? 0,
            }))
            .sort((a, b) => new Date(a.weekStart).getTime() - new Date(b.weekStart).getTime())
    }, [weeklyRatings])

    // --- Teams lists for filters (safe) ---
    const inquiringTeamsWithTickets = useMemo(() => {
        const set = new Set<string>()
        tickets?.content?.forEach(t => {
            if (t.team?.name) set.add(t.team.name)
        })
        return Array.from(set)
    }, [tickets])

    const escalatedTeamsWithTickets = useMemo(() => {
        const set = new Set<string>()
        tickets?.content?.forEach(t => (t.escalations || []).forEach((e: Escalation) => {
            if (e.team?.name) set.add(e.team.name)
        }))
        return Array.from(set)
    }, [tickets])

    if (ticketsLoading || ratingsLoading) return <LoadingSkeleton />

    return (
        <div className="min-h-screen bg-gradient-to-br from-gray-50 to-gray-100">
            {/* Sticky header to mirror SLA dashboards */}
            <div className="sticky top-0 z-10 bg-white shadow-md border-b border-gray-200">
                <div className="max-w-[1600px] mx-auto px-8 py-4">
                    <div className="flex items-center justify-between mb-3">
                        <div>
                            <h1 className="text-2xl font-bold text-gray-900">Analytics &amp; Operations</h1>
                            <p className="text-xs text-gray-500 mt-0.5">Insights, trends, and ticket management tools</p>
                        </div>
                    </div>

                    <form 
                        className="flex flex-wrap items-center gap-2 py-2"
                        onSubmit={(e) => e.preventDefault()}
                    >
                        <button
                            type="button"
                            onClick={(e) => {
                                e.preventDefault()
                                e.stopPropagation()
                                const end = new Date()
                                const start = new Date()
                                start.setDate(start.getDate() - 7)
                                setStartDate(start.toISOString().split('T')[0])
                                setEndDate(end.toISOString().split('T')[0])
                                setDateFilterMode('week')
                                setCurrentPage(1)
                            }}
                            className={`px-3 py-1.5 text-xs font-medium rounded transition-all ${
                                dateFilterMode === 'week'
                                    ? 'bg-blue-600 text-white'
                                    : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                            }`}
                        >
                            Last 7 Days
                        </button>
                        <button
                            type="button"
                            onClick={(e) => {
                                e.preventDefault()
                                e.stopPropagation()
                                const end = new Date()
                                const start = new Date()
                                start.setMonth(start.getMonth() - 1)
                                setStartDate(start.toISOString().split('T')[0])
                                setEndDate(end.toISOString().split('T')[0])
                                setDateFilterMode('month')
                                setCurrentPage(1)
                            }}
                            className={`px-3 py-1.5 text-xs font-medium rounded transition-all ${
                                dateFilterMode === 'month'
                                    ? 'bg-blue-600 text-white'
                                    : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                            }`}
                        >
                            Last Month
                        </button>
                        <button
                            type="button"
                            onClick={(e) => {
                                e.preventDefault()
                                e.stopPropagation()
                                const end = new Date()
                                const start = new Date()
                                start.setFullYear(start.getFullYear() - 1)
                                setStartDate(start.toISOString().split('T')[0])
                                setEndDate(end.toISOString().split('T')[0])
                                setDateFilterMode('year')
                                setCurrentPage(1)
                            }}
                            className={`px-3 py-1.5 text-xs font-medium rounded transition-all ${
                                dateFilterMode === 'year'
                                    ? 'bg-blue-600 text-white'
                                    : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                            }`}
                        >
                            Last Year
                        </button>
                        <button
                            type="button"
                            onClick={(e) => {
                                e.preventDefault()
                                e.stopPropagation()
                                setDateFilterMode('custom')
                            }}
                            className={`px-3 py-1.5 text-xs font-medium rounded transition-all ${
                                dateFilterMode === 'custom'
                                    ? 'bg-blue-600 text-white'
                                    : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                            }`}
                        >
                            Custom
                        </button>

                        {dateFilterMode === 'custom' && (
                            <>
                                <input
                                    type="date"
                                    value={startDate}
                                    onChange={e => {
                                        e.preventDefault()
                                        e.stopPropagation()
                                        setStartDate(e.target.value)
                                        setCurrentPage(1)
                                    }}
                                    onBlur={e => {
                                        e.preventDefault()
                                        e.stopPropagation()
                                    }}
                                    className="border rounded px-2 py-1 text-sm"
                                />
                                <input
                                    type="date"
                                    value={endDate}
                                    onChange={e => {
                                        e.preventDefault()
                                        e.stopPropagation()
                                        setEndDate(e.target.value)
                                        setCurrentPage(1)
                                    }}
                                    onBlur={e => {
                                        e.preventDefault()
                                        e.stopPropagation()
                                    }}
                                    className="border rounded px-2 py-1 text-sm"
                                />
                            </>
                        )}
                        <span className="text-xs text-gray-500 ml-2">
                            📅 {startDate} → {endDate}
                        </span>
                        {!isDateRangeValid && (
                            <span className="text-xs text-red-600 font-medium ml-2">
                                ⚠️ Invalid range
                            </span>
                        )}
                    </form>
                </div>
            </div>

            <div className="max-w-[1600px] mx-auto px-8 py-6">
                <div className="bg-white rounded-xl shadow-lg border border-gray-200 overflow-hidden">
                    <div className="flex border-b border-gray-200 bg-gradient-to-r from-gray-50 to-gray-100">
                        {tabs.map(tab => {
                            const isActive = activeTab === tab.key
                            const colorClasses: Record<string, string> = {
                                blue: isActive ? 'border-blue-600 bg-blue-50' : 'border-transparent hover:bg-blue-50',
                                yellow: isActive ? 'border-yellow-500 bg-yellow-50' : 'border-transparent hover:bg-yellow-50',
                                orange: isActive ? 'border-orange-600 bg-orange-50' : 'border-transparent hover:bg-orange-50',
                            }
                            const textColor = isActive ? 'text-gray-900' : 'text-gray-600'
                            const Icon = tab.icon
                            return (
                                <button
                                    key={tab.key}
                                    onClick={() => setActiveTab(tab.key)}
                                    className={`flex-1 flex items-center justify-center gap-2 px-6 py-4 text-sm font-semibold border-b-3 transition-all duration-200 ${colorClasses[tab.color]}`}
                                >
                                    <Icon className={`w-5 h-5 ${isActive ? 'animate-pulse' : ''}`} />
                                    <span className={textColor}>{tab.label}</span>
                                </button>
                            )
                        })}
                    </div>

                    <div className="p-8 space-y-8">
                        {activeTab === 'tickets' && (
                            <>
                                <div className="bg-white shadow-sm rounded-lg p-4 border">
                                    <h2 className="font-semibold mb-2 text-gray-700 text-center">Tickets Timeline</h2>
                                    <div style={{width: '100%', height: 300}}>
                                        <ResponsiveContainer width="100%" height="100%">
                                            <LineChart data={timelineData}>
                                                <CartesianGrid strokeDasharray="3 3"/>
                                                <XAxis dataKey="date"/>
                                                <YAxis/>
                                                <Tooltip/>
                                                <Legend/>
                                                <Line type="monotone" dataKey="opened" stroke="#0088FE" activeDot={{r: 6}}/>
                                                <Line type="monotone" dataKey="closed" stroke="#00C49F"/>
                                                <Line type="monotone" dataKey="escalated" stroke="#FF8042"/>
                                            </LineChart>
                                        </ResponsiveContainer>
                                    </div>
                                </div>

                                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
                                    <MetricCard title="Average Rating" value={<StarRating rating={avgRating}/>}
                                                extra={`(${totalRatings} ratings)`} color="yellow"/>
                                    <MetricCard title="Avg Resolution Time" value={formatSecs(avgResolutionTimeSecs)} color="purple"/>
                                    <MetricCard title="Longest Active Ticket" value={formatSecs(largestActiveTicketSecs)}
                                                color="purple"/>
                                    <MetricCard title="Stale Tickets" value={staleTicketsCount} color="orange"/>
                                </div>

                                {isAssignmentEnabled && assignmentsByDay.length > 0 && (
                                    <div className="bg-white shadow-sm rounded-lg p-4 border mt-4">
                                        <h2 className="font-semibold mb-2 text-gray-700 text-center">Average Ticket Assignments per Support Engineer</h2>
                                        <p className="text-sm text-gray-500 text-center mb-3">
                                            Average number of tickets assigned per engineer by day
                                        </p>
                                        <div style={{width: '100%', height: 300}}>
                                            <ResponsiveContainer width="100%" height="100%">
                                                <LineChart data={assignmentsByDay}>
                                                    <CartesianGrid strokeDasharray="3 3"/>
                                                    <XAxis 
                                                        dataKey="date" 
                                                        tickFormatter={(date: string) => new Date(date).toLocaleDateString()}
                                                    />
                                                    <YAxis 
                                                        label={{ value: 'Avg Tickets/Engineer', angle: -90, position: 'insideLeft' }}
                                                        allowDecimals={true}
                                                    />
                                                    <Tooltip 
                                                        labelFormatter={(label) => new Date(label as string).toLocaleDateString()}
                                                        formatter={(value: number, name: string) => {
                                                            if (name === 'avgAssignments') return [value.toFixed(2), 'Avg per Engineer']
                                                            if (name === 'totalAssignments') return [value, 'Total Assigned']
                                                            return [value, name]
                                                        }}
                                                    />
                                                    <Legend 
                                                        formatter={(value: string) => {
                                                            if (value === 'avgAssignments') return 'Avg per Engineer'
                                                            if (value === 'totalAssignments') return 'Total Assigned'
                                                            return value
                                                        }}
                                                    />
                                                    <Line 
                                                        type="monotone" 
                                                        dataKey="avgAssignments" 
                                                        stroke="#8b5cf6" 
                                                        strokeWidth={2}
                                                        activeDot={{r: 6}}
                                                    />
                                                    <Line 
                                                        type="monotone" 
                                                        dataKey="totalAssignments" 
                                                        stroke="#a78bfa" 
                                                        strokeWidth={2}
                                                        strokeDasharray="5 5"
                                                    />
                                                </LineChart>
                                            </ResponsiveContainer>
                                        </div>
                                    </div>
                                )}

                                {/* Tickets Opened by Requesting Team */}
                                {ticketsByTeam.length > 0 && (
                                    <div className="bg-white shadow-sm rounded-lg p-4 border mt-4">
                                        <h2 className="font-semibold mb-2 text-gray-700 text-center">Tickets Opened by Requesting Team (Top 10)</h2>
                                        <div style={{width: '100%', height: 300}}>
                                            <ResponsiveContainer width="100%" height="100%">
                                                <BarChart data={ticketsByTeam}>
                                                    <CartesianGrid strokeDasharray="3 3"/>
                                                    <XAxis 
                                                        dataKey="name" 
                                                        angle={-45}
                                                        textAnchor="end"
                                                        height={100}
                                                    />
                                                    <YAxis/>
                                                    <Tooltip/>
                                                    <Bar dataKey="value" fill="#0088FE">
                                                        {ticketsByTeam.map((_, idx) => (
                                                            <Cell key={idx} fill={COLORS[idx % COLORS.length]}/>
                                                        ))}
                                                    </Bar>
                                                </BarChart>
                                            </ResponsiveContainer>
                                        </div>
                                    </div>
                                )}

                                {/* Current Active Tickets per Engineer */}
                                {isAssignmentEnabled && activeTicketsPerEngineer.length > 0 && (
                                    <div className="bg-white shadow-sm rounded-lg p-4 border mt-4">
                                        <h2 className="font-semibold mb-2 text-gray-700 text-center">Current Active Tickets per Engineer</h2>
                                        <div style={{width: '100%', height: 300}}>
                                            <ResponsiveContainer width="100%" height="100%">
                                                <BarChart data={activeTicketsPerEngineer}>
                                                    <CartesianGrid strokeDasharray="3 3"/>
                                                    <XAxis 
                                                        dataKey="name" 
                                                        angle={-45}
                                                        textAnchor="end"
                                                        height={100}
                                                    />
                                                    <YAxis allowDecimals={false}/>
                                                    <Tooltip/>
                                                    <Bar dataKey="tickets" fill="#8b5cf6">
                                                        {activeTicketsPerEngineer.map((_, idx) => (
                                                            <Cell key={idx} fill={COLORS[idx % COLORS.length]}/>
                                                        ))}
                                                    </Bar>
                                                </BarChart>
                                            </ResponsiveContainer>
                                        </div>
                                    </div>
                                )}

                                {/* Tickets Opened by Hour of Day & Busiest Periods Heatmap - Side by Side */}
                                <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 mt-4">
                                    {/* Tickets Opened by Hour of Day */}
                                    {ticketsByHour.length > 0 && (
                                        <div className="bg-white shadow-sm rounded-lg p-4 border">
                                            <h2 className="font-semibold mb-2 text-gray-700 text-center">Tickets Opened by Hour of Day</h2>
                                            <p className="text-sm text-gray-500 text-center mb-3">
                                                Consolidation of all tickets by their opened time
                                            </p>
                                            <div style={{width: '100%', height: 300}}>
                                                <ResponsiveContainer width="100%" height="100%">
                                                    <BarChart data={ticketsByHour}>
                                                        <CartesianGrid strokeDasharray="3 3"/>
                                                        <XAxis 
                                                            dataKey="hourLabel"
                                                            angle={-45}
                                                            textAnchor="end"
                                                            height={80}
                                                        />
                                                        <YAxis allowDecimals={false}/>
                                                        <Tooltip/>
                                                        <Bar dataKey="count" fill="#10b981"/>
                                                    </BarChart>
                                                </ResponsiveContainer>
                                            </div>
                                        </div>
                                    )}

                                    {/* Busiest Periods Heatmap */}
                                    {busiestPeriods.length > 0 && (
                                        <div className="bg-white shadow-sm rounded-lg p-4 border">
                                            <h2 className="font-semibold mb-2 text-gray-700 text-center">Busiest Periods Heatmap</h2>
                                            <p className="text-xs text-gray-500 text-center mb-3">Weekdays (Mon-Fri)</p>
                                            <div className="overflow-x-auto">
                                                <table className="w-full text-xs">
                                                    <thead>
                                                        <tr>
                                                            <th className="text-left p-2 sticky left-0 bg-white z-10">Day</th>
                                                            {Array.from({ length: 13 }, (_, idx) => {
                                                                const hour = idx + 7 // Hours 7-19
                                                                return (
                                                                    <th key={hour} className="p-1 text-center min-w-[30px]">
                                                                        {String(hour).padStart(2, '0')}
                                                                    </th>
                                                                )
                                                            })}
                                                        </tr>
                                                    </thead>
                                                    <tbody>
                                                        {busiestPeriods.map((row, idx) => {
                                                            const hourValues = Array.from({ length: 13 }, (_, idx) => row[idx + 7] as number)
                                                            const maxValue = Math.max(...hourValues, 0)
                                                            return (
                                                                <tr key={idx} className="border-t">
                                                                    <td className="p-2 sticky left-0 bg-white font-medium">{row.day}</td>
                                                                    {Array.from({ length: 13 }, (_, idx) => {
                                                                        const hour = idx + 7 // Hours 7-19
                                                                        const value = row[hour] as number
                                                                        const intensity = maxValue > 0 ? (value / maxValue) * 100 : 0
                                                                        const bgColor = intensity > 70 
                                                                            ? 'bg-red-500' 
                                                                            : intensity > 40 
                                                                            ? 'bg-orange-400' 
                                                                            : intensity > 20 
                                                                            ? 'bg-yellow-300' 
                                                                            : 'bg-green-100'
                                                                        return (
                                                                            <td 
                                                                                key={hour} 
                                                                                className={`p-1 text-center ${bgColor} text-gray-800 font-medium`}
                                                                                title={`${row.day} ${String(hour).padStart(2, '0')}:00 - ${value} tickets`}
                                                                            >
                                                                                {value > 0 ? value : ''}
                                                                            </td>
                                                                        )
                                                                    })}
                                                                </tr>
                                                            )
                                                        })}
                                                    </tbody>
                                                </table>
                                            </div>
                                        </div>
                                    )}
                                </div>

                                {/* Capacity Planning - Combined Section */}
                                {isAssignmentEnabled && capacityVsDemand && (
                                    <div className="bg-white shadow-sm rounded-lg border mt-4 border-purple-200">
                                        {/* Capacity vs Demand - Always Visible */}
                                        <div className="p-4 border-b border-purple-200">
                                            <h2 className="font-semibold mb-4 text-gray-700 text-center">Capacity vs Demand</h2>
                                            <div className="space-y-4">
                                                <div className="flex justify-between items-center">
                                                    <span className="text-gray-600">Engineers on Rota:</span>
                                                    <div className="flex items-center gap-2">
                                                        <input
                                                            type="number"
                                                            min="1"
                                                            max={capacityVsDemand.totalEngineers || 10}
                                                            value={engineersOnRota}
                                                            onChange={(e) => {
                                                                const value = parseInt(e.target.value) || 1
                                                                setEngineersOnRota(Math.max(1, Math.min(value, capacityVsDemand.totalEngineers || 10)))
                                                            }}
                                                            className="w-16 px-2 py-1 border border-gray-300 rounded text-center font-semibold text-lg"
                                                        />
                                                        {capacityVsDemand.totalEngineers > 0 && (
                                                            <span className="text-xs text-gray-500">
                                                                (of {capacityVsDemand.totalEngineers} total)
                                                            </span>
                                                        )}
                                                    </div>
                                                </div>
                                                <div className="flex justify-between items-center">
                                                    <span className="text-gray-600">Tickets per Engineer Capacity:</span>
                                                    <div className="flex items-center gap-2">
                                                        <input
                                                            type="number"
                                                            min="1"
                                                            max="50"
                                                            value={ticketsPerEngineerCapacity}
                                                            onChange={(e) => {
                                                                const value = parseInt(e.target.value) || 1
                                                                setTicketsPerEngineerCapacity(Math.max(1, Math.min(value, 50)))
                                                            }}
                                                            className="w-16 px-2 py-1 border border-gray-300 rounded text-center font-semibold text-lg"
                                                        />
                                                    </div>
                                                </div>
                                                <div className="flex justify-between items-center border-t pt-2">
                                                    <span className="text-gray-600">Total Capacity:</span>
                                                    <span className="font-semibold text-lg">{capacityVsDemand.totalCapacity} tickets</span>
                                                </div>
                                                <div className="flex justify-between items-center">
                                                    <span className="text-gray-600">Open Tickets:</span>
                                                    <span className="font-semibold text-lg">{capacityVsDemand.openTickets}</span>
                                                </div>
                                                <div className="flex justify-between items-center">
                                                    <span className="text-gray-600">Tickets per Engineer:</span>
                                                    <span className="font-semibold text-lg">{capacityVsDemand.ticketsPerEngineer}</span>
                                                </div>
                                                <div className="mt-4">
                                                    <div className="flex justify-between items-center mb-2">
                                                        <span className="text-gray-600">Capacity Utilization:</span>
                                                        <span className={`font-semibold text-lg ${
                                                            capacityVsDemand.capacityUtilization > 100 ? 'text-red-700' :
                                                            capacityVsDemand.capacityUtilization > 80 ? 'text-red-600' :
                                                            capacityVsDemand.capacityUtilization > 60 ? 'text-orange-600' :
                                                            'text-green-600'
                                                        }`}>
                                                            {capacityVsDemand.capacityUtilization}%
                                                            {capacityVsDemand.capacityUtilization > 100 && (
                                                                <span className="ml-1 text-xs">⚠️ Over Capacity</span>
                                                            )}
                                                        </span>
                                                    </div>
                                                    <div className="w-full bg-gray-200 rounded-full h-4 relative overflow-hidden">
                                                        <div 
                                                            className={`h-4 rounded-full ${
                                                                capacityVsDemand.capacityUtilization > 100 ? 'bg-red-600' :
                                                                capacityVsDemand.capacityUtilization > 80 ? 'bg-red-500' :
                                                                capacityVsDemand.capacityUtilization > 60 ? 'bg-orange-400' :
                                                                'bg-green-500'
                                                            }`}
                                                            style={{ width: `${Math.min(100, capacityVsDemand.capacityUtilization)}%` }}
                                                        />
                                                        {capacityVsDemand.capacityUtilization > 100 && (
                                                            <div className="absolute inset-0 bg-red-600 opacity-50 animate-pulse" />
                                                        )}
                                                    </div>
                                                    {capacityVsDemand.capacityUtilization > 100 && (
                                                        <p className="text-xs text-red-600 mt-1 text-center">
                                                            Over capacity by {capacityVsDemand.capacityUtilization - 100}%
                                                        </p>
                                                    )}
                                                </div>
                                            </div>
                                        </div>

                                        {/* Capacity Insights by Time Block - Collapsible */}
                                        {capacityInsights.length > 0 && (
                                            <>
                                                <button
                                                    onClick={() => setCapacityInsightsExpanded(!capacityInsightsExpanded)}
                                                    className="w-full flex items-center justify-between p-3 hover:bg-purple-50 transition-colors"
                                                >
                                                    <div className="flex items-center gap-2">
                                                        <Headphones className="w-4 h-4 text-purple-600" />
                                                        <h3 className="font-semibold text-gray-900 text-sm">Capacity Insights by Time Block</h3>
                                                        <span className="text-xs text-gray-600 font-normal">
                                                            ({capacityInsightsExpanded ? 'Click to collapse' : 'Click to expand'})
                                                        </span>
                                                    </div>
                                                    <ChevronDown className={`w-4 h-4 text-purple-600 transition-transform duration-200 ${capacityInsightsExpanded ? 'rotate-180' : ''}`} />
                                                </button>
                                                
                                                {capacityInsightsExpanded && (
                                                    <div className="px-3 pb-3 border-t border-purple-200 pt-3">
                                                        <p className="text-xs text-gray-500 text-center mb-3">
                                                            Average tickets per weekday (2-hour blocks) | Capacity: {engineersOnRota} engineers × {ticketsPerEngineerCapacity} = {engineersOnRota * ticketsPerEngineerCapacity}
                                                        </p>
                                                        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3">
                                                            {capacityInsights.map((insight, idx) => {
                                                                const statusBg = insight.status === 'over'
                                                                    ? 'bg-red-50 border-red-200'
                                                                    : insight.status === 'near'
                                                                    ? 'bg-yellow-50 border-yellow-200'
                                                                    : 'bg-green-50 border-green-200'
                                                                const utilizationColor = insight.utilization > 100
                                                                    ? 'text-red-700 font-semibold'
                                                                    : insight.utilization > 80
                                                                    ? 'text-orange-600'
                                                                    : 'text-green-600'

                                                                return (
                                                                    <div key={idx} className={`border rounded-lg p-3 ${statusBg}`}>
                                                                        <div className="font-semibold text-sm mb-2 text-gray-900">
                                                                            {insight.timeBlock}
                                                                        </div>
                                                                        <div className="space-y-1 text-xs">
                                                                            <div className="flex justify-between">
                                                                                <span className="text-gray-600">Avg Tickets:</span>
                                                                                <span className="font-medium">{insight.avgTickets.toFixed(1)}</span>
                                                                            </div>
                                                                            <div className="flex justify-between">
                                                                                <span className="text-gray-600">Capacity:</span>
                                                                                <span>{insight.currentCapacity}</span>
                                                                            </div>
                                                                            <div className="flex justify-between">
                                                                                <span className="text-gray-600">Utilization:</span>
                                                                                <span className={`font-semibold ${utilizationColor}`}>
                                                                                    {insight.utilization}%
                                                                                </span>
                                                                            </div>
                                                                            <div className="flex justify-between pt-1 border-t border-gray-200">
                                                                                <span className="text-gray-600">Recommended:</span>
                                                                                <span className="font-semibold text-gray-900">
                                                                                    {insight.recommendedRange} engineers
                                                                                </span>
                                                                            </div>
                                                                        </div>
                                                                    </div>
                                                                )
                                                            })}
                                                        </div>
                                                        <div className="mt-3 pt-3 border-t">
                                                            <div className="flex flex-wrap gap-3 text-xs justify-center">
                                                                <div className="flex items-center gap-1">
                                                                    <div className="w-3 h-3 bg-red-50 border border-red-200 rounded"></div>
                                                                    <span>Over Capacity (&gt;100%)</span>
                                                                </div>
                                                                <div className="flex items-center gap-1">
                                                                    <div className="w-3 h-3 bg-yellow-50 border border-yellow-200 rounded"></div>
                                                                    <span>Near Capacity (80-100%)</span>
                                                                </div>
                                                                <div className="flex items-center gap-1">
                                                                    <div className="w-3 h-3 bg-green-50 border border-green-200 rounded"></div>
                                                                    <span>Under Capacity (&lt;80%)</span>
                                                                </div>
                                                            </div>
                                                        </div>
                                                    </div>
                                                )}
                                            </>
                                        )}
                                    </div>
                                )}
                            </>
                        )}

                        {activeTab === 'ratings' && (
                            <div className="space-y-6">
                                <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                            <MetricCard title="Consolidated Average Rating" value={<StarRating rating={avgRating}/>} extra={`(${totalRatings} ratings)`} color="yellow"/>
                                    <MetricCard title="Percentage Rated" value={`${percentageRated}%`} color="green"/>
                                    <MetricCard title="Ratings Count" value={totalRatings} color="blue"/>
                                </div>
                        <div className="space-y-6">
                            <div className="bg-white shadow-sm rounded-lg p-4 border">
                                <h2 className="text-xl font-semibold mb-2 text-gray-700 text-center">Ratings Received per Week</h2>
                                <div style={{width: '100%', height: 320}}>
                                    <ResponsiveContainer width="100%" height="100%">
                                        <BarChart data={ratingsByWeek}>
                                            <CartesianGrid strokeDasharray="3 3" />
                                            <XAxis dataKey="weekStart" tickFormatter={(d: string) => new Date(d).toLocaleDateString()} />
                                            <YAxis allowDecimals={false} />
                                            <Tooltip labelFormatter={(label) => new Date(label as string).toLocaleDateString()} />
                                            <Legend />
                                            <Bar dataKey="count" name="Ratings" fill="#10b981">
                                                {ratingsByWeek.map((_, idx) => (
                                                    <Cell key={idx} fill={COLORS[idx % COLORS.length]} />
                                                ))}
                                            </Bar>
                                        </BarChart>
                                    </ResponsiveContainer>
                                </div>
                            </div>

                            <div className="bg-white shadow-sm rounded-lg p-4 border">
                                <h2 className="text-xl font-semibold mb-2 text-gray-700 text-center">Average Rating per Week</h2>
                                <div style={{width: '100%', height: 320}}>
                                    <ResponsiveContainer width="100%" height="100%">
                                        <LineChart data={avgRatingsByWeek}>
                                            <CartesianGrid strokeDasharray="3 3" />
                                            <XAxis dataKey="weekStart" tickFormatter={(d: string) => new Date(d).toLocaleDateString()} />
                                            <YAxis domain={[0, 5]} tickCount={6} />
                                            <Tooltip labelFormatter={(label) => new Date(label as string).toLocaleDateString()} />
                                            <Legend />
                                            <Line type="monotone" dataKey="average" name="Average Rating" stroke="#f59e0b" dot />
                                        </LineChart>
                                    </ResponsiveContainer>
                                </div>
                            </div>
                        </div>
                            </div>
                        )}

                        {activeTab === 'workbench' && (
                            <>
                                {/* Bulk Reassign Section - Only show if assignment is enabled */}
                                {isAssignmentEnabled && (
                                    <div className="bg-gradient-to-r from-purple-50 to-blue-50 border border-purple-200 rounded-lg mb-4 shadow-sm overflow-hidden">
                                        {/* Collapsible Header */}
                                        <button
                                            onClick={() => setBulkReassignExpanded(!bulkReassignExpanded)}
                                            className="w-full p-4 flex items-center justify-between hover:bg-purple-100/50 transition-colors"
                                        >
                                            <div className="flex items-center gap-2">
                                                <Headphones className="w-5 h-5 text-purple-600" />
                                                <h3 className="font-bold text-gray-900">Bulk Reassign Tickets</h3>
                                                <span className="text-sm text-gray-600 font-normal">
                                                    {bulkReassignExpanded ? '(Click to collapse)' : '(Click to expand)'}
                                                </span>
                                            </div>
                                            <ChevronDown className={`w-5 h-5 text-purple-600 transition-transform duration-200 ${bulkReassignExpanded ? 'rotate-180' : ''}`} />
                                        </button>
                                        
                                        {/* Expandable Content */}
                                        {bulkReassignExpanded && (
                                            <div className="px-4 pb-4 border-t border-purple-200 pt-4">
                                                <div className="flex flex-wrap items-center gap-3">
                                            <div className="flex items-center gap-2">
                                                <label className="text-sm font-medium text-gray-700">From:</label>
                                                <select
                                                    value={bulkReassignFrom}
                                                    onChange={(e) => setBulkReassignFrom(e.target.value)}
                                                    className="p-2 border border-gray-300 rounded bg-white shadow-sm"
                                                >
                                                    <option value="">Select assignee...</option>
                                                    <option value="unassigned">Unassigned</option>
                                                    {supportMembers?.map((member: SupportMember) => (
                                                        <option key={member.userId} value={member.displayName}>
                                                            {member.displayName}
                                                        </option>
                                                    ))}
                                                </select>
                                            </div>
                                            
                                            <div className="flex items-center gap-2">
                                                <label className="text-sm font-medium text-gray-700">To:</label>
                                                <select
                                                    value={bulkReassignTo}
                                                    onChange={(e) => setBulkReassignTo(e.target.value)}
                                                    className="p-2 border border-gray-300 rounded bg-white shadow-sm"
                                                >
                                                    <option value="">Select assignee...</option>
                                                    {supportMembers?.map((member: SupportMember) => (
                                                        <option key={member.userId} value={member.displayName}>
                                                            {member.displayName}
                                                        </option>
                                                    ))}
                                                </select>
                                            </div>

                                            <button
                                                onClick={() => {
                                                    if (bulkReassignFrom && bulkReassignTo) {
                                                        if (bulkReassignFrom === bulkReassignTo) {
                                                            setReassignMessage({
                                                                type: 'error', 
                                                                text: 'Source and target assignee cannot be the same. Please select different assignees.'
                                                            })
                                                            setTimeout(() => setReassignMessage(null), 5000)
                                                            return
                                                        }
                                                        
                                                        const affectedTickets = filteredTickets.filter(t => 
                                                            t.status?.toLowerCase() === 'opened' && (
                                                                bulkReassignFrom === 'unassigned' 
                                                                    ? !t.assignedTo 
                                                                    : t.assignedTo === bulkReassignFrom
                                                            )
                                                        )
                                                        const count = affectedTickets.length
                                                        if (count === 0) {
                                                            setReassignMessage({type: 'error', text: 'No tickets found matching the selected assignee.'})
                                                            setTimeout(() => setReassignMessage(null), 5000)
                                                            return
                                                        }
                                                        
                                                        setConfirmationDetails({
                                                            from: bulkReassignFrom,
                                                            to: bulkReassignTo,
                                                            count: count,
                                                            tickets: affectedTickets
                                                        })
                                                        setShowConfirmation(true)
                                                        setReassignMessage(null)
                                                    }
                                                }}
                                                disabled={!bulkReassignFrom || !bulkReassignTo || isReassigning || showConfirmation}
                                                className="px-4 py-2 bg-purple-600 text-white rounded hover:bg-purple-700 disabled:bg-gray-300 disabled:cursor-not-allowed transition-colors font-medium shadow-sm"
                                            >
                                                Reassign All
                                            </button>
                                            
                                            {bulkReassignFrom && (
                                                <span className="text-sm font-medium text-purple-700 bg-purple-100 border border-purple-200 rounded px-3 py-1">
                                                    {filteredTickets.filter(t => 
                                                        t.status?.toLowerCase() === 'opened' && (
                                                            bulkReassignFrom === 'unassigned' 
                                                                ? !t.assignedTo 
                                                                : t.assignedTo === bulkReassignFrom
                                                        )
                                                    ).length} ticket{filteredTickets.filter(t => 
                                                        t.status?.toLowerCase() === 'opened' && (
                                                            bulkReassignFrom === 'unassigned' 
                                                                ? !t.assignedTo 
                                                                : t.assignedTo === bulkReassignFrom
                                                        )
                                                    ).length === 1 ? '' : 's'} (open)
                                                </span>
                                            )}
                                        </div>
                                        
                                        <p className="text-xs text-gray-600 mt-2">
                                            ℹ️ Only <strong>open tickets</strong> will be reassigned
                                        </p>
                                        
                                        {/* Success/Error Messages - Inside bulk reassign section */}
                                        {reassignMessage && (
                                            <div className={`mt-3 border rounded-md p-3 text-sm ${
                                                reassignMessage.type === 'success' 
                                                    ? 'bg-green-50 border-green-300 text-green-800' 
                                                    : 'bg-red-50 border-red-300 text-red-800'
                                            }`}>
                                                <p className="font-medium">{reassignMessage.text}</p>
                                            </div>
                                        )}

                                        {/* Inline Confirmation - Inside bulk reassign section */}
                                        {showConfirmation && confirmationDetails && (
                                            <div className="mt-3 bg-yellow-50 border border-yellow-300 rounded-md p-3">
                                                <div className="flex items-start gap-2">
                                                    <AlertTriangle className="w-4 h-4 text-yellow-600 flex-shrink-0 mt-0.5" />
                                                    <div className="flex-1">
                                                        <h4 className="font-semibold text-gray-900 text-sm mb-1">Confirm Bulk Reassignment</h4>
                                                        <p className="text-gray-700 text-xs mb-2">
                                                            Hand over <strong>{confirmationDetails.count} ticket{confirmationDetails.count === 1 ? '' : 's'}</strong> from{' '}
                                                            <strong>{confirmationDetails.from === 'unassigned' ? 'Unassigned' : confirmationDetails.from}</strong> to{' '}
                                                            <strong>{confirmationDetails.to}</strong>
                                                        </p>
                                                        <div className="flex gap-2">
                                                            <button
                                                                onClick={async () => {
                                                                    setShowConfirmation(false)
                                                                    setIsReassigning(true)
                                                                    setReassignMessage(null)
                                                                    
                                                                    try {
                                                                        const ticketIds = confirmationDetails.tickets.map(t => t.id)
                                                                        const targetUserId = supportMembers?.find(m => m.displayName === confirmationDetails.to)?.userId || ''
                                                                        
                                                                        if (!targetUserId) {
                                                                            throw new Error('Could not find user ID for selected assignee')
                                                                        }
                                                                        
                                                                        const request: BulkReassignRequest = {
                                                                            ticketIds,
                                                                            assignedTo: targetUserId
                                                                        }
                                                                        
                                                                        const response = await fetch('/api/assignment/bulk-reassign', {
                                                                            method: 'POST',
                                                                            headers: {
                                                                                'Content-Type': 'application/json',
                                                                            },
                                                                            body: JSON.stringify(request),
                                                                        })

                                                                        if (!response.ok) {
                                                                            throw new Error(`Failed to bulk reassign: ${response.status}`)
                                                                        }

                                                                        const result: BulkReassignResult = await response.json()
                                                                        
                                                                        setReassignMessage({
                                                                            type: 'success', 
                                                                            text: `${result.message} (${result.successCount} ticket${result.successCount === 1 ? '' : 's'})`
                                                                        })
                                                                        
                                                                        setBulkReassignFrom('')
                                                                        setBulkReassignTo('')
                                                                        setConfirmationDetails(null)
                                                                        
                                                                        await queryClient.invalidateQueries({ queryKey: ['tickets'] })
                                                                        
                                                                    } catch (error) {
                                                                        console.error('Bulk reassign failed:', error)
                                                                        setReassignMessage({
                                                                            type: 'error', 
                                                                            text: `Failed to reassign tickets: ${error instanceof Error ? error.message : 'Unknown error'}`
                                                                        })
                                                                    } finally {
                                                                        setIsReassigning(false)
                                                                        setTimeout(() => setReassignMessage(null), 10000)
                                                                    }
                                                                }}
                                                                disabled={isReassigning}
                                                                className="px-3 py-1.5 text-xs bg-yellow-600 text-white rounded hover:bg-yellow-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors font-medium flex items-center gap-1.5"
                                                            >
                                                                {isReassigning ? (
                                                                    <>
                                                                        <svg className="animate-spin h-3 w-3" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                                                                            <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                                                                            <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                                                                        </svg>
                                                                        Reassigning...
                                                                    </>
                                                                ) : (
                                                                    'Yes, Reassign'
                                                                )}
                                                            </button>
                                                            <button
                                                                onClick={() => {
                                                                    setShowConfirmation(false)
                                                                    setConfirmationDetails(null)
                                                                }}
                                                                disabled={isReassigning}
                                                                className="px-3 py-1.5 text-xs bg-gray-200 text-gray-700 rounded hover:bg-gray-300 disabled:opacity-50 disabled:cursor-not-allowed transition-colors font-medium"
                                                            >
                                                                Cancel
                                                            </button>
                                                        </div>
                                                    </div>
                                                </div>
                                            </div>
                                        )}
                                    </div>
                                        )}
                                    </div>
                                )}

                                {/* Filters */}
                                <div className="flex flex-wrap items-center gap-2 mb-4">
                                    <select
                                        value={inquiringTeamFilter}
                                        onChange={(e) => {
                                            setInquiringTeamFilter(e.target.value);
                                            setCurrentPage(1)
                                        }}
                                        className="p-2 border border-gray-300 rounded shadow-sm"
                                    >
                                        <option value="">Requesting Team</option>
                                        {inquiringTeamsWithTickets.map(team => <option key={team} value={team}>{team}</option>)}
                                    </select>

                                    <select
                                        value={escalatedTeamFilter}
                                        onChange={(e) => {
                                            setEscalatedTeamFilter(e.target.value);
                                            setCurrentPage(1)
                                        }}
                                        className="p-2 border border-gray-300 rounded shadow-sm"
                                    >
                                        <option value="">Escalated To</option>
                                        {escalatedTeamsWithTickets.map(team => <option key={team} value={team}>{team}</option>)}
                                    </select>

                                    <select
                                        value={statusFilter}
                                        onChange={(e) => {
                                            setStatusFilter(e.target.value);
                                            setCurrentPage(1)
                                        }}
                                        className="p-2 border border-gray-300 rounded shadow-sm"
                                    >
                                        <option value="">Status</option>
                                        <option value="opened">Opened</option>
                                        <option value="closed">Closed</option>
                                        <option value="stale">Stale</option>
                                    </select>

                                    {isAssignmentEnabled && (
                                        <select
                                            value={assigneeFilter}
                                            onChange={(e) => {
                                                setAssigneeFilter(e.target.value);
                                                setCurrentPage(1)
                                            }}
                                            className="p-2 border border-gray-300 rounded shadow-sm"
                                        >
                                            <option value="">Assignee</option>
                                            <option value="unassigned">Unassigned</option>
                                            {supportMembers?.map((member: SupportMember) => (
                                                <option key={member.userId} value={member.displayName}>
                                                    {member.displayName}
                                                </option>
                                            ))}
                                        </select>
                                    )}

                                    <select
                                        value={ratedFilter}
                                        onChange={(e) => {
                                            setRatedFilter(e.target.value);
                                            setCurrentPage(1)
                                        }}
                                        className="p-2 border border-gray-300 rounded shadow-sm"
                                    >
                                        <option value="">Rated</option>
                                        <option value="yes">Yes</option>
                                        <option value="no">No</option>
                                    </select>
                                </div>

                                {/* Tickets Table */}
                                <div className="overflow-x-auto border rounded-lg shadow-sm mb-6">
                                    <table className="min-w-full divide-y divide-gray-200">
                                        <thead className="bg-gradient-to-r from-gray-50 to-gray-100">
                                        <tr>
                                            <th className="px-4 py-3 text-left text-xs font-bold text-gray-700 uppercase tracking-wider">Team</th>
                                            <th className="px-4 py-3 text-left text-xs font-bold text-gray-700 uppercase tracking-wider">Impact</th>
                                            {isAssignmentEnabled && (
                                                <th className="px-4 py-3 text-left text-xs font-bold text-gray-700 uppercase tracking-wider">Support Engineer</th>
                                            )}
                                            <th className="px-4 py-3 text-left text-xs font-bold text-gray-700 uppercase tracking-wider">Escalated To</th>
                                            <th className="px-4 py-3 text-left text-xs font-bold text-gray-700 uppercase tracking-wider">Status</th>
                                            <th className="px-4 py-3 text-left text-xs font-bold text-gray-700 uppercase tracking-wider">Opened At</th>
                                            <th className="px-4 py-3 text-left text-xs font-bold text-gray-700 uppercase tracking-wider">Rated</th>
                                            <th className="px-4 py-3 text-left text-xs font-bold text-gray-700 uppercase tracking-wider">Link</th>
                                        </tr>
                                        </thead>
                                        <tbody className="bg-white divide-y divide-gray-200">
                                        {paginatedTickets.map((t) => {
                                            const {opened} = getOpenedClosed(t)
                                            return (
                                                <tr key={t.id} className="hover:bg-blue-50 transition-colors">
                                                    <td className="px-4 py-3 text-sm text-gray-900">{t.team?.name || '-'}</td>
                                                    <td className="px-4 py-3 text-sm">
                                                        <span className="text-gray-700">
                                                            {registryData?.impacts.find((i: TicketImpact) => i.code === t.impact)?.label || t.impact || '-'}
                                                        </span>
                                                    </td>
                                                    {isAssignmentEnabled && (
                                                        <td className="px-4 py-3 text-sm">
                                                            <span className={`px-2 py-1 rounded-full text-xs font-medium ${
                                                                t.assignedTo 
                                                                    ? 'bg-green-100 text-green-800' 
                                                                    : 'bg-gray-100 text-gray-600'
                                                            }`}>
                                                                {t.assignedTo || 'Unassigned'}
                                                            </span>
                                                        </td>
                                                    )}
                                                    <td className="px-4 py-3 text-sm text-gray-700">
                                                        {(t.escalations || []).length ? (t.escalations || []).map((e: Escalation) => e.team?.name).filter(Boolean).join(', ') : 'None'}
                                                    </td>
                                                    <td className="px-4 py-3 text-sm">
                                                        <span className={`px-2 py-1 rounded-full text-xs font-semibold ${statusColors[t.status?.toLowerCase()] || 'bg-gray-100 text-gray-800'}`}>
                                                            {t.status}
                                                        </span>
                                                    </td>
                                                    <td className="px-4 py-3 text-sm text-gray-600">
                                                        {opened ? opened.toLocaleDateString() : '-'}
                                                    </td>
                                                    <td className="px-4 py-3 text-sm text-gray-700">
                                                        {t.ratingSubmitted ? (
                                                            <span className="text-green-600 font-medium">✓ Yes</span>
                                                        ) : (
                                                            <span className="text-gray-400">No</span>
                                                        )}
                                                    </td>
                                                    <td className="px-4 py-3 text-sm">
                                                        <a 
                                                            href={t.query?.link} 
                                                            target="_blank" 
                                                            rel="noopener noreferrer"
                                                            className="text-blue-600 hover:text-blue-800 hover:underline font-medium"
                                                        >
                                                            View
                                                        </a>
                                                    </td>
                                                </tr>
                                            )
                                        })}
                                        {paginatedTickets.length === 0 && (
                                            <tr>
                                                <td colSpan={isAssignmentEnabled ? 8 : 7} className="text-center py-8 text-gray-500">
                                                    <div className="flex flex-col items-center gap-2">
                                                        <AlertTriangle className="w-8 h-8 text-gray-400" />
                                                        <p className="font-medium">No tickets found</p>
                                                        <p className="text-sm">Try adjusting your filters</p>
                                                    </div>
                                                </td>
                                            </tr>
                                        )}
                                        </tbody>
                                    </table>

                                    <div className="flex justify-center space-x-2 p-2 bg-white">
                                        {Array.from({length: Math.max(1, Math.ceil(filteredTickets.length / ticketsPerPage))}, (_, i) => (
                                            <button
                                                key={i}
                                                onClick={() => setCurrentPage(i + 1)}
                                                className={`px-3 py-1 rounded ${currentPage === i + 1 ? 'bg-blue-500 text-white' : 'bg-gray-200 text-gray-700'}`}
                                            >
                                                {i + 1}
                                            </button>
                                        ))}
                                    </div>
                                </div>
                            </>
                        )}
                    </div>
                </div>
            </div>
        </div>
    )

    function formatSecs(secs: number) {
        if (!secs || secs <= 0) return '-'
        if (secs < 60) return `${Math.round(secs)}s`
        const m = Math.floor(secs / 60)
        const s = Math.floor(secs % 60)
        if (m < 60) return `${m}m ${s}s`
        const h = Math.floor(m / 60)
        const mm = m % 60
        return `${h}h ${mm}m`
    }
}

// --- MetricCard & StarRating components ---
export function MetricCard({title, value, extra, color}: {
    title: string,
    value: React.ReactNode,
    extra?: string,
    color: string
}) {
    const colorClasses: Record<string, string> = {
        blue: 'bg-blue-50 text-blue-700 border-blue-200',
        green: 'bg-green-50 text-green-700 border-green-200',
        purple: 'bg-purple-50 text-purple-700 border-purple-200',
        yellow: 'bg-yellow-50 text-yellow-700 border-yellow-200',
    }
    return (
        <div className={`p-4 rounded-lg border shadow-sm ${colorClasses[color]}`}>
            <h2 className="font-semibold mb-2">{title}</h2>
            <div className="text-lg">{value}</div>
            {extra && <p className="text-sm text-gray-500">{extra}</p>}
        </div>
    )
}

function StarRating({rating}: { rating: number }) {
    const fullStars = Math.floor(rating)
    const halfStar = rating % 1 >= 0.5
    return (
        <div className="flex items-center">
            {Array.from({length: fullStars}, (_, i) => (
                <span key={i} className="text-yellow-500">★</span>
            ))}
            {halfStar && <span className="text-yellow-500">☆</span>}
            <span className="ml-2 text-sm text-gray-600">{rating.toFixed(1)}</span>
        </div>
    )
}
