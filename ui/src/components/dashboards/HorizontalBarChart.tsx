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
        <div className="bg-white shadow-lg rounded-xl p-6 border border-gray-200">
            <h2 className="text-xl font-semibold text-gray-700 mb-4">
                {title}
            </h2>
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
                        <Tooltip formatter={tooltipFormatter} labelFormatter={tooltipLabelFormatter} separator={tooltipSeparator} />
                        <Bar dataKey={dataKey} fill={color} />
                    </BarChart>
                </ResponsiveContainer>
            ) : (
                <p className="text-gray-500">No data available</p>
            )}
        </div>
    )
}

