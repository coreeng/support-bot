// src/components/dashboards/EscalationSLASection.tsx
import { HorizontalBarChart } from './HorizontalBarChart'
import { TimeSeriesChart } from './TimeSeriesChart'
import { RefreshButton } from './RefreshButton'
import { ResponsiveContainer, BarChart, Bar, CartesianGrid, XAxis, YAxis, Tooltip } from 'recharts'
import { useRegistry } from '@/lib/hooks'

interface EscalationSLASectionProps {
    avgEscalationDurationByTag: { tag: string; avgDuration: number }[] | undefined
    escalationPercentageByTag: { tag: string; count: number }[] | undefined
    escalationTrendsByDate: { date: string; escalations: number }[] | undefined
    escalationsByTeam: { assigneeName: string; totalEscalations: number }[] | undefined
    escalationsByImpact: { impactLevel: string; totalEscalations: number }[] | undefined
    isRefreshing: boolean
    onRefresh: () => void
}

export function EscalationSLASection({
    avgEscalationDurationByTag,
    escalationPercentageByTag,
    escalationTrendsByDate,
    escalationsByTeam,
    escalationsByImpact,
    isRefreshing,
    onRefresh
}: EscalationSLASectionProps) {
    const { data: registryData } = useRegistry()

    const impactLabel = (code?: string) => {
        if (!code) return code
        const match = registryData?.impacts?.find((i: { code: string; label: string }) => i.code === code)
        return match?.label || code
    }

    const mappedEscalationsByImpact = escalationsByImpact?.map(item => ({
        ...item,
        impactLevelLabel: impactLabel(item.impactLevel) || item.impactLevel
    }))

    return (
        <div>
            <div className="flex justify-between items-center mb-6">
                <div>
                    <h2 className="text-xl font-bold text-gray-800">Escalation Analysis</h2>
                    <p className="text-sm text-gray-500">Track escalation patterns and team performance</p>
                </div>
                <RefreshButton onRefresh={onRefresh} isRefreshing={isRefreshing} />
            </div>
            
            <div className="grid grid-cols-1 xl:grid-cols-2 gap-6 mb-6">
                <HorizontalBarChart
                    title="Average Escalation Duration by Tag (Top 15)"
                    data={avgEscalationDurationByTag || []}
                    dataKey="avgDuration"
                    yAxisDataKey="tag"
                    color="#8b5cf6"
                    tooltipFormatter={(value: number) => [value.toFixed(2) + ' hours', 'Avg Duration']}
                />
                
                <HorizontalBarChart
                    title="Escalation Count by Tag (Top 15)"
                    data={escalationPercentageByTag || []}
                    dataKey="count"
                    yAxisDataKey="tag"
                    color="#a855f7"
                />
            </div>
            
            <div className="grid grid-cols-1 xl:grid-cols-2 gap-6 mb-6">
                {/* Escalation Trends */}
                <TimeSeriesChart
                    title="Escalation Trends Over Time"
                    data={escalationTrendsByDate?.map(d => ({ week: d.date, escalations: d.escalations })) || []}
                    lines={[
                        { dataKey: 'escalations', name: 'Escalations', color: '#033cad' }
                    ]}
                    yAxisLabel="Escalations"
                />
                
                <HorizontalBarChart
                    title="Escalations by Team (Top 10)"
                    data={escalationsByTeam || []}
                    dataKey="totalEscalations"
                    yAxisDataKey="assigneeName"
                    color="#7c3aed"
                />
                
                {/* Escalations by Impact */}
                <div className="bg-white shadow-lg rounded-xl p-6 border border-gray-200">
                    <h2 className="text-xl font-semibold text-gray-700 mb-4">
                        Escalations by Impact Level
                    </h2>
                    {mappedEscalationsByImpact && mappedEscalationsByImpact.length > 0 ? (
                        <ResponsiveContainer width="100%" height={350}>
                            <BarChart data={mappedEscalationsByImpact}>
                                <CartesianGrid strokeDasharray="3 3" />
                                <XAxis 
                                    dataKey="impactLevelLabel" 
                                    angle={-45}
                                    textAnchor="end"
                                    height={100}
                                    interval={0}
                                    style={{ fontSize: '12px' }}
                                />
                                <YAxis />
                                <Tooltip />
                                <Bar dataKey="totalEscalations" fill="#6366f1" />
                            </BarChart>
                        </ResponsiveContainer>
                    ) : (
                        <p className="text-gray-500">No data available</p>
                    )}
                </div>
            </div>
        </div>
    )
}

