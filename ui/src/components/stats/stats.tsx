'use client'

import { useMemo, useState } from 'react'
import { useAllTickets, useRegistry } from '@/lib/hooks'
import { PieChart, Pie, Cell, Tooltip, Legend, ResponsiveContainer, PieLabelRenderProps } from 'recharts'
import { useTeamFilter } from '@/contexts/TeamFilterContext'
import {TicketImpact} from "@/lib/types";
import EscalatedToMyTeamWidget from '@/components/escalations/EscalatedToMyTeamWidget'
import { useUser } from '@/contexts/AuthContext'
import LoadingSkeleton from '@/components/LoadingSkeleton'

export default function StatsPage() {
    // Date filter state - default to last week to match other views
    type DateFilter = 'lastWeek' | 'last2Weeks' | 'lastMonth' | 'lastYear' | 'custom' | 'all'
    const [dateFilter, setDateFilter] = useState<DateFilter>('lastWeek')
    const [customDateRange, setCustomDateRange] = useState<{ start?: string; end?: string }>({})

    // Calculate date range based on filter
    // When switching to "custom", preserve the current range until custom dates are set
    const dateRange = useMemo(() => {
        if (dateFilter === 'all') {
            return { from: undefined, to: undefined }
        }
        
        if (dateFilter === 'custom') {
            // If custom dates are not set yet, preserve the previous filter's range
            if (!customDateRange.start || !customDateRange.end) {
                // Calculate the current range based on last week (default)
                const now = new Date()
                const to = now.toISOString().split('T')[0]
                const fromDate = new Date(now)
                fromDate.setDate(now.getDate() - 7)
                const from = fromDate.toISOString().split('T')[0]
                return { from, to }
            }
            return {
                from: customDateRange.start,
                to: customDateRange.end
            }
        }
        
        const now = new Date()
        const to = now.toISOString().split('T')[0]
        const fromDate = new Date(now)
        
        switch (dateFilter) {
            case 'lastWeek':
                fromDate.setDate(now.getDate() - 7)
                break
            case 'last2Weeks':
                fromDate.setDate(now.getDate() - 14)
                break
            case 'lastMonth':
                fromDate.setMonth(now.getMonth() - 1)
                break
            case 'lastYear':
                fromDate.setFullYear(now.getFullYear() - 1)
                break
        }
        
        const from = fromDate.toISOString().split('T')[0]
        return { from, to }
    }, [dateFilter, customDateRange])
    
    // Use useAllTickets to fetch all tickets within date range (not just first 1000)
    // This ensures we get complete data for accurate statistics
    const { data: ticketsData, isLoading: isTicketsLoading, error: ticketsError } = useAllTickets(200, dateRange.from, dateRange.to)
    const { data: registryData } = useRegistry()
    const { hasFullAccess, effectiveTeams, selectedTeam } = useTeamFilter()
    const { actualEscalationTeams } = useUser()

    // Check if viewing as escalation team
    const isViewingAsEscalationTeam = useMemo(() => {
        if (!selectedTeam || actualEscalationTeams.length === 0) return false
        return actualEscalationTeams.includes(selectedTeam)
    }, [selectedTeam, actualEscalationTeams])

    // Filter tickets by team unless superuser
    const teamTickets = useMemo(() => {
        if (!ticketsData?.content) return []
        
        // Full access (leadership/support viewing all) -> show all tickets
        if (hasFullAccess) return ticketsData.content
        
        // No teams and no full access -> no tickets
        if (effectiveTeams.length === 0) return []
        
        // Filter by specific teams
        return ticketsData.content.filter(t => t.team?.name && effectiveTeams.includes(t.team.name))
    }, [ticketsData, hasFullAccess, effectiveTeams])

    // Compute stats
    const totalTickets = teamTickets.length
    const openTickets = teamTickets.filter(t => t.status === 'opened').length
    const resolvedTickets = teamTickets.filter(t => t.status === 'closed').length
    const escalatedTickets = teamTickets.filter(t => (t.escalations?.length ?? 0) > 0).length

    // Tickets by Status
    const ticketsByStatus = useMemo(() => {
        const counts: Record<string, number> = {}
        teamTickets.forEach(t => {
            const status = t.status || 'unknown'
            counts[status] = (counts[status] || 0) + 1
        })
        return Object.entries(counts).map(([name, value]) => ({ name, value }))
    }, [teamTickets])

    // Tickets by Impact
    const ticketsByImpact = useMemo(() => {
        const counts: Record<string, number> = {}
        teamTickets.forEach(t => {
            const impactLabel =
                registryData?.impacts.find((i: TicketImpact) => i.code === t.impact)?.label ||
                t.impact ||
                'Not yet tagged'
            counts[impactLabel] = (counts[impactLabel] || 0) + 1
        })
        return Object.entries(counts).map(([name, value]) => ({ name, value }))
    }, [teamTickets, registryData])

    const COLORS = ['#0088FE', '#00C49F', '#FFBB28', '#FF8042', '#A28EFF']
const RADIAN = Math.PI / 180
const renderPieLabel = ({ cx, cy, midAngle, innerRadius, outerRadius, percent }: PieLabelRenderProps) => {
    if (
        cx == null ||
        cy == null ||
        midAngle == null ||
        innerRadius == null ||
        outerRadius == null ||
        percent == null
    ) {
        return null
    }
    const cxNum = Number(cx)
    const cyNum = Number(cy)
    const angle = Number(midAngle)
    const pct = Number(percent)
    if (Number.isNaN(pct)) return null
    const ir = Number(innerRadius)
    const or = Number(outerRadius)
    const radius = ir + (or - ir) * 0.55
    const x = cxNum + radius * Math.cos(-angle * RADIAN)
    const y = cyNum + radius * Math.sin(-angle * RADIAN)
    return (
        <text
            x={x}
            y={y}
            fill="#111827"
            textAnchor="middle"
            dominantBaseline="central"
            fontSize={12}
        >
            {`${Math.round(pct * 100)}%`}
        </text>
    )
}

    if (isTicketsLoading) return <LoadingSkeleton />
    if (ticketsError) return (
        <div className="p-6">
            <div className="bg-red-50 border border-red-200 rounded-lg p-4 text-red-800">
                <p className="font-semibold">Error loading dashboard</p>
                <p className="text-sm mt-1">Unable to load dashboard data. Please try refreshing the page.</p>
            </div>
        </div>
    )

    // Render split view for escalation teams
    if (isViewingAsEscalationTeam) {
        return (
            <div className="p-6 space-y-8">
                <div className="flex items-center justify-between">
                    <h1 className="text-3xl font-bold text-gray-800">
                        {`Home Dashboard - ${selectedTeam}`}
                    </h1>
                    {/* Date Filter */}
                    <div className="flex items-center gap-2">
                        <select 
                            value={dateFilter} 
                            onChange={e => setDateFilter(e.target.value as DateFilter)} 
                            className="p-2 border rounded text-sm"
                        >
                            <option value="lastWeek">Last Week</option>
                            <option value="last2Weeks">Last 2 Weeks</option>
                            <option value="lastMonth">Last Month</option>
                            <option value="lastYear">Last Year</option>
                            <option value="custom">Custom Range</option>
                            <option value="all">All Time</option>
                        </select>
                        {dateFilter === 'custom' && (
                            <>
                                <input 
                                    type="date" 
                                    value={customDateRange.start || ''}
                                    onChange={e => setCustomDateRange({...customDateRange, start: e.target.value})}
                                    className="p-2 border rounded text-sm"
                                />
                                <span className="text-gray-500">to</span>
                                <input 
                                    type="date" 
                                    value={customDateRange.end || ''}
                                    onChange={e => setCustomDateRange({...customDateRange, end: e.target.value})}
                                    className="p-2 border rounded text-sm"
                                />
                            </>
                        )}
                    </div>
                </div>

                {/* Section 1: Escalations We Are Handling */}
                <div className="border-2 border-purple-300 rounded-lg bg-gradient-to-br from-purple-50 to-indigo-50 p-6 shadow-lg">
                    <h2 className="text-xl font-bold text-purple-900 mb-6">Escalations We Are Handling</h2>
                    <EscalatedToMyTeamWidget />
                </div>

                {/* Section 2: Tickets We Own */}
                <div className="border-2 border-slate-300 rounded-lg bg-gradient-to-br from-slate-50 to-gray-50 p-6 shadow-lg">
                    <h2 className="text-xl font-bold text-slate-900 mb-6">Tickets We Own</h2>
                    
                    {/* Summary cards */}
                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-6">
                        <div className="p-6 bg-white shadow-md rounded-lg border border-gray-200">
                            <h3 className="font-semibold text-gray-700">Total Tickets</h3>
                            <p className="text-2xl font-bold text-blue-600 mt-2">{totalTickets}</p>
                        </div>
                        <div className="p-6 bg-white shadow-md rounded-lg border border-gray-200">
                            <h3 className="font-semibold text-gray-700">Open Tickets</h3>
                            <p className="text-2xl font-bold text-yellow-600 mt-2">{openTickets}</p>
                        </div>
                        <div className="p-6 bg-white shadow-md rounded-lg border border-gray-200">
                            <h3 className="font-semibold text-gray-700">Escalated Tickets</h3>
                            <p className="text-2xl font-bold text-red-600 mt-2">{escalatedTickets}</p>
                        </div>
                        <div className="p-6 bg-white shadow-md rounded-lg border border-gray-200">
                            <h3 className="font-semibold text-gray-700">Resolved Tickets</h3>
                            <p className="text-2xl font-bold text-green-600 mt-2">{resolvedTickets}</p>
                        </div>
                    </div>

                    {/* Charts */}
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                        <div className="bg-white shadow-md rounded-lg p-4 border">
                            <h3 className="font-semibold mb-2 text-gray-700">Tickets by Status</h3>
                            <ResponsiveContainer width="100%" height={280}>
                                <PieChart>
                                    <Pie
                                        data={ticketsByStatus}
                                        dataKey="value"
                                        nameKey="name"
                                        cx="50%"
                                        cy="50%"
                                        innerRadius={40}
                                        outerRadius={75}
                                        fill="#8884d8"
                                        labelLine={false}
                                        label={renderPieLabel}
                                        paddingAngle={2}
                                    >
                                        {ticketsByStatus.map((entry, index) => (
                                            <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                                        ))}
                                    </Pie>
                                    <Tooltip />
                                    <Legend verticalAlign="bottom" height={60} />
                                </PieChart>
                            </ResponsiveContainer>
                        </div>

                        <div className="bg-white shadow-md rounded-lg p-4 border">
                            <h3 className="font-semibold mb-2 text-gray-700">Tickets by Impact</h3>
                            <ResponsiveContainer width="100%" height={280}>
                                <PieChart>
                                    <Pie
                                        data={ticketsByImpact}
                                        dataKey="value"
                                        nameKey="name"
                                        cx="50%"
                                        cy="50%"
                                        innerRadius={40}
                                        outerRadius={75}
                                        fill="#82ca9d"
                                        labelLine={false}
                                        label={renderPieLabel}
                                        paddingAngle={2}
                                    >
                                        {ticketsByImpact.map((entry, index) => (
                                            <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                                        ))}
                                    </Pie>
                                    <Tooltip />
                                    <Legend verticalAlign="bottom" height={60} />
                                </PieChart>
                            </ResponsiveContainer>
                        </div>
                    </div>
                </div>
            </div>
        )
    }

    // Default view for non-escalation teams
    return (
        <div className="p-6 space-y-6 relative">
            <div className="flex items-center justify-between">
                <h1 className="text-3xl font-bold text-gray-800">
                    {hasFullAccess
                        ? 'Home Dashboard - All Teams'
                        : effectiveTeams.length > 0
                            ? `Home Dashboard - ${effectiveTeams.join(', ')}`
                            : 'Home Dashboard'}
                </h1>
                {/* Date Filter */}
                <div className="flex items-center gap-2">
                    <select 
                        value={dateFilter} 
                        onChange={e => setDateFilter(e.target.value as DateFilter)} 
                        className="p-2 border rounded text-sm"
                    >
                        <option value="lastWeek">Last Week</option>
                        <option value="last2Weeks">Last 2 Weeks</option>
                        <option value="lastMonth">Last Month</option>
                        <option value="lastYear">Last Year</option>
                        <option value="custom">Custom Range</option>
                        <option value="all">All Time</option>
                    </select>
                    {dateFilter === 'custom' && (
                        <>
                            <input 
                                type="date" 
                                value={customDateRange.start || ''}
                                onChange={e => setCustomDateRange({...customDateRange, start: e.target.value})}
                                className="p-2 border rounded text-sm"
                            />
                            <span className="text-gray-500">to</span>
                            <input 
                                type="date" 
                                value={customDateRange.end || ''}
                                onChange={e => setCustomDateRange({...customDateRange, end: e.target.value})}
                                className="p-2 border rounded text-sm"
                            />
                        </>
                    )}
                </div>
            </div>

            {/* Summary cards */}
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
                <div className="p-6 bg-white shadow-md rounded-lg border border-gray-200">
                    <h2 className="font-semibold text-gray-700">Total Tickets</h2>
                    <p className="text-2xl font-bold text-blue-600 mt-2">{totalTickets}</p>
                </div>
                <div className="p-6 bg-white shadow-md rounded-lg border border-gray-200">
                    <h2 className="font-semibold text-gray-700">Open Tickets</h2>
                    <p className="text-2xl font-bold text-yellow-600 mt-2">{openTickets}</p>
                </div>
                <div className="p-6 bg-white shadow-md rounded-lg border border-gray-200">
                    <h2 className="font-semibold text-gray-700">Escalated Tickets</h2>
                    <p className="text-2xl font-bold text-amber-500 mt-2">{escalatedTickets}</p>
                </div>
                <div className="p-6 bg-white shadow-md rounded-lg border border-gray-200">
                    <h2 className="font-semibold text-gray-700">Resolved Tickets</h2>
                    <p className="text-2xl font-bold text-green-600 mt-2">{resolvedTickets}</p>
                </div>
            </div>

            {/* Charts */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                <div className="bg-white shadow-md rounded-lg p-4 border">
                    <h2 className="font-semibold mb-2 text-gray-700">Tickets by Status</h2>
                    <ResponsiveContainer width="100%" height={280}>
                        <PieChart>
                            <Pie
                                data={ticketsByStatus}
                                dataKey="value"
                                nameKey="name"
                                cx="50%"
                                cy="50%"
                                innerRadius={40}
                                outerRadius={75}
                                fill="#8884d8"
                                labelLine={false}
                                label={renderPieLabel}
                                paddingAngle={2}
                            >
                                {ticketsByStatus.map((entry, index) => (
                                    <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                                ))}
                            </Pie>
                            <Tooltip />
                            <Legend verticalAlign="bottom" height={60} />
                        </PieChart>
                    </ResponsiveContainer>
                </div>

                <div className="bg-white shadow-md rounded-lg p-4 border">
                    <h2 className="font-semibold mb-2 text-gray-700">Tickets by Impact</h2>
                    <ResponsiveContainer width="100%" height={280}>
                        <PieChart>
                            <Pie
                                data={ticketsByImpact}
                                dataKey="value"
                                nameKey="name"
                                cx="50%"
                                cy="50%"
                                innerRadius={40}
                                outerRadius={75}
                                fill="#82ca9d"
                                labelLine={false}
                                label={renderPieLabel}
                                paddingAngle={2}
                            >
                                {ticketsByImpact.map((entry, index) => (
                                    <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                                ))}
                            </Pie>
                            <Tooltip />
                            <Legend verticalAlign="bottom" height={60} />
                        </PieChart>
                    </ResponsiveContainer>
                </div>
            </div>
        </div>
    )
}
