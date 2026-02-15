'use client'

import React, { useState, useRef } from 'react'
import { ChevronDown, ChevronRight, ExternalLink, BarChart3, Download, Upload, FileText } from 'lucide-react'
import { getCsrfToken } from 'next-auth/react'
import { useAnalysis } from '@/lib/hooks'
import { useToast } from '@/components/ui/toast'

export default function KnowledgeGapsPage() {
    const { data: analysisData, isLoading, error } = useAnalysis()
    const { showToast } = useToast()
    const [supportAreasExpanded, setSupportAreasExpanded] = useState(false)
    const [knowledgeGapsExpanded, setKnowledgeGapsExpanded] = useState(false)
    const [expandedItems, setExpandedItems] = useState<Set<string>>(new Set())
    const [isDownloading, setIsDownloading] = useState(false)
    const [isUploading, setIsUploading] = useState(false)
    const fileInputRef = useRef<HTMLInputElement>(null)

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

    const handleExportDownload = async () => {
        setIsDownloading(true)
        try {
            // Get CSRF token for protection against cross-site request forgery
            const csrfToken = await getCsrfToken()

            const response = await fetch('/api/summary-data/export?days=31', {
                headers: {
                    'X-CSRF-Token': csrfToken || '',
                },
            })
            if (!response.ok) {
                throw new Error('Failed to download export')
            }

            const blob = await response.blob()
            const url = window.URL.createObjectURL(blob)
            const a = document.createElement('a')
            a.href = url
            a.download = 'content.zip'
            document.body.appendChild(a)
            a.click()
            window.URL.revokeObjectURL(url)
            document.body.removeChild(a)
        } catch (error) {
            console.error('Error downloading export:', error)
            showToast('Failed to download export. Please try again.', 'error')
        } finally {
            setIsDownloading(false)
        }
    }

    const handlePromptDownload = async () => {
        try {
            // Get CSRF token for protection against cross-site request forgery
            const csrfToken = await getCsrfToken()

            const response = await fetch('/api/prompt', {
                headers: {
                    'X-CSRF-Token': csrfToken || '',
                },
            })

            if (!response.ok) {
                throw new Error('Failed to download prompt')
            }

            const blob = await response.blob()
            const url = window.URL.createObjectURL(blob)
            const a = document.createElement('a')
            a.href = url
            a.download = 'gap_analysis_taxonomy_summary-prompt.md'
            document.body.appendChild(a)
            a.click()
            window.URL.revokeObjectURL(url)
            document.body.removeChild(a)
        } catch (error) {
            console.error('Error downloading prompt:', error)
            showToast('Failed to download prompt. Please try again.', 'error')
        }
    }

    const handleImportClick = () => {
        fileInputRef.current?.click()
    }

    const handleFileChange = async (event: React.ChangeEvent<HTMLInputElement>) => {
        const file = event.target.files?.[0]
        if (!file) return

        setIsUploading(true)
        try {
            // Get CSRF token for protection against cross-site request forgery
            const csrfToken = await getCsrfToken()

            const formData = new FormData()
            formData.append('file', file)

            const response = await fetch('/api/summary-data/import', {
                method: 'POST',
                headers: {
                    'X-CSRF-Token': csrfToken || '',
                },
                body: formData,
            })

            if (!response.ok) {
                throw new Error('Failed to upload file')
            }

            const result = await response.json()
            showToast(`Import successful! ${result.recordsImported} records imported.`, 'success')

            // Reset the file input
            if (fileInputRef.current) {
                fileInputRef.current.value = ''
            }
        } catch (error) {
            console.error('Error uploading file:', error)
            showToast('Failed to upload file. Please try again.', 'error')
        } finally {
            setIsUploading(false)
        }
    }

    const renderAreaItem = (item: { name: string; coveragePercentage: number; queryCount: number; queries: { text: string; link: string }[] }, index: number) => {
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

    if (isLoading) {
        return (
            <div className="flex items-center justify-center h-full">
                <div className="text-center">
                    <div className="inline-block animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mb-4"></div>
                    <p className="text-gray-600">Loading support area summary...</p>
                </div>
            </div>
        )
    }

    if (error) {
        return (
            <div className="flex items-center justify-center h-full">
                <div className="text-center">
                    <p className="text-red-600">Error loading analysis data</p>
                    <p className="text-gray-600 text-sm mt-2">Please try again later</p>
                </div>
            </div>
        )
    }

    if (!analysisData) return null

    return (
        <div className="h-full overflow-auto bg-gray-50">
            <div className="max-w-7xl mx-auto p-8 space-y-6">
                {/* Header */}
                <div className="flex items-start justify-between">
                    <div>
                        <h1 className="text-3xl font-bold text-gray-900 flex items-center gap-3">
                            <BarChart3 className="w-8 h-8 text-blue-600" />
                            Support Area Summary
                        </h1>
                        <p className="text-gray-600 mt-2">
                            Overview of support areas and knowledge gaps requiring attention
                        </p>
                    </div>
                    <div className="flex items-center gap-3">
                        <input
                            ref={fileInputRef}
                            type="file"
                            accept=".tsv,.txt"
                            onChange={handleFileChange}
                            className="hidden"
                        />
                        <button
                            onClick={handleExportDownload}
                            disabled={isDownloading}
                            className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:bg-gray-400 disabled:cursor-not-allowed transition-colors"
                        >
                            <Download className="w-4 h-4" />
                            {isDownloading ? 'Downloading...' : 'Export Data'}
                        </button>
                        <button
                            onClick={handlePromptDownload}
                            className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
                        >
                            <FileText className="w-4 h-4" />
                            Get Prompt
                        </button>
                        <button
                            onClick={handleImportClick}
                            disabled={isUploading}
                            className="flex items-center gap-2 px-4 py-2 bg-green-600 text-white rounded-lg hover:bg-green-700 disabled:bg-gray-400 disabled:cursor-not-allowed transition-colors"
                        >
                            <Upload className="w-4 h-4" />
                            {isUploading ? 'Uploading...' : 'Import Data'}
                        </button>
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
                            <span className="text-sm text-gray-500">({analysisData.supportAreas.length} areas)</span>
                        </div>
                        {supportAreasExpanded ? (
                            <ChevronDown className="w-6 h-6 text-gray-400" />
                        ) : (
                            <ChevronRight className="w-6 h-6 text-gray-400" />
                        )}
                    </button>

                    {supportAreasExpanded && (
                        <div className="px-6 pb-6 space-y-3">
                            {analysisData.supportAreas.slice(0, 5).map((item, index) => renderAreaItem(item, index))}
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
                            <span className="text-sm text-gray-500">({analysisData.knowledgeGaps.length} gaps)</span>
                        </div>
                        {knowledgeGapsExpanded ? (
                            <ChevronDown className="w-6 h-6 text-gray-400" />
                        ) : (
                            <ChevronRight className="w-6 h-6 text-gray-400" />
                        )}
                    </button>

                    {knowledgeGapsExpanded && (
                        <div className="px-6 pb-6 space-y-3">
                            {analysisData.knowledgeGaps.slice(0, 5).map((item, index) => renderAreaItem(item, index))}
                        </div>
                    )}
                </div>
            </div>
        </div>
    )
}
