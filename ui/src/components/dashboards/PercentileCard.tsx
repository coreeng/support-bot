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
    colorScheme = 'blue' 
}: PercentileCardProps) {
    const colors = {
        blue: {
            bg: 'bg-blue-50',
            border: 'border-blue-200',
            text: 'text-blue-800',
            accent: 'text-blue-600',
            iconColor: 'text-blue-600'
        },
        green: {
            bg: 'bg-green-50',
            border: 'border-green-200',
            text: 'text-green-800',
            accent: 'text-green-600',
            iconColor: 'text-green-600'
        },
        purple: {
            bg: 'bg-purple-50',
            border: 'border-purple-200',
            text: 'text-purple-800',
            accent: 'text-purple-600',
            iconColor: 'text-purple-600'
        }
    }

    const scheme = colors[colorScheme]

    return (
        <div className={`w-full ${scheme.bg} border ${scheme.border} rounded-xl p-6 flex flex-col md:flex-row items-center justify-between`}>
            {/* Header */}
            <div className="flex items-center mb-4 md:mb-0">
                {icon ? (
                    <span className={`${scheme.iconColor} mr-2`}>{icon}</span>
                ) : (
                    <Clock className={`w-6 h-6 ${scheme.iconColor} mr-2`} />
                )}
                <h2 className={`text-lg font-semibold ${scheme.text}`}>
                    {title}
                </h2>
            </div>

            {/* Values */}
            <div className="flex justify-around md:justify-between flex-1 w-full mt-4 md:mt-0">
                <div className="text-center flex-1">
                    <p className={`text-sm font-medium ${scheme.accent}`}>P50</p>
                    <p className={`text-2xl font-bold ${scheme.text}`}>{p50}</p>
                </div>
                {p75 && (
                    <div className="text-center flex-1">
                        <p className={`text-sm font-medium ${scheme.accent}`}>P75</p>
                        <p className={`text-2xl font-bold ${scheme.text}`}>{p75}</p>
                    </div>
                )}
                <div className="text-center flex-1">
                    <p className={`text-sm font-medium ${scheme.accent}`}>P90</p>
                    <p className={`text-2xl font-bold ${scheme.text}`}>{p90}</p>
                </div>
            </div>
        </div>
    )
}

