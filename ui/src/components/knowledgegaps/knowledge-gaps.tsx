'use client'

import React, { useState, useRef, useEffect } from 'react'
import { ChevronDown, ChevronRight, ExternalLink, BarChart3, Download, Upload, FileText, Play } from 'lucide-react'
import { getCsrfToken } from 'next-auth/react'
import { useQueryClient } from '@tanstack/react-query'
import { useAnalysis } from '@/lib/hooks'
import { useToast } from '@/components/ui/toast'
import { useAuth } from '@/hooks/useAuth'

interface AnalysisStatus {
    jobId: string | null
    exportedCount: number | null
    analyzedCount: number | null
    running: boolean
    error: string | null
}

export default function KnowledgeGapsPage() {
    const queryClient = useQueryClient()
    const { isSupportEngineer } = useAuth()
    const { data: analysisData, isLoading, error } = useAnalysis()
    const { showToast } = useToast()
    const [supportAreasExpanded, setSupportAreasExpanded] = useState(true)
    const [knowledgeGapsExpanded, setKnowledgeGapsExpanded] = useState(true)
    const [expandedItems, setExpandedItems] = useState<Set<string>>(new Set())
    const [showAllSupportAreas, setShowAllSupportAreas] = useState(false)
    const [showAllKnowledgeGaps, setShowAllKnowledgeGaps] = useState(false)
    const [isDownloading, setIsDownloading] = useState(false)
    const [isUploading, setIsUploading] = useState(false)
    const [selectedDays, setSelectedDays] = useState<number>(7)
    const fileInputRef = useRef<HTMLInputElement>(null)
    const [analysisStatus, setAnalysisStatus] = useState<AnalysisStatus | null>(null)
    const [isStartingAnalysis, setIsStartingAnalysis] = useState(false)
    const pollingIntervalRef = useRef<NodeJS.Timeout | null>(null)
    const [showCompletedStatus, setShowCompletedStatus] = useState(false)
    const [completedMessage, setCompletedMessage] = useState<string>('')
    const isCompletedRef = useRef(false)
    const [isAnalysisEnabled, setIsAnalysisEnabled] = useState(false)

    // Fetch analysis status
    const fetchAnalysisStatus = async () => {
        try {
            const response = await fetch('/api/analysis/status')
            if (response.ok) {
                const status: AnalysisStatus = await response.json()
                setAnalysisStatus(status)
                return status
            }
        } catch (error) {
            console.error('Error fetching analysis status:', error)
        }
        return null
    }

    // Start polling for analysis status
    const startPolling = () => {
        if (pollingIntervalRef.current) return

        pollingIntervalRef.current = setInterval(async () => {
            // Don't fetch status if we're already showing completion
            if (isCompletedRef.current) return

            const status = await fetchAnalysisStatus()
            if (status && !status.running) {
                stopPolling()
                isCompletedRef.current = true

                // Show completion message in the progress panel
                if (status.error) {
                    setCompletedMessage(`Analysis failed: ${status.error}`)
                    setShowCompletedStatus(true)

                    // Show error toast and hide panel after 5 seconds
                    setTimeout(() => {
                        setShowCompletedStatus(false)
                        setAnalysisStatus(null)
                        isCompletedRef.current = false
                        showToast(`Analysis failed: ${status.error}`, 'error')
                    }, 5000)
                } else {
                    setCompletedMessage(`Analysis complete! Exported: ${status.exportedCount || 0}, Analyzed: ${status.analyzedCount || 0}`)
                    setShowCompletedStatus(true)

                    // Wait 5 seconds, then hide panel and refresh data
                    setTimeout(async () => {
                        setShowCompletedStatus(false)
                        setAnalysisStatus(null)
                        isCompletedRef.current = false
                        await queryClient.invalidateQueries({ queryKey: ['analysis'] })
                    }, 5000)
                }
            }
        }, 3000)
    }

    // Stop polling
    const stopPolling = () => {
        if (pollingIntervalRef.current) {
            clearInterval(pollingIntervalRef.current)
            pollingIntervalRef.current = null
        }
    }

    // Fetch analysis enabled status
    useEffect(() => {
        const fetchAnalysisEnabled = async () => {
            try {
                const response = await fetch('/api/analysis/enabled')
                if (response.ok) {
                    const data = await response.json()
                    setIsAnalysisEnabled(data.enabled)
                }
            } catch (error) {
                console.error('Error fetching analysis enabled status:', error)
            }
        }

        fetchAnalysisEnabled()
    }, [])

    // Check status on mount and when page becomes visible
    useEffect(() => {
        fetchAnalysisStatus()

        return () => {
            stopPolling()
        }
    }, [])

    // Start polling if analysis is running
    useEffect(() => {
        if (analysisStatus?.running) {
            startPolling()
        } else {
            stopPolling()
        }
    }, [analysisStatus?.running])

    const handleStartAnalysis = async () => {
        setIsStartingAnalysis(true)
        try {
            const csrfToken = await getCsrfToken()

            const response = await fetch(`/api/analysis/run?days=${selectedDays}`, {
                method: 'POST',
                headers: {
                    'X-CSRF-Token': csrfToken || '',
                },
            })

            if (response.status === 202) {
                // Analysis started successfully - show progress panel immediately
                setAnalysisStatus({
                    jobId: null,
                    exportedCount: 0,
                    analyzedCount: 0,
                    running: true,
                    error: null
                })

                // Fetch actual status
                const status = await fetchAnalysisStatus()

                // Check if analysis completed immediately (nothing to analyze)
                if (status && !status.running) {
                    isCompletedRef.current = true

                    // Show completion message
                    if (status.error) {
                        setCompletedMessage(`Analysis failed: ${status.error}`)
                        setShowCompletedStatus(true)

                        setTimeout(() => {
                            setShowCompletedStatus(false)
                            setAnalysisStatus(null)
                            isCompletedRef.current = false
                            showToast(`Analysis failed: ${status.error}`, 'error')
                        }, 5000)
                    } else {
                        setCompletedMessage(`Analysis complete! Exported: ${status.exportedCount || 0}, Analyzed: ${status.analyzedCount || 0}`)
                        setShowCompletedStatus(true)

                        setTimeout(async () => {
                            setShowCompletedStatus(false)
                            setAnalysisStatus(null)
                            isCompletedRef.current = false
                            await queryClient.invalidateQueries({ queryKey: ['analysis'] })
                        }, 5000)
                    }
                } else {
                    // Analysis is still running, start polling
                    startPolling()
                }
            } else if (response.status === 409) {
                showToast('Analysis was just started by someone else', 'error')
            } else {
                showToast('Failed to start analysis. Please try again.', 'error')
            }
        } catch (error) {
            console.error('Error starting analysis:', error)
            showToast('Failed to start analysis. Please try again.', 'error')
        } finally {
            setIsStartingAnalysis(false)
        }
    }

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

            const response = await fetch(`/api/summary-data/export?days=${selectedDays}`, {
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

    const handleAnalysisBundleDownload = async () => {
        try {
            // Get CSRF token for protection against cross-site request forgery
            const csrfToken = await getCsrfToken()

            const response = await fetch('/api/summary-data/analysis', {
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
            a.download = 'analysis.zip'
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

            // Invalidate and refetch the analysis data
            await queryClient.invalidateQueries({ queryKey: ['analysis'] })

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

    type ColorTheme = 'blue' | 'amber'

    const themeClasses = {
        blue: {
            badge: 'bg-blue-100 text-blue-700',
            countPill: 'bg-blue-100 text-blue-700',
            queryBg: 'bg-blue-50 hover:bg-blue-100',
            queryAccent: 'border-l-blue-400',
            link: 'text-blue-600 hover:text-blue-700',
            border: 'border-blue-100',
            volumeBar: 'bg-blue-400',
            volumeTrack: 'bg-blue-100',
        },
        amber: {
            badge: 'bg-amber-100 text-amber-700',
            countPill: 'bg-amber-100 text-amber-700',
            queryBg: 'bg-amber-50 hover:bg-amber-100',
            queryAccent: 'border-l-amber-400',
            link: 'text-amber-600 hover:text-amber-700',
            border: 'border-amber-100',
            volumeBar: 'bg-amber-400',
            volumeTrack: 'bg-amber-100',
        },
    }

    const renderAreaItem = (item: { name: string; coveragePercentage: number; queryCount: number; queries: { text: string; link: string | null }[] }, index: number, theme: ColorTheme = 'blue', maxQueryCount: number = 1) => {
        const isExpanded = expandedItems.has(item.name)
        const colors = themeClasses[theme]
        const volumePercent = Math.round((item.queryCount / maxQueryCount) * 100)

        return (
            <div key={item.name} className="border border-gray-200 rounded-xl bg-white hover:shadow-lg hover:-translate-y-0.5 transition-all duration-200 cursor-pointer" onClick={() => toggleItemExpansion(item.name)}>
                <div className="p-5">
                    <div className="flex items-center gap-4">
                        <div className={`flex items-center justify-center w-10 h-10 rounded-xl ${colors.badge} font-bold text-lg shrink-0`}>
                            {index + 1}
                        </div>
                        <div className="flex-1 min-w-0">
                            <div className="flex items-center justify-between">
                                <h3 className="font-semibold text-gray-900 text-lg truncate">{item.name}</h3>
                                <div className="flex items-center gap-3 shrink-0 ml-3">
                                    <span className={`inline-flex items-center px-2.5 py-1 rounded-full text-xs font-semibold ${colors.countPill}`}>
                                        {item.queryCount.toLocaleString()} {item.queryCount === 1 ? 'query' : 'queries'}
                                    </span>
                                    {isExpanded ? (
                                        <ChevronDown className="w-5 h-5 text-gray-400" />
                                    ) : (
                                        <ChevronRight className="w-5 h-5 text-gray-400" />
                                    )}
                                </div>
                            </div>
                            <div className={`mt-2.5 h-2.5 rounded-full ${colors.volumeTrack} overflow-hidden`}>
                                <div
                                    className={`h-full rounded-full ${colors.volumeBar} transition-all duration-700 ease-out`}
                                    style={{ width: `${volumePercent}%` }}
                                />
                            </div>
                        </div>
                    </div>

                    {isExpanded && (
                        <div className={`mt-5 pt-5 border-t ${colors.border}`} onClick={(e) => e.stopPropagation()}>
                            <h4 className="text-xs font-semibold uppercase tracking-wider text-gray-500 mb-3">Relevant Support Queries</h4>
                            <div className="space-y-2">
                                {item.queries.map((query, qIndex) => (
                                    query.link ? (
                                        <a
                                            key={qIndex}
                                            href={query.link}
                                            target="_blank"
                                            rel="noopener noreferrer"
                                            className={`flex items-start gap-3 p-3.5 rounded-lg border-l-4 ${colors.queryAccent} ${colors.queryBg} transition-all duration-150 group no-underline`}
                                        >
                                            <span className="text-sm font-medium text-gray-800 flex-1 leading-relaxed">{query.text}</span>
                                            <span className={`shrink-0 ${colors.link} flex items-center gap-1.5 text-xs font-semibold opacity-60 group-hover:opacity-100 transition-opacity`}>
                                                <span className="hidden sm:inline">View</span>
                                                <ExternalLink className="w-3.5 h-3.5" />
                                            </span>
                                        </a>
                                    ) : (
                                        <div
                                            key={qIndex}
                                            className={`flex items-start gap-3 p-3.5 rounded-lg border-l-4 ${colors.queryAccent} ${colors.queryBg}`}
                                        >
                                            <span className="text-sm font-medium text-gray-800 flex-1 leading-relaxed">{query.text}</span>
                                        </div>
                                    )
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
                    {isSupportEngineer && (
                        <div className="flex flex-col items-end gap-3">
                            <div className="flex items-center gap-2">
                                <select
                                    value={selectedDays}
                                    onChange={(e) => setSelectedDays(Number(e.target.value))}
                                    className="h-10 px-3 border border-gray-200 rounded-xl bg-white text-sm text-gray-700 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                                >
                                    <option value={7}>Week</option>
                                    <option value={31}>Month</option>
                                    <option value={92}>Quarter</option>
                                </select>
                                {isAnalysisEnabled && (
                                    <button
                                        onClick={handleStartAnalysis}
                                        disabled={analysisStatus?.running || isStartingAnalysis}
                                        className="h-10 flex items-center gap-2 px-4 text-sm font-medium rounded-xl bg-purple-600 text-white hover:bg-purple-700 disabled:opacity-50 disabled:cursor-not-allowed transition-all"
                                    >
                                        <Play className="w-4 h-4" />
                                        {isStartingAnalysis ? 'Starting...' : 'Start Analysis'}
                                    </button>
                                )}
                                {!isAnalysisEnabled && (
                                    <>
                                        <input
                                            ref={fileInputRef}
                                            type="file"
                                            accept=".jsonl"
                                            onChange={handleFileChange}
                                            className="hidden"
                                        />
                                        <button
                                            onClick={handleExportDownload}
                                            disabled={isDownloading}
                                            className="h-10 flex items-center gap-2 px-4 text-sm font-medium border border-gray-200 rounded-xl bg-white text-gray-700 hover:bg-gray-50 hover:border-gray-300 disabled:opacity-50 disabled:cursor-not-allowed transition-all"
                                        >
                                            <Download className="w-4 h-4" />
                                            {isDownloading ? 'Downloading...' : 'Export'}
                                        </button>
                                        <button
                                            onClick={handleAnalysisBundleDownload}
                                            className="h-10 flex items-center gap-2 px-4 text-sm font-medium border border-gray-200 rounded-xl bg-white text-gray-700 hover:bg-gray-50 hover:border-gray-300 transition-all"
                                        >
                                            <FileText className="w-4 h-4" />
                                            Analysis Bundle
                                        </button>
                                        <button
                                            onClick={handleImportClick}
                                            disabled={isUploading}
                                            className="h-10 flex items-center gap-2 px-4 text-sm font-medium rounded-xl bg-gray-900 text-white hover:bg-gray-800 disabled:opacity-50 disabled:cursor-not-allowed transition-all"
                                        >
                                            <Upload className="w-4 h-4" />
                                            {isUploading ? 'Uploading...' : 'Import'}
                                        </button>
                                    </>
                                )}
                            </div>
                            {isAnalysisEnabled && (analysisStatus?.running || showCompletedStatus) && (
                                <div className={`border rounded-xl px-4 py-2 text-sm ${
                                    showCompletedStatus
                                        ? 'bg-green-50 border-green-200 text-green-800'
                                        : 'bg-blue-50 border-blue-200 text-blue-800'
                                }`}>
                                    <div className="flex items-center gap-2">
                                        {!showCompletedStatus && (
                                            <div className="inline-block animate-spin rounded-full h-4 w-4 border-b-2 border-blue-600"></div>
                                        )}
                                        <span>
                                            {showCompletedStatus
                                                ? completedMessage
                                                : `Analysis in progress... Exported: ${analysisStatus?.exportedCount || 0}, Analyzed: ${analysisStatus?.analyzedCount || 0}`
                                            }
                                        </span>
                                    </div>
                                </div>
                            )}
                        </div>
                    )}
                </div>

                {/* Top Support Areas */}
                <div className="bg-white rounded-xl border border-gray-200 shadow-sm overflow-hidden">
                    <button
                        onClick={() => setSupportAreasExpanded(!supportAreasExpanded)}
                        className="w-full px-6 py-5 flex items-center justify-between bg-gradient-to-r from-blue-50 to-white hover:from-blue-100 hover:to-blue-50 transition-colors"
                    >
                        <div className="flex items-center gap-4">
                            <div className="w-1.5 h-9 bg-blue-600 rounded-full"></div>
                            <h2 className="text-xl font-bold text-gray-900">Top Support Areas</h2>
                            <span className="px-2.5 py-0.5 rounded-full text-xs font-semibold bg-blue-100 text-blue-700">{analysisData.supportAreas.length}</span>
                        </div>
                        {supportAreasExpanded ? (
                            <ChevronDown className="w-6 h-6 text-gray-400" />
                        ) : (
                            <ChevronRight className="w-6 h-6 text-gray-400" />
                        )}
                    </button>

                    {supportAreasExpanded && (
                        <div className="px-6 pb-6 pt-2 space-y-3">
                            {(() => {
                                const items = showAllSupportAreas ? analysisData.supportAreas : analysisData.supportAreas.slice(0, 5)
                                const maxCount = items[0]?.queryCount ?? 1
                                return items.map((item, index) => renderAreaItem(item, index, 'blue', maxCount))
                            })()}
                            {analysisData.supportAreas.length > 5 && (
                                <button
                                    onClick={() => setShowAllSupportAreas(!showAllSupportAreas)}
                                    className="w-full py-2.5 text-sm text-blue-600 hover:text-blue-700 font-semibold hover:bg-blue-50 rounded-lg transition-colors"
                                >
                                    {showAllSupportAreas ? 'Show less' : `Show all ${analysisData.supportAreas.length} areas`}
                                </button>
                            )}
                        </div>
                    )}
                </div>

                {/* Top 5 Knowledge Gaps */}
                <div className="bg-white rounded-xl border border-gray-200 shadow-sm overflow-hidden">
                    <button
                        onClick={() => setKnowledgeGapsExpanded(!knowledgeGapsExpanded)}
                        className="w-full px-6 py-5 flex items-center justify-between bg-gradient-to-r from-amber-50 to-white hover:from-amber-100 hover:to-amber-50 transition-colors"
                    >
                        <div className="flex items-center gap-4">
                            <div className="w-1.5 h-9 bg-amber-600 rounded-full"></div>
                            <h2 className="text-xl font-bold text-gray-900">Top Knowledge Gaps</h2>
                            <span className="px-2.5 py-0.5 rounded-full text-xs font-semibold bg-amber-100 text-amber-700">{analysisData.knowledgeGaps.length}</span>
                        </div>
                        {knowledgeGapsExpanded ? (
                            <ChevronDown className="w-6 h-6 text-gray-400" />
                        ) : (
                            <ChevronRight className="w-6 h-6 text-gray-400" />
                        )}
                    </button>

                    {knowledgeGapsExpanded && (
                        <div className="px-6 pb-6 pt-2 space-y-3">
                            {(() => {
                                const items = showAllKnowledgeGaps ? analysisData.knowledgeGaps : analysisData.knowledgeGaps.slice(0, 5)
                                const maxCount = items[0]?.queryCount ?? 1
                                return items.map((item, index) => renderAreaItem(item, index, 'amber', maxCount))
                            })()}
                            {analysisData.knowledgeGaps.length > 5 && (
                                <button
                                    onClick={() => setShowAllKnowledgeGaps(!showAllKnowledgeGaps)}
                                    className="w-full py-2.5 text-sm text-amber-600 hover:text-amber-700 font-semibold hover:bg-amber-50 rounded-lg transition-colors"
                                >
                                    {showAllKnowledgeGaps ? 'Show less' : `Show all ${analysisData.knowledgeGaps.length} gaps`}
                                </button>
                            )}
                        </div>
                    )}
                </div>
            </div>
        </div>
    )
}
