// src/components/dashboards/PercentileCard.tsx
import { Clock } from 'lucide-react'

interface PercentileCardProps {
    title: string
    p50: string
    p90: string
    p75?: string
    icon?: React.ReactNode
    colorScheme?: 'blue' | 'green' | 'purple'
}

export function PercentileCard({
    title,
    p50,
    p90,
    p75,
    icon,
}: PercentileCardProps) {
    return (
        <div className="rounded-xl border bg-card p-6">
            <div className="flex items-center gap-2 mb-6">
                {icon ? (
                    <span className="text-muted-foreground">{icon}</span>
                ) : (
                    <Clock className="h-4 w-4 text-muted-foreground" />
                )}
                <h2 className="text-base font-semibold text-foreground">{title}</h2>
            </div>

            <div className={`grid ${p75 ? 'grid-cols-3' : 'grid-cols-2'} gap-4`}>
                <div>
                    <p className="text-sm font-medium text-muted-foreground">P50</p>
                    <p className="mt-2 font-mono text-2xl font-semibold tracking-tight tabular-nums text-foreground">{p50}</p>
                </div>
                {p75 && (
                    <div>
                        <p className="text-sm font-medium text-muted-foreground">P75</p>
                        <p className="mt-2 font-mono text-2xl font-semibold tracking-tight tabular-nums text-foreground">{p75}</p>
                    </div>
                )}
                <div>
                    <p className="text-sm font-medium text-muted-foreground">P90</p>
                    <p className="mt-2 font-mono text-2xl font-semibold tracking-tight tabular-nums text-foreground">{p90}</p>
                </div>
            </div>
        </div>
    )
}
