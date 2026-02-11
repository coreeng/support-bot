'use client'

import React, { useState, useEffect } from 'react'
import { ChevronDown, ChevronRight, ExternalLink, BarChart3 } from 'lucide-react'

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

export default function KnowledgeGapsPage() {
    const [data, setData] = useState<SupportAreaSummary | null>(null)
    const [loading, setLoading] = useState(true)
    const [selectedPeriod, setSelectedPeriod] = useState<'Last Week' | 'Last Month' | 'Last Quarter' | 'Last Year'>('Last Week')
    const [supportAreasExpanded, setSupportAreasExpanded] = useState(false)
    const [knowledgeGapsExpanded, setKnowledgeGapsExpanded] = useState(false)
    const [expandedItems, setExpandedItems] = useState<Set<string>>(new Set())

    useEffect(() => {
        const fetchData = async () => {
            setLoading(true)
            await new Promise(resolve => setTimeout(resolve, 800))

            const mockData: SupportAreaSummary = {
                timePeriod: selectedPeriod,
                totalCoveragePercentage: selectedPeriod === 'Last Week' ? 83 : selectedPeriod === 'Last Month' ? 78 : 85,
                knowledgeGaps: [
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
                        name: 'Monitoring & Troubleshooting Tenant Applications',
                        coveragePercentage: 88,
                        queryCount: 50,
                        queries: [
                            { text: 'How to view application logs?', link: '#' },
                            { text: 'Setting up custom metrics', link: '#' }
                        ]
                    },
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
                        name: 'Deploying & Configuring Tenant Applications',
                        coveragePercentage: 45,
                        queryCount: 15,
                        queries: [
                            { text: 'Deployment failed with timeout', link: '#' },
                            { text: 'Configuring environment variables', link: '#' }
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

            mockData.knowledgeGaps.sort((a, b) => b.coveragePercentage - a.coveragePercentage)
            mockData.supportAreas.sort((a, b) => b.coveragePercentage - a.coveragePercentage)

            setData(mockData)
            setLoading(false)
        }

        fetchData()
    }, [selectedPeriod])

    const toggleItemExpansion = (itemName: string) => {
        setExpandedItems(prev => {
            const next = new Set(prev)
            if (next.has(itemName)) {
                next.delete(itemName)
            } else {
                next.add(itemName)
            }
            return next
        })
    }

    const renderAreaItem = (item: AreaItem, index: number) => {
        const isExpanded = expandedItems.has(item.name)

        return (
            <div key={item.name} className="border border-gray-200 rounded-lg bg-white hover:shadow-md transition-shadow">
                <div className="p-4">
                    <div className="flex items-center justify-between">
                        <div className="flex items-center gap-3 flex-1">
                            <div className="flex items-center justify-center w-8 h-8 rounded-full bg-blue-100 text-blue-700 font-semibold text-sm">
                                {index + 1}
                            </div>
                            <div className="flex-1">
                                <h3 className="font-semibold text-gray-900 text-base">{item.name}</h3>
                                <div className="flex items-center gap-4 mt-1">
                                    <span className="text-sm text-gray-500">{item.queryCount.toLocaleString()} queries</span>
                                    <span className={`text-sm font-medium ${
                                        item.coveragePercentage < 50 ? 'text-green-600' :
                                        item.coveragePercentage < 80 ? 'text-yellow-600' : 'text-red-600'
                                    }`} style={{ display: 'none' }}>
                                        {item.coveragePercentage}% coverage
                                    </span>
                                </div>
                            </div>
                        </div>
                        <button
                            onClick={() => toggleItemExpansion(item.name)}
                            className="ml-4 p-2 hover:bg-gray-100 rounded-lg transition-colors"
                            aria-label={isExpanded ? 'Collapse' : 'Expand'}
                        >
                            {isExpanded ? (
                                <ChevronDown className="w-5 h-5 text-gray-600" />
                            ) : (
                                <ChevronRight className="w-5 h-5 text-gray-600" />
                            )}
                        </button>
                    </div>

                    {isExpanded && (
                        <div className="mt-4 pt-4 border-t border-gray-100">
                            <h4 className="text-sm font-medium text-gray-700 mb-3">Relevant Support Queries</h4>
                            <div className="space-y-2">
                                {item.queries.map((query, qIndex) => (
                                    <div
                                        key={qIndex}
                                        className="flex items-start justify-between p-3 rounded-md bg-gray-50 hover:bg-gray-100 transition-colors group"
                                    >
                                        <span className="text-sm text-gray-700 flex-1">{query.text}</span>
                                        <a
                                            href={query.link}
                                            className="ml-3 flex-shrink-0 text-blue-600 hover:text-blue-700 opacity-0 group-hover:opacity-100 transition-opacity"
                                            aria-label="View query"
                                        >
                                            <ExternalLink className="w-4 h-4" />
                                        </a>
                                    </div>
                                ))}
                            </div>
                        </div>
                    )}
                </div>
            </div>
        )
    }

    if (loading) {
        return (
            <div className="flex items-center justify-center h-full">
                <div className="text-center">
                    <div className="inline-block animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mb-4"></div>
                    <p className="text-gray-600">Loading support area summary...</p>
                </div>
            </div>
        )
    }

    if (!data) return null

    return (
        <div className="h-full overflow-auto bg-gray-50">
            <div className="max-w-7xl mx-auto p-8 space-y-6">
                {/* Header */}
                <div className="flex items-center justify-between">
                    <div>
                        <h1 className="text-3xl font-bold text-gray-900 flex items-center gap-3">
                            <BarChart3 className="w-8 h-8 text-blue-600" />
                            Support Area Summary
                        </h1>
                        <p className="text-gray-600 mt-2">
                            Overview of support areas and knowledge gaps requiring attention
                        </p>
                    </div>

                    <div className="flex items-center gap-4 bg-white px-4 py-3 rounded-lg border border-gray-200 shadow-sm">
                        <label className="text-sm font-medium text-gray-700">Time Period:</label>
                        <select
                            value={selectedPeriod}
                            onChange={(e) => setSelectedPeriod(e.target.value as any)}
                            className="text-sm border-gray-300 rounded-md focus:border-blue-500 focus:ring-blue-500 bg-white"
                        >
                            <option value="Last Week">Last Week</option>
                            <option value="Last Month">Last Month</option>
                            <option value="Last Quarter">Last Quarter</option>
                            <option value="Last Year">Last Year</option>
                        </select>
                    </div>
                </div>

                {/* Overall Coverage */}
                <div className="bg-white rounded-lg border border-gray-200 p-6 shadow-sm" style={{ display: 'none' }}>
                    <div className="flex items-center justify-between">
                        <div>
                            <h2 className="text-sm font-medium text-gray-600">Overall Coverage</h2>
                            <p className="text-4xl font-bold text-gray-900 mt-1">{data.totalCoveragePercentage}%</p>
                        </div>
                        <div className="w-32 h-32">
                            <div className="relative w-full h-full">
                                <svg className="w-full h-full transform -rotate-90">
                                    <circle
                                        cx="64"
                                        cy="64"
                                        r="56"
                                        stroke="#e5e7eb"
                                        strokeWidth="12"
                                        fill="none"
                                    />
                                    <circle
                                        cx="64"
                                        cy="64"
                                        r="56"
                                        stroke="#3b82f6"
                                        strokeWidth="12"
                                        fill="none"
                                        strokeDasharray={`${2 * Math.PI * 56}`}
                                        strokeDashoffset={`${2 * Math.PI * 56 * (1 - data.totalCoveragePercentage / 100)}`}
                                        strokeLinecap="round"
                                    />
                                </svg>
                            </div>
                        </div>
                    </div>
                </div>

                {/* Top 5 Support Areas */}
                <div className="bg-white rounded-lg border border-gray-200 shadow-sm">
                    <button
                        onClick={() => setSupportAreasExpanded(!supportAreasExpanded)}
                        className="w-full px-6 py-4 flex items-center justify-between hover:bg-gray-50 transition-colors"
                    >
                        <div className="flex items-center gap-3">
                            <div className="w-1 h-8 bg-blue-600 rounded-full"></div>
                            <h2 className="text-xl font-semibold text-gray-900">Top 5 Support Areas</h2>
                            <span className="text-sm text-gray-500">({data.supportAreas.length} areas)</span>
                        </div>
                        {supportAreasExpanded ? (
                            <ChevronDown className="w-6 h-6 text-gray-400" />
                        ) : (
                            <ChevronRight className="w-6 h-6 text-gray-400" />
                        )}
                    </button>

                    {supportAreasExpanded && (
                        <div className="px-6 pb-6 space-y-3">
                            {data.supportAreas.slice(0, 5).map((item, index) => renderAreaItem(item, index))}
                        </div>
                    )}
                </div>

                {/* Top 5 Knowledge Gaps */}
                <div className="bg-white rounded-lg border border-gray-200 shadow-sm">
                    <button
                        onClick={() => setKnowledgeGapsExpanded(!knowledgeGapsExpanded)}
                        className="w-full px-6 py-4 flex items-center justify-between hover:bg-gray-50 transition-colors"
                    >
                        <div className="flex items-center gap-3">
                            <div className="w-1 h-8 bg-amber-600 rounded-full"></div>
                            <h2 className="text-xl font-semibold text-gray-900">Top 5 Knowledge Gaps</h2>
                            <span className="text-sm text-gray-500">({data.knowledgeGaps.length} gaps)</span>
                        </div>
                        {knowledgeGapsExpanded ? (
                            <ChevronDown className="w-6 h-6 text-gray-400" />
                        ) : (
                            <ChevronRight className="w-6 h-6 text-gray-400" />
                        )}
                    </button>

                    {knowledgeGapsExpanded && (
                        <div className="px-6 pb-6 space-y-3">
                            {data.knowledgeGaps.slice(0, 5).map((item, index) => renderAreaItem(item, index))}
                        </div>
                    )}
                </div>
            </div>
        </div>
    )
}
