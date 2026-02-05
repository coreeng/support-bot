'use client'

import React, { useState, useEffect } from 'react'
import { ExternalLink, BookOpen, AlertTriangle, ChevronDown, ChevronUp } from 'lucide-react'

// Local component definitions to replace missing shadcn/ui components
const Card = ({ className, children }: { className?: string; children: React.ReactNode }) => (
    <div className={`rounded-xl border bg-card text-card-foreground shadow-sm ${className}`}>{children}</div>
)

const CardHeader = ({ className, children }: { className?: string; children: React.ReactNode }) => (
    <div className={`flex flex-col space-y-1.5 p-6 ${className}`}>{children}</div>
)

const CardTitle = ({ className, children }: { className?: string; children: React.ReactNode }) => (
    <div className={`font-semibold leading-none tracking-tight ${className}`}>{children}</div>
)

const CardContent = ({ className, children }: { className?: string; children: React.ReactNode }) => (
    <div className={`p-6 pt-0 ${className}`}>{children}</div>
)

const Badge = ({ className, variant, children }: { className?: string; variant?: string; children: React.ReactNode }) => (
    <div className={`inline-flex items-center rounded-md border px-2.5 py-0.5 text-xs font-semibold transition-colors focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2 ${className}`}>{children}</div>
)

const Progress = ({ value, className }: { value: number; className?: string }) => (
    <div className={`relative w-full overflow-hidden rounded-full bg-gray-200 ${className}`}>
        <div
            className="h-full w-full flex-1 bg-blue-600 transition-all"
            style={{ transform: `translateX(-${100 - (value || 0)}%)` }}
        />
    </div>
)

interface Query {
    text: string
    link: string
}

interface AreaItem {
    name: string
    coveragePercentage: number
    queryCount: number
    queries: Query[]
}

interface SupportAreaSummary {
    timePeriod: 'Last Week' | 'Last Month' | 'Last Quarter' | 'Last Year'
    totalCoveragePercentage: number
    knowledgeGaps: AreaItem[]
    supportAreas: AreaItem[]
}

// Component for individual Area Card to handle expanded state
const AreaCard = ({ item, index, isGap }: { item: AreaItem; index: number; isGap: boolean }) => {
    const [expanded, setExpanded] = useState(false)

    return (
        <Card className="overflow-hidden border-l-4 border-l-blue-500 hover:shadow-md transition-shadow">
            <CardHeader className="bg-gray-50/50 pb-3">
                <div className="flex justify-between items-start">
                    <CardTitle className="text-lg font-semibold text-gray-800 flex items-center gap-2">
                        <span className="flex items-center justify-center w-6 h-6 rounded-full bg-blue-100 text-blue-600 text-xs font-bold">
                            {index + 1}
                        </span>
                        {item.name}
                    </CardTitle>
                    <div className="flex items-center gap-4">
                        <div className="text-sm font-medium text-gray-500" title="Number of queries">
                            {item.queryCount} Queries
                        </div>
                        <div className="flex items-center gap-2" title={`Coverage: ${item.coveragePercentage}%`}>
                            <span className={`text-sm font-medium ${item.coveragePercentage < 50 ? 'text-green-600' :
                                item.coveragePercentage < 80 ? 'text-yellow-600' : 'text-red-600'
                                }`}>
                                {item.coveragePercentage}% Coverage
                            </span>
                            <Progress
                                value={item.coveragePercentage}
                                className="w-20 h-1.5"
                            />
                        </div>
                    </div>
                </div>
            </CardHeader>
            <CardContent className="pt-4">
                <div className="space-y-3">
                    <button
                        onClick={() => setExpanded(!expanded)}
                        className="flex items-center gap-2 text-sm font-medium text-gray-500 uppercase tracking-wider hover:text-gray-700 transition-colors focus:outline-none"
                    >
                        <AlertTriangle className="w-3 h-3" />
                        Relevant Queries
                        {expanded ? <ChevronUp className="w-4 h-4" /> : <ChevronDown className="w-4 h-4" />}
                    </button>

                    {expanded && (
                        <ul className="space-y-2 animate-in fade-in slide-in-from-top-2 duration-200">
                            {item.queries.map((query, qIndex) => (
                                <li key={qIndex} className="group flex items-center justify-between p-2 rounded-md hover:bg-gray-50 border border-transparent hover:border-gray-100 transition-colors">
                                    <span className="text-gray-700 font-medium text-sm">
                                        &quot;{query.text}&quot;
                                    </span>
                                    <a
                                        href={query.link}
                                        className="text-blue-600 opacity-0 group-hover:opacity-100 transition-opacity flex items-center gap-1 text-xs font-medium hover:underline"
                                    >
                                        View Query <ExternalLink className="w-3 h-3" />
                                    </a>
                                </li>
                            ))}
                        </ul>
                    )}
                </div>
            </CardContent>
        </Card>
    )
}

