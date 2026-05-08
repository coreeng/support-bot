// src/components/dashboards/ResolutionPercentileChart.tsx
import { formatHoursToDHMS } from '@/lib/utils'
import { Clock } from 'lucide-react'

interface ResolutionPercentileChartProps {
 p50: number
 p75: number
 p90: number
}

export function ResolutionPercentileChart({ p50, p75, p90 }: ResolutionPercentileChartProps) {
 // Convert from seconds to hours for calculations
 const p50Hours = p50 / 3600
 const p75Hours = p75 / 3600
 const p90Hours = p90 / 3600
 const maxHours = p90Hours // Use P90 as the max for scaling

 const percentiles = [
 { label: 'P50', sublabel: 'Median', value: p50Hours, percent: (p50Hours / maxHours) * 100, color: 'bg-success', textColor: 'text-success' },
 { label: 'P75', sublabel: '75th Percentile', value: p75Hours, percent: (p75Hours / maxHours) * 100, color: 'bg-warning', textColor: 'text-warning' },
 { label: 'P90', sublabel: '90th Percentile', value: p90Hours, percent: 100, color: 'bg-destructive', textColor: 'text-destructive' }
 ]

 return (
 <div className="rounded-xl border bg-card p-6">
 <div className="flex items-center gap-2 mb-6">
 <Clock className="w-4 h-4 text-muted-foreground" />
 <h2 className="text-base font-semibold text-foreground">
 Ticket Resolution Durations
 </h2>
 </div>
 
 <div className="space-y-6">
 {percentiles.map((p) => (
 <div key={p.label} className="space-y-2">
 <div className="flex justify-between items-baseline">
 <div className="flex items-baseline gap-2">
 <span className={`text-lg font-bold ${p.textColor}`}>{p.label}</span>
 <span className="text-sm text-muted-foreground">{p.sublabel}</span>
 </div>
 <span className="font-mono text-2xl font-semibold tracking-tight tabular-nums text-foreground">
 {formatHoursToDHMS(p.value)}
 </span>
 </div>
 <div className="w-full bg-muted rounded-full h-2 overflow-hidden">
 <div 
 className={`${p.color} h-full rounded-full transition-all duration-500 ease-out`}
 style={{ width: `${p.percent}%` }}
 />
 </div>
 </div>
 ))}
 </div>

 <div className="mt-6 pt-4 border-t">
 <p className="text-xs text-muted-foreground text-center">
 50% of tickets resolved within {formatHoursToDHMS(p50Hours)} • 90% within {formatHoursToDHMS(p90Hours)}
 </p>
 </div>
 </div>
 )
}

