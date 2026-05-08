// src/components/dashboards/ScatterChart.tsx
import { ResponsiveContainer, ScatterChart as RechartsScatter, Scatter, CartesianGrid, XAxis, YAxis, Tooltip } from 'recharts'

interface ScatterChartProps {
 title: string
 data: { index: number; value: number }[]
 yAxisLabel?: string
 color?: string
 isLoading?: boolean
}

export function ScatterChart({ 
 title, 
 data, 
 yAxisLabel = 'Minutes',
 color = 'var(--chart-1)',
 isLoading 
}: ScatterChartProps) {
 return (
 <div className="bg-card rounded-xl p-6 border">
 <h2 className="text-base font-semibold text-foreground mb-4">
 {title}
 </h2>
 {isLoading ? (
 <p className="text-muted-foreground">Loading distribution data...</p>
 ) : data.length > 0 ? (
 <ResponsiveContainer width="100%" height={300}>
 <RechartsScatter>
 <CartesianGrid strokeDasharray="3 3" stroke="var(--border)"/>
 <XAxis 
 dataKey="index" 
 name="Ticket"
 label={{ value: 'Ticket Number', position: 'insideBottom', offset: -5 }}
 stroke="var(--border)" tick={{ fill: 'var(--muted-foreground)', fontSize: 11 }}/>
 <YAxis 
 dataKey="value"
 name={yAxisLabel}
 label={{ value: yAxisLabel, angle: -90, position: 'insideLeft' }}
 stroke="var(--border)" tick={{ fill: 'var(--muted-foreground)', fontSize: 11 }}/>
 <Tooltip
 cursor={{ strokeDasharray: '3 3', stroke: 'var(--border)' }}
 formatter={(value: number) => [value.toFixed(1), yAxisLabel]}
 labelFormatter={(label) => `Ticket #${label}`}
 contentStyle={{ background: 'var(--popover)', color: 'var(--popover-foreground)', border: '1px solid var(--border)', borderRadius: '8px', boxShadow: '0 4px 12px rgba(0,0,0,0.15)' }}
 labelStyle={{ color: 'var(--popover-foreground)' }}
 itemStyle={{ color: 'var(--popover-foreground)' }}
 />
 <Scatter data={data} fill={color} />
 </RechartsScatter>
 </ResponsiveContainer>
 ) : (
 <p className="text-muted-foreground">No distribution data available</p>
 )}
 </div>
 )
}

