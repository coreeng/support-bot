// src/components/dashboards/MetricCard.tsx
import React from 'react'

interface MetricCardProps {
    title: string
    value: string | number
    description?: string
    isLoading?: boolean
    icon?: React.ReactNode
    colorScheme?: 'blue' | 'orange' | 'green' | 'purple' | 'cyan' | 'red'
}

export function MetricCard({
    title,
    value,
    description,
    isLoading,
    icon,
    colorScheme = 'blue'
}: MetricCardProps) {
    const valueColor: Record<NonNullable<MetricCardProps['colorScheme']>, string> = {
        blue: 'text-info',
        orange: 'text-warning',
        green: 'text-success',
        purple: 'text-foreground',
        cyan: 'text-info',
        red: 'text-destructive',
    }

    return (
        <div className="rounded-xl border bg-card p-6">
            <div className="flex items-center gap-2 mb-6">
                {icon && <span className="text-muted-foreground">{icon}</span>}
                <h2 className="text-base font-semibold text-foreground">{title}</h2>
            </div>
            {isLoading ? (
                <p className="text-sm text-muted-foreground">Loading...</p>
            ) : (
                <p className={`font-mono text-2xl font-semibold tracking-tight tabular-nums ${valueColor[colorScheme]}`}>
                    {value}
                </p>
            )}
            {description && (
                <p className="mt-2 text-sm text-muted-foreground">{description}</p>
            )}
        </div>
    )
}