export default function KnowledgeGapsPage() {
    const [data, setData] = useState<SupportAreaSummary | null>(null)
    const [loading, setLoading] = useState(true)
    const [selectedPeriod, setSelectedPeriod] = useState<'Last Week' | 'Last Month' | 'Last Quarter' | 'Last Year'>('Last Week')

    useEffect(() => {
        // Mock data fetching
        const fetchData = async () => {
            setLoading(true)
            // Simulate network delay
            await new Promise(resolve => setTimeout(resolve, 800))

            const mockData: SupportAreaSummary = {
                timePeriod: selectedPeriod,
                totalCoveragePercentage: selectedPeriod === 'Last Week' ? 83 : selectedPeriod === 'Last Month' ? 78 : 85,
                knowledgeGaps: [
                    {
                        name: 'CI',
                        coveragePercentage: 75,
                        queryCount: 42,
                        queries: [
                            { text: 'How do I fix the CI pipeline failure?', link: '#' },
                            { text: 'What is the correct configuration for the build step?', link: '#' }
                        ]
                    },
                    {
                        name: 'Configuring Platform Features - Kafka and Dial',
                        coveragePercentage: 60,
                        queryCount: 35,
                        queries: [
                            { text: 'How to setup Kafka consumers?', link: '#' },
                            { text: 'Dial configuration for new tenant', link: '#' }
                        ]
                    },
                    {
                        name: 'Connectivity and Networking',
                        coveragePercentage: 90,
                        queryCount: 28,
                        queries: [
                            { text: 'Firewall rules for output traffic', link: '#' },
                            { text: 'DNS resolution issues', link: '#' }
                        ]
                    },
                    {
                        name: 'Deploying & Configuring Tenant Applications',
                        coveragePercentage: 45,
                        queryCount: 15,
                        queries: [
                            { text: 'Deployment failed with timeout', link: '#' },
                            { text: 'Configuring environment variables', link: '#' }
                        ]
                    },
                    {
                        name: 'Monitoring & Troubleshooting Tenant Applications',
                        coveragePercentage: 88,
                        queryCount: 50,
                        queries: [
                            { text: 'How to view application logs?', link: '#' },
                            { text: 'Setting up custom metrics', link: '#' }
                        ]
                    }
                ],
                supportAreas: [
                    {
                        name: 'Knowledge Gap',
                        coveragePercentage: 56,
                        queryCount: 2127,
                        queries: [
                            { text: 'Documentation missing for new API', link: '#' },
                            { text: 'How to configure advanced settings?', link: '#' }
                        ]
                    },
                    {
                        name: 'Product Temporary Issue',
                        coveragePercentage: 22,
                        queryCount: 825,
                        queries: [
                            { text: 'Service temporarily unavailable', link: '#' },
                            { text: '503 errors on login', link: '#' }
                        ]
                    },
                    {
                        name: 'Task Request',
                        coveragePercentage: 13,
                        queryCount: 493,
                        queries: [
                            { text: 'Please reset my API key', link: '#' },
                            { text: 'Update billing address', link: '#' }
                        ]
                    },
                    {
                        name: 'Product Usability Problem',
                        coveragePercentage: 5,
                        queryCount: 196,
                        queries: [
                            { text: 'Cannot find the logout button', link: '#' },
                            { text: 'Dashboard is confusing', link: '#' }
                        ]
                    },
                    {
                        name: 'Feature Request',
                        coveragePercentage: 4,
                        queryCount: 163,
                        queries: [
                            { text: 'Add dark mode support', link: '#' },
                            { text: 'Export report to PDF', link: '#' }
                        ]
                    }
                ]
            }

            // Sort data by coverage percentage (descending)
            mockData.knowledgeGaps.sort((a, b) => b.coveragePercentage - a.coveragePercentage)
            mockData.supportAreas.sort((a, b) => b.coveragePercentage - a.coveragePercentage)

            setData(mockData)
            setLoading(false)
        }

        fetchData()
    }, [selectedPeriod])

    if (loading) {
        return (
            <div className="p-8 flex items-center justify-center">
                <div className="text-center text-gray-500">
                    <div className="inline-block animate-spin rounded-full h-8 w-8 border-b-2 border-blue-500 mb-2"></div>
                    <p>Loading knowledge gaps...</p>
                </div>
            </div>
        )
    }

    if (!data) return null

    return (
        <div className="p-8 max-w-7xl mx-auto space-y-8">
            <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
                <div>
                    <h1 className="text-2xl font-bold text-gray-900 flex items-center gap-2">
                        <BookOpen className="w-8 h-8 text-blue-600" />
                        Support Area Summary
                    </h1>
                    <p className="text-gray-500 mt-1">
                        Identify areas where with Knowledge Gaps that need to be addressed.
                    </p>
                </div>
                <div className="flex flex-col sm:flex-row items-start sm:items-center gap-4 bg-white p-3 rounded-lg border border-gray-200 shadow-sm">
                    <div className="flex items-center gap-2">
                        <span className="text-sm font-medium text-gray-500">Period:</span>
                        <select
                            value={selectedPeriod}
                            onChange={(e) => setSelectedPeriod(e.target.value as any)}
                            className="text-sm border-gray-300 rounded-md shadow-sm focus:border-blue-500 focus:ring-blue-500 bg-gray-50 text-gray-900 py-1 pl-2 pr-8 border"
                        >
                            <option value="Last Week">Last Week</option>
                            <option value="Last Month">Last Month</option>
                            <option value="Last Quarter">Last Quarter</option>
                            <option value="Last Year">Last Year</option>
                        </select>
                    </div>

                    <div className="hidden sm:block h-4 w-px bg-gray-300"></div>

                    <div className="flex items-center gap-3">
                        <div className="text-sm font-medium text-gray-500">Coverage:</div>
                        <div className="flex items-center gap-2">
                            <span className="text-lg font-bold text-gray-900">{data.totalCoveragePercentage}%</span>
                            <div className="w-24">
                                <Progress value={data.totalCoveragePercentage} className="h-2" />
                            </div>
                        </div>
                    </div>
                </div>
            </div>

            <div className="space-y-6">
                <div>
                    <h2 className="text-xl font-semibold text-gray-900 mb-4 flex items-center gap-2">
                        <span className="w-1.5 h-6 bg-green-500 rounded-full"></span>
                        Support Areas
                    </h2>
                    <div className="grid gap-6">
                        {data.supportAreas.map((item, index) => (
                            <AreaCard key={`support-${index}`} item={item} index={index} isGap={false} />
                        ))}
                    </div>
                </div>

                <div>
                    <h2 className="text-xl font-semibold text-gray-900 mb-4 flex items-center gap-2">
                        <span className="w-1.5 h-6 bg-blue-500 rounded-full"></span>
                        Top Knowledge Gaps
                    </h2>
                    <div className="grid gap-6">
                        {data.knowledgeGaps.map((item, index) => (
                            <AreaCard key={`gap-${index}`} item={item} index={index} isGap={true} />
                        ))}
                    </div>
                </div>
            </div>
        </div>
    )
}
