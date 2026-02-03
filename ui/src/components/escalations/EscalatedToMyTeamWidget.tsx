'use client'

import { useMemo, useCallback } from 'react'
import { useEscalations, useRegistry } from '@/lib/hooks'
import { useUser } from '@/contexts/AuthContext'
import { useTeamFilter } from '@/contexts/TeamFilterContext'
import { PieChart, Pie, Cell, Tooltip, Legend, ResponsiveContainer } from 'recharts'

export default function EscalatedToMyTeamWidget() {
    const { isEscalationTeam, actualEscalationTeams } = useUser()
    const { selectedTeam } = useTeamFilter()
    const { data: escalationsData, isLoading, error: escalationsError } = useEscalations()
    const { data: registryData } = useRegistry()

    // Only show when viewing one of the user's actual escalation teams
    const isViewingEscalationsOnly = useMemo(() => {
        if (!selectedTeam || actualEscalationTeams.length === 0) return false
        return actualEscalationTeams.includes(selectedTeam)
    }, [selectedTeam, actualEscalationTeams])

    // Count escalations escalated TO the currently selected escalation team
    // "Escalated TO my team" means escalations that were escalated TO your team (not tickets you own)
    const escalatedToMyTeam = useMemo(() => {
        if (!escalationsData?.content || !selectedTeam) return { total: 0, active: 0, resolved: 0 }
        
        // Filter escalations where the target team (escalated TO) matches the selected escalation team
        // Use case-insensitive comparison to handle format differences
        const toMyTeam = escalationsData.content.filter(esc => {
            if (!esc.team?.name) return false
            return esc.team.name.trim().toLowerCase() === selectedTeam.trim().toLowerCase()
        })
        
        return {
            total: toMyTeam.length,
            active: toMyTeam.filter(esc => !esc.resolvedAt).length,
            resolved: toMyTeam.filter(esc => esc.resolvedAt).length
        }
    }, [escalationsData, selectedTeam])

    // Get filtered escalations for this team
    // "Escalated TO my team" means escalations that were escalated TO your team (not tickets you own)
    const myTeamEscalations = useMemo(() => {
        if (!escalationsData?.content || !selectedTeam) return []
        return escalationsData.content.filter(esc => {
            if (!esc.team?.name) return false
            return esc.team.name.trim().toLowerCase() === selectedTeam.trim().toLowerCase()
        })
    }, [escalationsData, selectedTeam])

    // Escalations by Status
    const escalationsByStatus = useMemo(() => {
        const counts: Record<string, number> = {
            'Active': 0,
            'Resolved': 0
        }
        myTeamEscalations.forEach(esc => {
            if (esc.resolvedAt) {
                counts['Resolved']++
            } else {
                counts['Active']++
            }
        })
        return Object.entries(counts)
            .filter(([, value]) => value > 0)
            .map(([name, value]) => ({ name, value }))
    }, [myTeamEscalations])

    const impactLabel = useCallback((code?: string | null) => {
        if (!code) return 'Not specified'
        const match = registryData?.impacts?.find((i: { code: string; label: string }) => i.code === code)
        return match?.label || code
    }, [registryData])

    // Escalations by Impact
    const escalationsByImpact = useMemo(() => {
        const counts: Record<string, number> = {}
        myTeamEscalations.forEach(esc => {
            const label = impactLabel(esc.impact || undefined)
            counts[label] = (counts[label] || 0) + 1
        })
        return Object.entries(counts).map(([name, value]) => ({ name, value }))
    }, [myTeamEscalations, impactLabel])

    const COLORS = ['#0088FE', '#00C49F', '#FFBB28', '#FF8042', '#A28EFF']

    // Don't show if not in escalation team OR not viewing escalations team specifically
    if (!isEscalationTeam || !isViewingEscalationsOnly) {
        return null
    }

    if (isLoading) {
        return (
            <div className="p-6 bg-white shadow-md rounded-lg border border-gray-200">
                <p className="text-gray-500">Loading...</p>
            </div>
        )
    }

    if (escalationsError) {
        return null
    }

    return (
        <div className="space-y-6">
            {/* Summary Cards */}
            <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                <div className="p-6 bg-white shadow-md rounded-lg border border-gray-200">
                    <h3 className="font-semibold text-gray-700">Total Received</h3>
                    <p className="text-3xl font-bold text-purple-700 mt-2">{escalatedToMyTeam.total}</p>
                </div>
                <div className="p-6 bg-white shadow-md rounded-lg border border-gray-200">
                    <h3 className="font-semibold text-gray-700">Active</h3>
                    <p className="text-3xl font-bold text-yellow-600 mt-2">{escalatedToMyTeam.active}</p>
                </div>
                <div className="p-6 bg-white shadow-md rounded-lg border border-gray-200">
                    <h3 className="font-semibold text-gray-700">Resolved</h3>
                    <p className="text-3xl font-bold text-green-600 mt-2">{escalatedToMyTeam.resolved}</p>
                </div>
            </div>

            {/* Charts */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                <div className="bg-white shadow-md rounded-lg p-4 border">
                    <h3 className="font-semibold mb-2 text-gray-700">Escalations by Status</h3>
                    <ResponsiveContainer width="100%" height={300}>
                        <PieChart>
                            <Pie
                                data={escalationsByStatus}
                                dataKey="value"
                                nameKey="name"
                                cx="50%"
                                cy="40%"
                                outerRadius={80}
                                fill="#8884d8"
                                label
                            >
                                {escalationsByStatus.map((entry, index) => (
                                    <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                                ))}
                            </Pie>
                            <Tooltip />
                            <Legend verticalAlign="bottom" height={60} />
                        </PieChart>
                    </ResponsiveContainer>
                </div>

                <div className="bg-white shadow-md rounded-lg p-4 border">
                    <h3 className="font-semibold mb-2 text-gray-700">Escalations by Impact</h3>
                    <ResponsiveContainer width="100%" height={300}>
                        <PieChart>
                            <Pie
                                data={escalationsByImpact}
                                dataKey="value"
                                nameKey="name"
                                cx="50%"
                                cy="40%"
                                outerRadius={80}
                                fill="#82ca9d"
                                label
                            >
                                {escalationsByImpact.map((entry, index) => (
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

