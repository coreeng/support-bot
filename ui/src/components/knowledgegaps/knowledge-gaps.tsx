'use client'

import React, { useState, useRef, useEffect, useId } from 'react'
import { ChevronDown, ChevronRight, BarChart3, Download, Upload, FileText, Play, CheckCircle2, AlertCircle, ShieldCheck } from 'lucide-react'
import { useQueryClient } from '@tanstack/react-query'
import { useAnalysis, apiFetch } from '@/lib/hooks'
import { useToast } from '@/components/ui/toast'
import { useAuth } from '@/hooks/useAuth'
import type { DimensionSummary, QuerySummary } from '@/lib/types'
import EditTicketModal from '@/components/tickets/EditTicketModal'

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
    const completionTimeoutRef = useRef<NodeJS.Timeout | null>(null)
    const [showCompletedStatus, setShowCompletedStatus] = useState(false)
    const [completedMessage, setCompletedMessage] = useState<string>('')
    const [isCompletionError, setIsCompletionError] = useState(false)
    const [selectedTicketId, setSelectedTicketId] = useState<string | null>(null)
    const [isTicketModalOpen, setIsTicketModalOpen] = useState(false)
    const isCompletedRef = useRef(false)
    const [isAnalysisEnabled, setIsAnalysisEnabled] = useState(false)
    const [isSettingsOpen, setIsSettingsOpen] = useState(false)
    const settingsContainerRef = useRef<HTMLDivElement>(null)
    const settingsTriggerRef = useRef<HTMLButtonElement>(null)
    const queryWindowSelectRef = useRef<HTMLSelectElement>(null)
    const restoreFocusOnCloseRef = useRef(false)
    const wasSettingsOpenRef = useRef(false)
    const settingsTitleId = useId()
    const settingsDescriptionId = useId()
    const settingsPanelId = 'analysis-settings-popover'

    const formatQueryTimestamp = (timestamp: string): string => {
        const slackTsMatch = /^(\d+)(?:\.\d+)?$/.exec(timestamp)
        const parsedTimestamp = slackTsMatch
            ? new Date(Number(slackTsMatch[1]) * 1000)
            : new Date(timestamp)

        if (isNaN(parsedTimestamp.getTime())) {
            return timestamp
        }

        return new Intl.DateTimeFormat('en-US', {
            month: 'short',
            day: 'numeric',
            year: 'numeric',
            hour: 'numeric',
            minute: '2-digit',
            hour12: true,
            timeZone: 'UTC',
        }).format(parsedTimestamp)
    }

    const openTicketModal = (ticketId: string) => {
        setSelectedTicketId(ticketId)
        setIsTicketModalOpen(true)
    }

    const closeSettingsAndRun = (action: () => void) => {
        restoreFocusOnCloseRef.current = false
        setIsSettingsOpen(false)
        action()
    }

    const handleTicketModalSuccess = () => {
        if (selectedTicketId) {
            queryClient.invalidateQueries({ queryKey: ['ticket', selectedTicketId] })
        }
        queryClient.invalidateQueries({ queryKey: ['tickets'] })
        queryClient.invalidateQueries({ queryKey: ['analysis'] })
    }

    // Stop polling
    const stopPolling = () => {
        if (pollingIntervalRef.current) {
            clearInterval(pollingIntervalRef.current)
            pollingIntervalRef.current = null
        }
    }

    // Fetch analysis status
    const fetchAnalysisStatus = async () => {
        try {
            const response = await apiFetch('/api/analysis/status')
            if (response.ok) {
                const status: AnalysisStatus = await response.json()
                setAnalysisStatus(status)
                return status
            }
            console.error('Analysis status request failed:', response.status)
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

            // If status is null (e.g., 401 error), stop polling
            if (!status) {
                stopPolling()
                return
            }

            if (!status.running) {
                stopPolling()
                isCompletedRef.current = true

                // Show completion message in the progress panel
                if (status.error) {
                    setIsCompletionError(true)
                    setCompletedMessage(`Analysis failed: ${status.error}`)
                    setShowCompletedStatus(true)

                    // Show error toast and hide panel after 5 seconds
                    completionTimeoutRef.current = setTimeout(() => {
                        setShowCompletedStatus(false)
                        setAnalysisStatus(null)
                        isCompletedRef.current = false
                        showToast(`Analysis failed: ${status.error}`, 'error')
                    }, 5000)
                } else {
                    setIsCompletionError(false)
                    const exported = status.exportedCount ?? 0
                        const message = exported === 0
                            ? 'All threads are up to date'
                            : `Analysis complete! ${status.analyzedCount || 0} of ${exported} threads analysed`
                    setCompletedMessage(message)
                    setShowCompletedStatus(true)

                    // Refresh data immediately, then hide panel after 5 seconds
                    queryClient.invalidateQueries({ queryKey: ['analysis'] })
                    completionTimeoutRef.current = setTimeout(() => {
                        setShowCompletedStatus(false)
                        setAnalysisStatus(null)
                        isCompletedRef.current = false
                    }, 5000)
                }
            }
        }, 3000)
    }

    // Fetch analysis enabled status
    useEffect(() => {
        const fetchAnalysisEnabled = async () => {
            try {
                const response = await apiFetch('/api/analysis/enabled')
                if (response.ok) {
                    const data = await response.json()
                    setIsAnalysisEnabled(data.enabled)
                } else {
                    console.error('Failed to check analysis enabled status:', response.status)
                }
            } catch (error) {
                console.error('Error fetching analysis enabled status:', error)
            }
        }

        fetchAnalysisEnabled()
    }, [])

    // Check status on mount and when page becomes visible
    // Only fetch if user has SUPPORT_ENGINEER role (required by backend) AND analysis is enabled
    useEffect(() => {
        if (isSupportEngineer && isAnalysisEnabled) {
            fetchAnalysisStatus()
        }

        return () => {
            stopPolling()
            if (completionTimeoutRef.current) {
                clearTimeout(completionTimeoutRef.current)
            }
        }
    }, [isSupportEngineer, isAnalysisEnabled])

    // Start polling if analysis is running
    useEffect(() => {
        if (analysisStatus?.running) {
            startPolling()
        } else {
            stopPolling()
        }
        // startPolling and stopPolling are stable functions that don't need to be in deps
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [analysisStatus?.running])

    useEffect(() => {
        if (isSettingsOpen) {
            queryWindowSelectRef.current?.focus()
        } else if (wasSettingsOpenRef.current && restoreFocusOnCloseRef.current) {
            settingsTriggerRef.current?.focus()
            restoreFocusOnCloseRef.current = false
        }

        wasSettingsOpenRef.current = isSettingsOpen
    }, [isSettingsOpen])

    useEffect(() => {
        if (!isSettingsOpen) {
            return
        }

        const handlePointerDown = (event: MouseEvent) => {
            if (!settingsContainerRef.current?.contains(event.target as Node)) {
                restoreFocusOnCloseRef.current = true
                setIsSettingsOpen(false)
            }
        }

        const handleKeyDown = (event: KeyboardEvent) => {
            if (event.key === 'Escape') {
                restoreFocusOnCloseRef.current = true
                setIsSettingsOpen(false)
            }
        }

        document.addEventListener('pointerdown', handlePointerDown)
        document.addEventListener('keydown', handleKeyDown)

        return () => {
            document.removeEventListener('pointerdown', handlePointerDown)
            document.removeEventListener('keydown', handleKeyDown)
        }
    }, [isSettingsOpen])

    const handleStartAnalysis = async () => {
        closeSettingsAndRun(() => setIsStartingAnalysis(true))
        try {
            const response = await apiFetch(`/api/analysis/run?days=${selectedDays}`, {
                method: 'POST',
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

                // If status is null (e.g., 401 error), don't start polling
                if (!status) {
                    return
                }

                // Check if analysis completed immediately (nothing to analyze)
                if (!status.running) {
                    isCompletedRef.current = true

                    // Show completion message
                    if (status.error) {
                        setIsCompletionError(true)
                        setCompletedMessage(`Analysis failed: ${status.error}`)
                        setShowCompletedStatus(true)

                        completionTimeoutRef.current = setTimeout(() => {
                            setShowCompletedStatus(false)
                            setAnalysisStatus(null)
                            isCompletedRef.current = false
                            showToast(`Analysis failed: ${status.error}`, 'error')
                        }, 5000)
                    } else {
                        setIsCompletionError(false)
                        const exported = status.exportedCount ?? 0
                        const message = exported === 0
                            ? 'All threads are up to date'
                            : `Analysis complete! ${status.analyzedCount || 0} of ${exported} threads analysed`
                        setCompletedMessage(message)
                        setShowCompletedStatus(true)

                        // Refresh data immediately, then hide panel after 5 seconds
                        queryClient.invalidateQueries({ queryKey: ['analysis'] })
                        completionTimeoutRef.current = setTimeout(() => {
                            setShowCompletedStatus(false)
                            setAnalysisStatus(null)
                            isCompletedRef.current = false
                        }, 5000)
                    }
                } else {
                    // Analysis is still running, start polling
                    startPolling()
                }
            } else if (response.status === 409) {
                showToast('Analysis was just started by someone else', 'error')
            } else {
                showToast('Failed to start analysis.. Please try again.', 'error')
            }
        } catch (error) {
            console.error('Error starting analysis:', error)
            showToast('Failed to start analysis.. Please try again.', 'error')
        } finally {
            setIsStartingAnalysis(false)
        }
    }

    const getItemExpansionKey = (scope: 'support-area' | 'knowledge-gap', itemName: string) => `${scope}:${itemName}`

    const toggleItemExpansion = (itemKey: string) => {
        setExpandedItems(prev => {
            const next = new Set(prev)
            if (next.has(itemKey)) {
                next.delete(itemKey)
            } else {
                next.add(itemKey)
            }
            return next
        })
    }

    const handleExportDownload = async () => {
        setIsDownloading(true)
        try {
            const response = await apiFetch(`/api/summary-data/export?days=${selectedDays}`)
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
            const response = await apiFetch('/api/summary-data/analysis')

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
            const formData = new FormData()
            formData.append('file', file)

            const response = await apiFetch('/api/summary-data/import', {
                method: 'POST',
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
            border: 'border-blue-100',
            volumeBar: 'bg-blue-400',
            volumeTrack: 'bg-blue-100',
        },
        amber: {
            badge: 'bg-amber-100 text-amber-700',
            countPill: 'bg-amber-100 text-amber-700',
            queryBg: 'bg-amber-50 hover:bg-amber-100',
            queryAccent: 'border-l-amber-400',
            border: 'border-amber-100',
            volumeBar: 'bg-amber-400',
            volumeTrack: 'bg-amber-100',
        },
    }

    const renderQueryRow = (query: QuerySummary, qIndex: number, colors: typeof themeClasses.blue) => (
        <button
            key={qIndex}
            type="button"
            aria-label={`View ticket ${query.ticketId}`}
            onClick={() => openTicketModal(query.ticketId)}
            className={`flex items-center justify-between gap-3 p-3.5 rounded-lg border-l-4 ${colors.queryAccent} ${colors.queryBg} transition-all duration-150 cursor-pointer hover:brightness-95 text-left`}
        >
            <p className="flex-1 min-w-0 text-sm font-medium text-gray-800 leading-relaxed">{query.text}</p>
            <span className="shrink-0 text-xs text-gray-400 whitespace-nowrap">{formatQueryTimestamp(query.timestamp)}</span>
        </button>
    )

    const renderAreaItem = (
        item: DimensionSummary,
        index: number,
        scope: 'support-area' | 'knowledge-gap',
        theme: ColorTheme = 'blue',
        maxQueryCount: number = 1
    ) => {
        const itemKey = getItemExpansionKey(scope, item.name)
        const isExpanded = expandedItems.has(itemKey)
        const colors = themeClasses[theme]
        const volumePercent = Math.round((item.queryCount / maxQueryCount) * 100)
        const contentId = `${itemKey}-queries`

        return (
            <div key={itemKey} className="border border-gray-200 rounded-xl bg-white hover:shadow-lg hover:-translate-y-0.5 transition-all duration-200">
                <div className="p-5">
                    <button
                        type="button"
                        onClick={() => toggleItemExpansion(itemKey)}
                        aria-expanded={isExpanded}
                        aria-controls={contentId}
                        className="w-full text-left"
                    >
                        <div className="flex items-center gap-4">
                            <div className={`flex items-center justify-center w-10 h-10 rounded-xl ${colors.badge} font-bold text-lg shrink-0`}>
                                {index + 1}
                            </div>
                            <div className="flex-1 min-w-0">
                                <div className="flex items-center justify-between">
                                    <h3 className="font-semibold text-gray-900 text-lg truncate">{item.name}</h3>
                                    <div className="flex items-center gap-3 shrink-0 ml-3">
                                        <span className={`inline-flex items-center px-2.5 py-1 rounded-full text-xs font-semibold ${colors.countPill}`}>
                                            {item.queryCount.toLocaleString()} {item.queryCount === 1 ? 'total query' : 'total queries'}
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
                    </button>

                    {isExpanded && (
                        <div id={contentId} className={`mt-5 pt-5 border-t ${colors.border}`}>
                            <p className="mb-3 text-xs text-gray-400">Up to 5 most recent queries</p>
                            <div className="space-y-2">
                                {item.queries.map((query, qIndex) => renderQueryRow(query, qIndex, colors))}
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
                        <div ref={settingsContainerRef} className="relative">
                            {!isAnalysisEnabled && (
                                <input
                                    ref={fileInputRef}
                                    type="file"
                                    accept=".jsonl"
                                    onChange={handleFileChange}
                                    className="hidden"
                                />
                            )}
                            <button
                                ref={settingsTriggerRef}
                                type="button"
                                onClick={() => {
                                    restoreFocusOnCloseRef.current = false
                                    setIsSettingsOpen(current => !current)
                                }}
                                disabled={isAnalysisEnabled && (analysisStatus?.running || isStartingAnalysis || showCompletedStatus)}
                                aria-haspopup="dialog"
                                aria-expanded={isSettingsOpen}
                                aria-controls={settingsPanelId}
                                className="h-10 inline-flex items-center gap-2 px-4 text-sm font-medium rounded-xl bg-purple-600 text-white hover:bg-purple-700 disabled:opacity-50 disabled:cursor-not-allowed transition-all"
                            >
                                <Play className="w-4 h-4" />
                                {isAnalysisEnabled && isStartingAnalysis ? 'Checking...' : 'Run Analysis'}
                            </button>
                            {isSettingsOpen && (
                                <div
                                    id={settingsPanelId}
                                    role="dialog"
                                    aria-modal="false"
                                    aria-labelledby={settingsTitleId}
                                    aria-describedby={settingsDescriptionId}
                                    className="absolute right-0 mt-2 w-64 rounded-2xl border border-gray-200 bg-white p-4 shadow-xl z-10"
                                >
                                    <div className="space-y-4">
                                        <div>
                                            <h2 id={settingsTitleId} className="text-sm font-semibold text-gray-900">Analysis settings</h2>
                                            <p id={settingsDescriptionId} className="mt-1 text-sm text-gray-600">
                                                Choose how far back to pull queries for this run.
                                            </p>
                                        </div>
                                        <div className="space-y-2">
                                            <label htmlFor="query-window" className="text-sm font-medium text-gray-700">
                                                Query window
                                            </label>
                                            <select
                                                ref={queryWindowSelectRef}
                                                id="query-window"
                                                aria-label="Query window"
                                                value={selectedDays}
                                                onChange={(e) => setSelectedDays(Number(e.target.value))}
                                                className="w-full h-10 px-3 border border-gray-200 rounded-xl bg-white text-sm text-gray-700 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                                            >
                                                <option value={7}>Week</option>
                                                <option value={31}>Month</option>
                                                <option value={92}>Quarter</option>
                                            </select>
                                        </div>
                                        {isAnalysisEnabled ? (
                                            <button
                                                type="button"
                                                onClick={handleStartAnalysis}
                                                disabled={analysisStatus?.running || isStartingAnalysis || showCompletedStatus}
                                                className="w-full h-10 flex items-center justify-center gap-2 px-4 text-sm font-medium rounded-xl bg-purple-600 text-white hover:bg-purple-700 disabled:opacity-50 disabled:cursor-not-allowed transition-all"
                                            >
                                                <Play className="w-4 h-4" />
                                                {isStartingAnalysis ? 'Checking...' : 'Run Analysis'}
                                            </button>
                                        ) : (
                                            <div className="flex flex-col gap-2">
                                                <button
                                                    type="button"
                                                    onClick={() => closeSettingsAndRun(handleExportDownload)}
                                                    disabled={isDownloading}
                                                    className="h-10 flex items-center gap-2 px-4 text-sm font-medium border border-gray-200 rounded-xl bg-white text-gray-700 hover:bg-gray-50 hover:border-gray-300 disabled:opacity-50 disabled:cursor-not-allowed transition-all"
                                                >
                                                    <Download className="w-4 h-4" />
                                                    {isDownloading ? 'Downloading...' : 'Export'}
                                                </button>
                                                <button
                                                    type="button"
                                                    onClick={() => closeSettingsAndRun(handleAnalysisBundleDownload)}
                                                    className="h-10 flex items-center gap-2 px-4 text-sm font-medium border border-gray-200 rounded-xl bg-white text-gray-700 hover:bg-gray-50 hover:border-gray-300 transition-all"
                                                >
                                                    <FileText className="w-4 h-4" />
                                                    Analysis Bundle
                                                </button>
                                                <button
                                                    type="button"
                                                    onClick={() => closeSettingsAndRun(handleImportClick)}
                                                    disabled={isUploading}
                                                    className="h-10 flex items-center gap-2 px-4 text-sm font-medium rounded-xl bg-gray-900 text-white hover:bg-gray-800 disabled:opacity-50 disabled:cursor-not-allowed transition-all"
                                                >
                                                    <Upload className="w-4 h-4" />
                                                    {isUploading ? 'Uploading...' : 'Import'}
                                                </button>
                                            </div>
                                        )}
                                    </div>
                                </div>
                            )}
                        </div>
                    )}
                </div>

                {/* Analysis Progress Card — visible to all users */}
                {(analysisStatus?.running || showCompletedStatus) && (() => {
                    const isUpToDate = showCompletedStatus && !isCompletionError && !analysisStatus?.exportedCount
                    const isSuccess = showCompletedStatus && !isCompletionError && (analysisStatus?.exportedCount ?? 0) > 0
                    const isError = showCompletedStatus && isCompletionError
                    const isRunning = !showCompletedStatus

                    const exported = analysisStatus?.exportedCount ?? 0
                    const analyzed = analysisStatus?.analyzedCount ?? 0
                    const progressPercent = exported > 0 ? Math.round((analyzed / exported) * 100) : 0

                    const borderColor = isError ? 'border-red-200' : isUpToDate ? 'border-emerald-200' : isSuccess ? 'border-green-200' : 'border-blue-200'
                    const bgGradient = isError
                        ? 'from-red-50 to-white'
                        : isUpToDate
                            ? 'from-emerald-50 to-white'
                            : isSuccess
                                ? 'from-green-50 to-white'
                                : 'from-purple-50 to-white'
                    const barColor = isError ? 'bg-red-500' : isSuccess ? 'bg-green-500' : isUpToDate ? 'bg-emerald-500' : 'bg-purple-500'
                    const barTrack = isError ? 'bg-red-100' : isSuccess ? 'bg-green-100' : isUpToDate ? 'bg-emerald-100' : 'bg-purple-100'

                    return (
                        <div className={`bg-white rounded-xl border ${borderColor} shadow-sm overflow-hidden`}>
                            <div className={`px-6 py-5 bg-gradient-to-r ${bgGradient}`}>
                                <div className="flex items-center justify-between">
                                    <div className="flex items-center gap-3">
                                        {isRunning && (
                                            <div className="inline-block animate-spin rounded-full h-5 w-5 border-b-2 border-purple-600 shrink-0"></div>
                                        )}
                                        {isUpToDate && <ShieldCheck className="w-5 h-5 text-emerald-600 shrink-0" />}
                                        {isSuccess && <CheckCircle2 className="w-5 h-5 text-green-600 shrink-0" />}
                                        {isError && <AlertCircle className="w-5 h-5 text-red-600 shrink-0" />}
                                        <span className="font-semibold text-gray-900">
                                            {showCompletedStatus
                                                ? completedMessage
                                                : exported > 0
                                                    ? `Analysing threads... ${analyzed} of ${exported} complete`
                                                    : 'Checking for new threads to analyse...'
                                            }
                                        </span>
                                    </div>
                                    {isRunning && exported > 0 && (
                                        <span className="text-sm font-medium text-purple-700">{progressPercent}%</span>
                                    )}
                                </div>
                                {(isRunning || isSuccess) && exported > 0 && (
                                    <div className={`mt-3 h-2.5 rounded-full ${barTrack} overflow-hidden`}>
                                        <div
                                            className={`h-full rounded-full ${barColor} transition-all duration-500 ease-out`}
                                            style={{ width: `${isSuccess ? 100 : progressPercent}%` }}
                                        />
                                    </div>
                                )}
                            </div>
                        </div>
                    )
                })()}

                {/* Top Support Areas */}
                <div className="bg-white rounded-xl border border-gray-200 shadow-sm overflow-hidden">
                    <button
                        onClick={() => setSupportAreasExpanded(!supportAreasExpanded)}
                        aria-expanded={supportAreasExpanded}
                        aria-controls="support-areas-section"
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
                        <div id="support-areas-section" className="px-6 pb-6 pt-2 space-y-3">
                            {(() => {
                                const items = showAllSupportAreas ? analysisData.supportAreas : analysisData.supportAreas.slice(0, 5)
                                const maxCount = items[0]?.queryCount ?? 1
                                return items.map((item, index) => renderAreaItem(item, index, 'support-area', 'blue', maxCount))
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
                        aria-expanded={knowledgeGapsExpanded}
                        aria-controls="knowledge-gaps-section"
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
                        <div id="knowledge-gaps-section" className="px-6 pb-6 pt-2 space-y-3">
                            {(() => {
                                const items = showAllKnowledgeGaps ? analysisData.knowledgeGaps : analysisData.knowledgeGaps.slice(0, 5)
                                const maxCount = items[0]?.queryCount ?? 1
                                return items.map((item, index) => renderAreaItem(item, index, 'knowledge-gap', 'amber', maxCount))
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

                <EditTicketModal
                    ticketId={selectedTicketId}
                    open={isTicketModalOpen}
                    onOpenChange={(open) => {
                        setIsTicketModalOpen(open)
                        if (!open) {
                            setSelectedTicketId(null)
                        }
                    }}
                    onSuccess={handleTicketModalSuccess}
                />
            </div>
        </div>
    )
}
