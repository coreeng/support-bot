// src/components/dashboards/HorizontalBarChart.tsx
import { ResponsiveContainer, BarChart, Bar, CartesianGrid, XAxis, YAxis, Tooltip } from 'recharts'
import type { Props as TooltipContentProps, ValueType, NameType } from 'recharts/types/component/DefaultTooltipContent'

interface HorizontalBarChartProps<V extends ValueType = ValueType, N extends NameType = NameType> {
    title: string
    data: Record<string, unknown>[]
    dataKey: string
    yAxisDataKey: string
    color: string
    tooltipFormatter?: TooltipContentProps<V, N>['formatter']
    tooltipLabelFormatter?: TooltipContentProps<V, N>['labelFormatter']
    tooltipSeparator?: TooltipContentProps<V, N>['separator']
    height?: number
}

export function HorizontalBarChart<V extends ValueType = ValueType, N extends NameType = NameType>({
    title,
    data,
    dataKey,
    yAxisDataKey,
    color,
    tooltipFormatter,
    tooltipLabelFormatter,
    tooltipSeparator,
    height = 350
}: HorizontalBarChartProps<V, N>) {
    return (
        <div className="rounded-xl border bg-card p-5">
            <h3 className="text-sm font-medium text-muted-foreground mb-3">
                {title}
            </h3>
            {data && data.length > 0 ? (
                <ResponsiveContainer width="100%" height={height}>
                    <BarChart data={data} layout="vertical">
                        <CartesianGrid strokeDasharray="3 3" />
                        <XAxis type="number" />
                        <YAxis 
                            dataKey={yAxisDataKey} 
                            type="category" 
                            width={150}
                            style={{ fontSize: '12px' }}
                        />
                        <Tooltip
                            formatter={tooltipFormatter}
                            labelFormatter={tooltipLabelFormatter}
                            separator={tooltipSeparator}
                            contentStyle={{
                                background: 'var(--popover)',
                                color: 'var(--popover-foreground)',
                                border: '1px solid var(--border)',
                                borderRadius: '8px',
                                boxShadow: '0 4px 12px rgba(0,0,0,0.15)',
                            }}
                            labelStyle={{ color: 'var(--popover-foreground)' }}
                            itemStyle={{ color: 'var(--popover-foreground)' }}
                            cursor={{ fill: 'var(--accent)' }}
                        />
                        <Bar dataKey={dataKey} fill={color} />
                    </BarChart>
                </ResponsiveContainer>
            ) : (
                <p className="text-muted-foreground">No data available</p>
            )}
        </div>
    )
}

