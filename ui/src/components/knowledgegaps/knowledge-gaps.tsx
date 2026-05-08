'use client'

import React, { useState, useRef, useEffect, useId } from 'react'
import { ChevronDown, BarChart3, Download, Upload, FileText, Play, CheckCircle2, AlertCircle, ShieldCheck } from 'lucide-react'
import { Button } from '@/components/ui/button'
import {
 Select,
 SelectContent,
 SelectItem,
 SelectTrigger,
 SelectValue,
} from '@/components/ui/select'
import { Label } from '@/components/ui/label'
import { useQueryClient } from '@tanstack/react-query'
import { useAnalysis, apiFetch } from '@/lib/hooks'
import { toast } from 'sonner'
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
 const restoreFocusOnCloseRef = useRef(false)
 const wasSettingsOpenRef = useRef(false)
 const settingsTitleId = useId()
 const settingsDescriptionId = useId()
 const settingsPanelId = 'analysis-settings-popover'

 const formatQueryTimestamp = (timestamp: string): string => {
 const parsed = new Date(timestamp)

 if (isNaN(parsed.getTime())) {
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
 }).format(parsed)
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
 toast.error(`Analysis failed: ${status.error}`)
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
 if (!isSettingsOpen && wasSettingsOpenRef.current && restoreFocusOnCloseRef.current) {
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
 toast.error(`Analysis failed: ${status.error}`)
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
 toast.error('Analysis was just started by someone else')
 } else {
 toast.error('Failed to start analysis.. Please try again.')
 }
 } catch (error) {
 console.error('Error starting analysis:', error)
 toast.error('Failed to start analysis.. Please try again.')
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
 toast.error('Failed to download export. Please try again.')
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
 toast.error('Failed to download prompt. Please try again.')
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
 toast.success(`Import successful! ${result.recordsImported} records imported.`)

 // Invalidate and refetch the analysis data
 await queryClient.invalidateQueries({ queryKey: ['analysis'] })

 // Reset the file input
 if (fileInputRef.current) {
 fileInputRef.current.value = ''
 }
 } catch (error) {
 console.error('Error uploading file:', error)
 toast.error('Failed to upload file. Please try again.')
 } finally {
 setIsUploading(false)
 }
 }

 type ColorTheme = 'blue' | 'amber'

 const themeClasses = {
 blue: {
 badge: 'bg-primary/10 text-primary',
 countPill: 'bg-primary/10 text-primary',
 queryBg: 'bg-primary/10 hover:bg-primary/20',
 queryAccent: 'border-l-primary',
 border: 'border-primary/30',
 volumeBar: 'bg-primary',
 volumeTrack: 'bg-primary/10',
 },
 amber: {
 badge: 'bg-warning/10 text-warning',
 countPill: 'bg-warning/10 text-warning',
 queryBg: 'bg-warning/10 hover:bg-warning/20',
 queryAccent: 'border-l-warning',
 border: 'border-warning/30',
 volumeBar: 'bg-warning',
 volumeTrack: 'bg-warning/10',
 },
 }

 const renderQueryRow = (query: QuerySummary, qIndex: number, _colors: typeof themeClasses.blue) => (
 <button
 key={qIndex}
 type="button"
 aria-label={`View ticket ${query.ticketId}`}
 onClick={() => openTicketModal(query.ticketId)}
 className="flex w-full items-center justify-between gap-3 rounded-md border bg-muted/40 px-3 py-2 text-left transition-colors hover:bg-muted cursor-pointer"
 >
 <p className="flex-1 min-w-0 text-sm text-foreground">{query.text}</p>
 <span className="shrink-0 text-xs text-muted-foreground whitespace-nowrap">{formatQueryTimestamp(query.timestamp)}</span>
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
 <div key={itemKey} className="rounded-md border bg-card transition-colors hover:bg-muted/40">
 <div className="px-4 py-3">
 <button
 type="button"
 onClick={() => toggleItemExpansion(itemKey)}
 aria-expanded={isExpanded}
 aria-controls={contentId}
 className="w-full text-left cursor-pointer"
 >
 <div className="flex items-center gap-3">
 <div className={`flex h-7 w-7 shrink-0 items-center justify-center rounded-md ${colors.badge} font-mono text-xs font-semibold tabular-nums`}>
 {index + 1}
 </div>
 <div className="flex-1 min-w-0">
 <div className="flex items-center justify-between gap-3">
 <h3 className="text-sm font-medium text-foreground truncate">{item.name}</h3>
 <div className="flex items-center gap-2 shrink-0">
 <span className="inline-flex items-center rounded-full border px-2 py-0.5 text-xs text-muted-foreground tabular-nums">
 {item.queryCount.toLocaleString()} {item.queryCount === 1 ? 'query' : 'queries'}
 </span>
 <ChevronDown
 className={`w-4 h-4 text-muted-foreground transition-transform duration-[400ms] ${isExpanded ? '' : '-rotate-90'}`}
 />
 </div>
 </div>
 <div className={`mt-2 h-1.5 rounded-full ${colors.volumeTrack} overflow-hidden`}>
 <div
 className={`h-full rounded-full ${colors.volumeBar} transition-all duration-500 ease-out`}
 style={{ width: `${volumePercent}%` }}
 />
 </div>
 </div>
 </div>
 </button>

 {isExpanded && (
 <div id={contentId} className="mt-3 pt-3 border-t animate-in fade-in slide-in-from-top-1 duration-[400ms]">
 <p className="mb-2 text-xs text-muted-foreground">Up to 5 most recent queries</p>
 <div className="space-y-1.5">
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
 <div className="inline-block animate-spin rounded-full h-12 w-12 border-b-2 border-primary mb-4"></div>
 <p className="text-muted-foreground">Loading support area summary...</p>
 </div>
 </div>
 )
 }

 if (error) {
 return (
 <div className="flex items-center justify-center h-full">
 <div className="text-center">
 <p className="text-destructive">Error loading analysis data</p>
 <p className="text-muted-foreground text-sm mt-2">Please try again later</p>
 </div>
 </div>
 )
 }

 if (!analysisData) return null

 return (
 <div className="space-y-6">
 {/* Header */}
 <div className="flex items-start justify-between gap-4">
 <div>
 <h1 className="text-2xl font-bold text-foreground">Support Area Summary</h1>
 <p className="text-muted-foreground text-sm">
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
 className="inline-flex h-9 items-center gap-2 rounded-md bg-primary px-4 text-sm font-medium text-primary-foreground hover:bg-primary/90 disabled:opacity-50 disabled:cursor-not-allowed transition-colors cursor-pointer"
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
 className="absolute right-0 mt-2 w-72 z-10 rounded-md border bg-popover p-4 shadow-md text-popover-foreground animate-in fade-in slide-in-from-top-1 duration-[200ms]"
 >
 <div className="space-y-4">
 <div>
 <h2 id={settingsTitleId} className="text-sm font-semibold text-foreground">Analysis settings</h2>
 <p id={settingsDescriptionId} className="mt-1 text-xs text-muted-foreground">
 Choose how far back to pull queries for this run.
 </p>
 </div>
 <div className="space-y-1.5">
 <Label htmlFor="query-window" className="text-sm font-medium text-foreground">
 Query window
 </Label>
 <Select value={String(selectedDays)} onValueChange={(v) => setSelectedDays(Number(v))}>
 <SelectTrigger id="query-window" className="w-full">
 <SelectValue />
 </SelectTrigger>
 <SelectContent>
 <SelectItem value="7">Week</SelectItem>
 <SelectItem value="31">Month</SelectItem>
 <SelectItem value="92">Quarter</SelectItem>
 </SelectContent>
 </Select>
 </div>
 {isAnalysisEnabled ? (
 <Button
 type="button"
 onClick={handleStartAnalysis}
 disabled={analysisStatus?.running || isStartingAnalysis || showCompletedStatus}
 className="w-full"
 >
 <Play className="h-4 w-4" />
 {isStartingAnalysis ? 'Checking...' : 'Run Analysis'}
 </Button>
 ) : (
 <div className="flex flex-col gap-2">
 <Button
 variant="outline"
 type="button"
 onClick={() => closeSettingsAndRun(handleExportDownload)}
 disabled={isDownloading}
 className="w-full justify-start"
 >
 <Download className="h-4 w-4" />
 {isDownloading ? 'Downloading...' : 'Export'}
 </Button>
 <Button
 variant="outline"
 type="button"
 onClick={() => closeSettingsAndRun(handleAnalysisBundleDownload)}
 className="w-full justify-start"
 >
 <FileText className="h-4 w-4" />
 Analysis Bundle
 </Button>
 <Button
 variant="outline"
 type="button"
 onClick={() => closeSettingsAndRun(handleImportClick)}
 disabled={isUploading}
 className="w-full justify-start"
 >
 <Upload className="h-4 w-4" />
 {isUploading ? 'Uploading...' : 'Import'}
 </Button>
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

 const borderColor = isError ? 'border-destructive/30' : isUpToDate ? 'border-success/30' : isSuccess ? 'border-success/30' : 'border-primary/30'
 const bgGradient = isError
 ? ' to-white'
 : isUpToDate
 ? ' to-white'
 : isSuccess
 ? ' to-white'
 : ' to-white'
 const barColor = isError ? 'bg-destructive' : isSuccess ? 'bg-success' : isUpToDate ? 'bg-success' : 'bg-secondary'
 const barTrack = isError ? 'bg-destructive/10' : isSuccess ? 'bg-success/10' : isUpToDate ? 'bg-success/10' : 'bg-muted'

 return (
 <div className={`bg-card rounded-xl border ${borderColor} shadow-sm overflow-hidden`}>
 <div className={`px-6 py-5 ${bgGradient}`}>
 <div className="flex items-center justify-between">
 <div className="flex items-center gap-3">
 {isRunning && (
 <div className="inline-block animate-spin rounded-full h-5 w-5 border-b-2 border-secondary shrink-0"></div>
 )}
 {isUpToDate && <ShieldCheck className="w-5 h-5 text-success shrink-0" />}
 {isSuccess && <CheckCircle2 className="w-5 h-5 text-success shrink-0" />}
 {isError && <AlertCircle className="w-5 h-5 text-destructive shrink-0" />}
 <span className="font-semibold text-foreground">
 {showCompletedStatus
 ? completedMessage
 : exported > 0
 ? `Analysing threads... ${analyzed} of ${exported} complete`
 : 'Checking for new threads to analyse...'
 }
 </span>
 </div>
 {isRunning && exported > 0 && (
 <span className="text-sm font-medium text-foreground">{progressPercent}%</span>
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
 <div className="rounded-xl border bg-card overflow-hidden">
 <button
 onClick={() => setSupportAreasExpanded(!supportAreasExpanded)}
 aria-expanded={supportAreasExpanded}
 aria-controls="support-areas-section"
 className="w-full px-6 py-4 flex items-center justify-between hover:bg-muted/40 transition-colors cursor-pointer"
 >
 <div className="flex items-center gap-3">
 <h2 className="text-base font-semibold text-foreground">Top Support Areas</h2>
 <span className="inline-flex items-center rounded-full border px-2 py-0.5 text-xs text-muted-foreground tabular-nums">{analysisData.supportAreas.length}</span>
 </div>
 <ChevronDown
 className={`w-4 h-4 text-muted-foreground transition-transform duration-[400ms] ${supportAreasExpanded ? '' : '-rotate-90'}`}
 />
 </button>

 {supportAreasExpanded && (
 <div id="support-areas-section" className="px-6 pb-6 pt-2 space-y-3 animate-in fade-in slide-in-from-top-1 duration-[400ms]">
 {(() => {
 const items = showAllSupportAreas ? analysisData.supportAreas : analysisData.supportAreas.slice(0, 5)
 const maxCount = items[0]?.queryCount ?? 1
 return items.map((item, index) => renderAreaItem(item, index, 'support-area', 'blue', maxCount))
 })()}
 {analysisData.supportAreas.length > 5 && (
 <button
 onClick={() => setShowAllSupportAreas(!showAllSupportAreas)}
 className="w-full py-2.5 text-sm text-primary hover:text-primary font-semibold hover:bg-info/10 rounded-lg transition-colors cursor-pointer"
 >
 {showAllSupportAreas ? 'Show less' : `Show all ${analysisData.supportAreas.length} areas`}
 </button>
 )}
 </div>
 )}
 </div>

 {/* Top 5 Knowledge Gaps */}
 <div className="rounded-xl border bg-card overflow-hidden">
 <button
 onClick={() => setKnowledgeGapsExpanded(!knowledgeGapsExpanded)}
 aria-expanded={knowledgeGapsExpanded}
 aria-controls="knowledge-gaps-section"
 className="w-full px-6 py-4 flex items-center justify-between hover:bg-muted/40 transition-colors cursor-pointer"
 >
 <div className="flex items-center gap-3">
 <h2 className="text-base font-semibold text-foreground">Top Knowledge Gaps</h2>
 <span className="inline-flex items-center rounded-full border px-2 py-0.5 text-xs text-muted-foreground tabular-nums">{analysisData.knowledgeGaps.length}</span>
 </div>
 <ChevronDown
 className={`w-4 h-4 text-muted-foreground transition-transform duration-[400ms] ${knowledgeGapsExpanded ? '' : '-rotate-90'}`}
 />
 </button>

 {knowledgeGapsExpanded && (
 <div id="knowledge-gaps-section" className="px-6 pb-6 pt-2 space-y-3 animate-in fade-in slide-in-from-top-1 duration-[400ms]">
 {(() => {
 const items = showAllKnowledgeGaps ? analysisData.knowledgeGaps : analysisData.knowledgeGaps.slice(0, 5)
 const maxCount = items[0]?.queryCount ?? 1
 return items.map((item, index) => renderAreaItem(item, index, 'knowledge-gap', 'amber', maxCount))
 })()}
 {analysisData.knowledgeGaps.length > 5 && (
 <button
 onClick={() => setShowAllKnowledgeGaps(!showAllKnowledgeGaps)}
 className="w-full py-2.5 text-sm text-warning hover:text-warning font-semibold hover:bg-warning/10 rounded-lg transition-colors cursor-pointer"
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
 )
}
