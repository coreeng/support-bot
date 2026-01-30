// src/components/dashboards/MetricCard.tsx
import React from 'react'

interface MetricCardProps {
    title: string
    value: string | number
    description?: string
    isLoading?: boolean
    colorScheme?: 'blue' | 'orange' | 'green' | 'purple' | 'cyan'
}

export function MetricCard({ 
    title, 
    value, 
    description, 
    isLoading, 
    colorScheme = 'blue' 
}: MetricCardProps) {
    const colors = {
        blue: {
            bg: 'bg-blue-50',
            border: 'border-blue-200',
            title: 'text-blue-800',
            value: 'text-blue-600'
        },
        orange: {
            bg: 'bg-orange-50',
            border: 'border-orange-200',
            title: 'text-orange-800',
            value: 'text-orange-600'
        },
        green: {
            bg: 'bg-green-50',
            border: 'border-green-200',
            title: 'text-green-800',
            value: 'text-green-600'
        },
        purple: {
            bg: 'bg-purple-50',
            border: 'border-purple-200',
            title: 'text-purple-800',
            value: 'text-purple-600'
        },
        cyan: {
            bg: 'bg-cyan-50',
            border: 'border-cyan-200',
            title: 'text-cyan-800',
            value: 'text-cyan-600'
        }
    }

    const scheme = colors[colorScheme]

    return (
        <div className={`${scheme.bg} border ${scheme.border} rounded-xl p-6`}>
            <h2 className={`text-lg font-semibold ${scheme.title} mb-2`}>
                {title}
            </h2>
            {isLoading ? (
                <p className="text-gray-500">Loading...</p>
            ) : (
                <p className={`text-3xl font-bold ${scheme.value}`}>
                    {value}
                </p>
            )}
            {description && (
                <p className="text-sm text-gray-600 mt-2">
                    {description}
                </p>
            )}
        </div>
    )
}

