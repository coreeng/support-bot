"use client";

import EditTicketModal from "@/components/tickets/EditTicketModal";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { useAuth } from "@/hooks/useAuth";
import { apiFetch, useAnalysis } from "@/lib/hooks";
import type { DimensionSummary, QuerySummary } from "@/lib/types";
import { useQueryClient } from "@tanstack/react-query";
import { AlertCircle, CheckCircle2, ChevronDown, Download, FileText, Play, ShieldCheck, Upload } from "lucide-react";
import React, { useEffect, useId, useRef, useState } from "react";
import { toast } from "sonner";

interface AnalysisStatus {
  jobId: string | null;
  exportedCount: number | null;
  analyzedCount: number | null;
  running: boolean;
  error: string | null;
}

export default function KnowledgeGapsPage() {
  const queryClient = useQueryClient();
  const { isSupportEngineer } = useAuth();
  const { data: analysisData, isLoading, error } = useAnalysis();
  const [supportAreasExpanded, setSupportAreasExpanded] = useState(true);
  const [knowledgeGapsExpanded, setKnowledgeGapsExpanded] = useState(true);
  const [expandedItems, setExpandedItems] = useState<Set<string>>(new Set());
  const [showAllSupportAreas, setShowAllSupportAreas] = useState(false);
  const [showAllKnowledgeGaps, setShowAllKnowledgeGaps] = useState(false);
  const [isDownloading, setIsDownloading] = useState(false);
  const [isUploading, setIsUploading] = useState(false);
  const [selectedDays, setSelectedDays] = useState<number>(7);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [analysisStatus, setAnalysisStatus] = useState<AnalysisStatus | null>(null);
  const [isStartingAnalysis, setIsStartingAnalysis] = useState(false);
  const pollingIntervalRef = useRef<NodeJS.Timeout | null>(null);
  const completionTimeoutRef = useRef<NodeJS.Timeout | null>(null);
  const [showCompletedStatus, setShowCompletedStatus] = useState(false);
  const [completedMessage, setCompletedMessage] = useState<string>("");
  const [isCompletionError, setIsCompletionError] = useState(false);
  const [selectedTicketId, setSelectedTicketId] = useState<string | null>(null);
  const [isTicketModalOpen, setIsTicketModalOpen] = useState(false);
  const isCompletedRef = useRef(false);
  const [isAnalysisEnabled, setIsAnalysisEnabled] = useState(false);
  const [isSettingsOpen, setIsSettingsOpen] = useState(false);
  const settingsTitleId = useId();
  const settingsDescriptionId = useId();

  const formatQueryTimestamp = (timestamp: string): string => {
    const parsed = new Date(timestamp);

    if (isNaN(parsed.getTime())) {
      return timestamp;
    }

    return new Intl.DateTimeFormat("en-US", {
      month: "short",
      day: "numeric",
      year: "numeric",
      hour: "numeric",
      minute: "2-digit",
      hour12: true,
      timeZone: "UTC",
    }).format(parsed);
  };

  const openTicketModal = (ticketId: string) => {
    setSelectedTicketId(ticketId);
    setIsTicketModalOpen(true);
  };

  const closeSettingsAndRun = (action: () => void) => {
    setIsSettingsOpen(false);
    action();
  };

  const handleTicketModalSuccess = () => {
    if (selectedTicketId) {
      queryClient.invalidateQueries({ queryKey: ["ticket", selectedTicketId] });
    }
    queryClient.invalidateQueries({ queryKey: ["tickets"] });
    queryClient.invalidateQueries({ queryKey: ["analysis"] });
  };

  // Stop polling
  const stopPolling = () => {
    if (pollingIntervalRef.current) {
      clearInterval(pollingIntervalRef.current);
      pollingIntervalRef.current = null;
    }
  };

  // Fetch analysis status
  const fetchAnalysisStatus = async () => {
    try {
      const response = await apiFetch("/api/analysis/status");
      if (response.ok) {
        const status: AnalysisStatus = await response.json();
        setAnalysisStatus(status);
        return status;
      }
      console.error("Analysis status request failed:", response.status);
    } catch (error) {
      console.error("Error fetching analysis status:", error);
    }
    return null;
  };

  // Start polling for analysis status
  const startPolling = () => {
    if (pollingIntervalRef.current) return;

    pollingIntervalRef.current = setInterval(async () => {
      // Don't fetch status if we're already showing completion
      if (isCompletedRef.current) return;

      const status = await fetchAnalysisStatus();

      // If status is null (e.g., 401 error), stop polling
      if (!status) {
        stopPolling();
        return;
      }

      if (!status.running) {
        stopPolling();
        isCompletedRef.current = true;

        // Show completion message in the progress panel
        if (status.error) {
          setIsCompletionError(true);
          setCompletedMessage(`Analysis failed: ${status.error}`);
          setShowCompletedStatus(true);

          // Show error toast and hide panel after 5 seconds
          completionTimeoutRef.current = setTimeout(() => {
            setShowCompletedStatus(false);
            setAnalysisStatus(null);
            isCompletedRef.current = false;
            toast.error(`Analysis failed: ${status.error}`);
          }, 5000);
        } else {
          setIsCompletionError(false);
          const exported = status.exportedCount ?? 0;
          const message =
            exported === 0
              ? "All threads are up to date"
              : `Analysis complete! ${status.analyzedCount || 0} of ${exported} threads analysed`;
          setCompletedMessage(message);
          setShowCompletedStatus(true);

          // Refresh data immediately, then hide panel after 5 seconds
          queryClient.invalidateQueries({ queryKey: ["analysis"] });
          completionTimeoutRef.current = setTimeout(() => {
            setShowCompletedStatus(false);
            setAnalysisStatus(null);
            isCompletedRef.current = false;
          }, 5000);
        }
      }
    }, 3000);
  };

  // Fetch analysis enabled status
  useEffect(() => {
    const fetchAnalysisEnabled = async () => {
      try {
        const response = await apiFetch("/api/analysis/enabled");
        if (response.ok) {
          const data = await response.json();
          setIsAnalysisEnabled(data.enabled);
        } else {
          console.error("Failed to check analysis enabled status:", response.status);
        }
      } catch (error) {
        console.error("Error fetching analysis enabled status:", error);
      }
    };

    fetchAnalysisEnabled();
  }, []);

  // Check status on mount and when page becomes visible
  // Only fetch if user has SUPPORT_ENGINEER role (required by backend) AND analysis is enabled
  useEffect(() => {
    if (isSupportEngineer && isAnalysisEnabled) {
      fetchAnalysisStatus();
    }

    return () => {
      stopPolling();
      if (completionTimeoutRef.current) {
        clearTimeout(completionTimeoutRef.current);
      }
    };
  }, [isSupportEngineer, isAnalysisEnabled]);

  // Start polling if analysis is running
  useEffect(() => {
    if (analysisStatus?.running) {
      startPolling();
    } else {
      stopPolling();
    }
    // startPolling and stopPolling are stable functions that don't need to be in deps
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [analysisStatus?.running]);

  const handleStartAnalysis = async () => {
    closeSettingsAndRun(() => setIsStartingAnalysis(true));
    try {
      const response = await apiFetch(`/api/analysis/run?days=${selectedDays}`, {
        method: "POST",
      });

      if (response.status === 202) {
        // Analysis started successfully - show progress panel immediately
        setAnalysisStatus({
          jobId: null,
          exportedCount: 0,
          analyzedCount: 0,
          running: true,
          error: null,
        });

        // Fetch actual status
        const status = await fetchAnalysisStatus();

        // If status is null (e.g., 401 error), don't start polling
        if (!status) {
          return;
        }

        // Check if analysis completed immediately (nothing to analyze)
        if (!status.running) {
          isCompletedRef.current = true;

          // Show completion message
          if (status.error) {
            setIsCompletionError(true);
            setCompletedMessage(`Analysis failed: ${status.error}`);
            setShowCompletedStatus(true);

            completionTimeoutRef.current = setTimeout(() => {
              setShowCompletedStatus(false);
              setAnalysisStatus(null);
              isCompletedRef.current = false;
              toast.error(`Analysis failed: ${status.error}`);
            }, 5000);
          } else {
            setIsCompletionError(false);
            const exported = status.exportedCount ?? 0;
            const message =
              exported === 0
                ? "All threads are up to date"
                : `Analysis complete! ${status.analyzedCount || 0} of ${exported} threads analysed`;
            setCompletedMessage(message);
            setShowCompletedStatus(true);

            // Refresh data immediately, then hide panel after 5 seconds
            queryClient.invalidateQueries({ queryKey: ["analysis"] });
            completionTimeoutRef.current = setTimeout(() => {
              setShowCompletedStatus(false);
              setAnalysisStatus(null);
              isCompletedRef.current = false;
            }, 5000);
          }
        } else {
          // Analysis is still running, start polling
          startPolling();
        }
      } else if (response.status === 409) {
        toast.error("Analysis was just started by someone else");
      } else {
        toast.error("Failed to start analysis.. Please try again.");
      }
    } catch (error) {
      console.error("Error starting analysis:", error);
      toast.error("Failed to start analysis.. Please try again.");
    } finally {
      setIsStartingAnalysis(false);
    }
  };

  const getItemExpansionKey = (scope: "support-area" | "knowledge-gap", itemName: string) => `${scope}:${itemName}`;

  const toggleItemExpansion = (itemKey: string) => {
    setExpandedItems((prev) => {
      const next = new Set(prev);
      if (next.has(itemKey)) {
        next.delete(itemKey);
      } else {
        next.add(itemKey);
      }
      return next;
    });
  };

  const handleExportDownload = async () => {
    setIsDownloading(true);
    try {
      const response = await apiFetch(`/api/summary-data/export?days=${selectedDays}`);
      if (!response.ok) {
        throw new Error("Failed to download export");
      }

      const blob = await response.blob();
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = "content.zip";
      document.body.appendChild(a);
      a.click();
      window.URL.revokeObjectURL(url);
      document.body.removeChild(a);
    } catch (error) {
      console.error("Error downloading export:", error);
      toast.error("Failed to download export. Please try again.");
    } finally {
      setIsDownloading(false);
    }
  };

  const handleAnalysisBundleDownload = async () => {
    try {
      const response = await apiFetch("/api/summary-data/analysis");

      if (!response.ok) {
        throw new Error("Failed to download prompt");
      }

      const blob = await response.blob();
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = "analysis.zip";
      document.body.appendChild(a);
      a.click();
      window.URL.revokeObjectURL(url);
      document.body.removeChild(a);
    } catch (error) {
      console.error("Error downloading prompt:", error);
      toast.error("Failed to download prompt. Please try again.");
    }
  };

  const handleImportClick = () => {
    fileInputRef.current?.click();
  };

  const handleFileChange = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) return;

    setIsUploading(true);
    try {
      const formData = new FormData();
      formData.append("file", file);

      const response = await apiFetch("/api/summary-data/import", {
        method: "POST",
        body: formData,
      });

      if (!response.ok) {
        throw new Error("Failed to upload file");
      }

      const result = await response.json();
      toast.success(`Import successful! ${result.recordsImported} records imported.`);

      // Invalidate and refetch the analysis data
      await queryClient.invalidateQueries({ queryKey: ["analysis"] });

      // Reset the file input
      if (fileInputRef.current) {
        fileInputRef.current.value = "";
      }
    } catch (error) {
      console.error("Error uploading file:", error);
      toast.error("Failed to upload file. Please try again.");
    } finally {
      setIsUploading(false);
    }
  };

  type ColorTheme = "blue" | "amber";

  const themeClasses = {
    blue: {
      badge: "bg-primary/10 text-primary",
      countPill: "bg-primary/10 text-primary",
      queryBg: "bg-primary/10 hover:bg-primary/20",
      queryAccent: "border-l-primary",
      border: "border-primary/30",
      volumeBar: "bg-primary",
      volumeTrack: "bg-primary/10",
    },
    amber: {
      badge: "bg-warning/10 text-warning",
      countPill: "bg-warning/10 text-warning",
      queryBg: "bg-warning/10 hover:bg-warning/20",
      queryAccent: "border-l-warning",
      border: "border-warning/30",
      volumeBar: "bg-warning",
      volumeTrack: "bg-warning/10",
    },
  };

  const renderQueryRow = (query: QuerySummary, qIndex: number, _colors: typeof themeClasses.blue) => (
    <button
      key={qIndex}
      type="button"
      aria-label={`View ticket ${query.ticketId}`}
      onClick={() => openTicketModal(query.ticketId)}
      className="bg-muted/40 hover:bg-muted flex w-full cursor-pointer items-center justify-between gap-3 rounded-md border px-3 py-2 text-left transition-colors"
    >
      <p className="text-foreground min-w-0 flex-1 text-sm">{query.text}</p>
      <span className="text-muted-foreground shrink-0 text-xs whitespace-nowrap">{formatQueryTimestamp(query.timestamp)}</span>
    </button>
  );

  const renderAreaItem = (
    item: DimensionSummary,
    index: number,
    scope: "support-area" | "knowledge-gap",
    theme: ColorTheme = "blue",
    maxQueryCount: number = 1
  ) => {
    const itemKey = getItemExpansionKey(scope, item.name);
    const isExpanded = expandedItems.has(itemKey);
    const colors = themeClasses[theme];
    const volumePercent = Math.round((item.queryCount / maxQueryCount) * 100);
    const contentId = `${itemKey}-queries`;

    return (
      <div key={itemKey} className="bg-card hover:bg-muted/40 rounded-md border transition-colors">
        <div className="px-4 py-3">
          <button
            type="button"
            onClick={() => toggleItemExpansion(itemKey)}
            aria-expanded={isExpanded}
            aria-controls={contentId}
            className="w-full cursor-pointer text-left"
          >
            <div className="flex items-center gap-3">
              <div
                className={`flex h-7 w-7 shrink-0 items-center justify-center rounded-md ${colors.badge} font-mono text-xs font-semibold tabular-nums`}
              >
                {index + 1}
              </div>
              <div className="min-w-0 flex-1">
                <div className="flex items-center justify-between gap-3">
                  <h3 className="text-foreground truncate text-sm font-medium">{item.name}</h3>
                  <div className="flex shrink-0 items-center gap-2">
                    <span className="text-muted-foreground inline-flex items-center rounded-full border px-2 py-0.5 text-xs tabular-nums">
                      {item.queryCount.toLocaleString()} {item.queryCount === 1 ? "query" : "queries"}
                    </span>
                    <ChevronDown
                      className={`text-muted-foreground h-4 w-4 transition-transform duration-[400ms] ${isExpanded ? "" : "-rotate-90"}`}
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
            <div id={contentId} className="animate-in fade-in slide-in-from-top-1 mt-3 border-t pt-3 duration-[400ms]">
              <p className="text-muted-foreground mb-2 text-xs">Up to 5 most recent queries</p>
              <div className="space-y-1.5">{item.queries.map((query, qIndex) => renderQueryRow(query, qIndex, colors))}</div>
            </div>
          )}
        </div>
      </div>
    );
  };

  if (isLoading) {
    return (
      <div className="flex h-full items-center justify-center">
        <div className="text-center">
          <div className="border-primary mb-4 inline-block h-12 w-12 animate-spin rounded-full border-b-2"></div>
          <p className="text-muted-foreground">Loading support area summary...</p>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex h-full items-center justify-center">
        <div className="text-center">
          <p className="text-destructive">Error loading analysis data</p>
          <p className="text-muted-foreground mt-2 text-sm">Please try again later</p>
        </div>
      </div>
    );
  }

  if (!analysisData) return null;

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-foreground text-2xl font-bold">Support Area Summary</h1>
          <p className="text-muted-foreground text-sm">Overview of support areas and knowledge gaps requiring attention</p>
        </div>
        {isSupportEngineer && (
          <Popover open={isSettingsOpen} onOpenChange={setIsSettingsOpen}>
            {!isAnalysisEnabled && <input ref={fileInputRef} type="file" accept=".jsonl" onChange={handleFileChange} className="hidden" />}
            <PopoverTrigger asChild>
              <Button type="button" disabled={isAnalysisEnabled && (analysisStatus?.running || isStartingAnalysis || showCompletedStatus)}>
                <Play className="h-4 w-4" />
                {isAnalysisEnabled && isStartingAnalysis ? "Checking..." : "Run Analysis"}
              </Button>
            </PopoverTrigger>
            <PopoverContent
              align="end"
              role="dialog"
              aria-labelledby={settingsTitleId}
              aria-describedby={settingsDescriptionId}
              className="w-72"
            >
              <div className="space-y-4">
                <div>
                  <h2 id={settingsTitleId} className="text-foreground text-sm font-semibold">
                    Analysis settings
                  </h2>
                  <p id={settingsDescriptionId} className="text-muted-foreground mt-1 text-xs">
                    Choose how far back to pull queries for this run.
                  </p>
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="query-window" className="text-foreground text-sm font-medium">
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
                    {isStartingAnalysis ? "Checking..." : "Run Analysis"}
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
                      {isDownloading ? "Downloading..." : "Export"}
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
                      {isUploading ? "Uploading..." : "Import"}
                    </Button>
                  </div>
                )}
              </div>
            </PopoverContent>
          </Popover>
        )}
      </div>

      {/* Analysis Progress Card — visible to all users */}
      {(analysisStatus?.running || showCompletedStatus) &&
        (() => {
          const isUpToDate = showCompletedStatus && !isCompletionError && !analysisStatus?.exportedCount;
          const isSuccess = showCompletedStatus && !isCompletionError && (analysisStatus?.exportedCount ?? 0) > 0;
          const isError = showCompletedStatus && isCompletionError;
          const isRunning = !showCompletedStatus;

          const exported = analysisStatus?.exportedCount ?? 0;
          const analyzed = analysisStatus?.analyzedCount ?? 0;
          const progressPercent = exported > 0 ? Math.round((analyzed / exported) * 100) : 0;

          const borderColor = isError
            ? "border-destructive/30"
            : isUpToDate
              ? "border-success/30"
              : isSuccess
                ? "border-success/30"
                : "border-primary/30";
          const bgGradient = isError ? " to-white" : isUpToDate ? " to-white" : isSuccess ? " to-white" : " to-white";
          const barColor = isError ? "bg-destructive" : isSuccess ? "bg-success" : isUpToDate ? "bg-success" : "bg-secondary";
          const barTrack = isError ? "bg-destructive/10" : isSuccess ? "bg-success/10" : isUpToDate ? "bg-success/10" : "bg-muted";

          return (
            <div className={`bg-card rounded-xl border ${borderColor} overflow-hidden shadow-sm`}>
              <div className={`px-6 py-5 ${bgGradient}`}>
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-3">
                    {isRunning && (
                      <div className="border-secondary inline-block h-5 w-5 shrink-0 animate-spin rounded-full border-b-2"></div>
                    )}
                    {isUpToDate && <ShieldCheck className="text-success h-5 w-5 shrink-0" />}
                    {isSuccess && <CheckCircle2 className="text-success h-5 w-5 shrink-0" />}
                    {isError && <AlertCircle className="text-destructive h-5 w-5 shrink-0" />}
                    <span className="text-foreground font-semibold">
                      {showCompletedStatus
                        ? completedMessage
                        : exported > 0
                          ? `Analysing threads... ${analyzed} of ${exported} complete`
                          : "Checking for new threads to analyse..."}
                    </span>
                  </div>
                  {isRunning && exported > 0 && <span className="text-foreground text-sm font-medium">{progressPercent}%</span>}
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
          );
        })()}

      {/* Top Support Areas */}
      <div className="bg-card overflow-hidden rounded-xl border">
        <button
          onClick={() => setSupportAreasExpanded(!supportAreasExpanded)}
          aria-expanded={supportAreasExpanded}
          aria-controls="support-areas-section"
          className="hover:bg-muted/40 flex w-full cursor-pointer items-center justify-between px-6 py-4 transition-colors"
        >
          <div className="flex items-center gap-3">
            <h2 className="text-foreground text-base font-semibold">Top Support Areas</h2>
            <span className="text-muted-foreground inline-flex items-center rounded-full border px-2 py-0.5 text-xs tabular-nums">
              {analysisData.supportAreas.length}
            </span>
          </div>
          <ChevronDown
            className={`text-muted-foreground h-4 w-4 transition-transform duration-[400ms] ${supportAreasExpanded ? "" : "-rotate-90"}`}
          />
        </button>

        {supportAreasExpanded && (
          <div id="support-areas-section" className="animate-in fade-in slide-in-from-top-1 space-y-3 px-6 pt-2 pb-6 duration-[400ms]">
            {(() => {
              const items = showAllSupportAreas ? analysisData.supportAreas : analysisData.supportAreas.slice(0, 5);
              const maxCount = items[0]?.queryCount ?? 1;
              return items.map((item, index) => renderAreaItem(item, index, "support-area", "blue", maxCount));
            })()}
            {analysisData.supportAreas.length > 5 && (
              <button
                onClick={() => setShowAllSupportAreas(!showAllSupportAreas)}
                className="text-primary hover:text-primary hover:bg-info/10 w-full cursor-pointer rounded-lg py-2.5 text-sm font-semibold transition-colors"
              >
                {showAllSupportAreas ? "Show less" : `Show all ${analysisData.supportAreas.length} areas`}
              </button>
            )}
          </div>
        )}
      </div>

      {/* Top 5 Knowledge Gaps */}
      <div className="bg-card overflow-hidden rounded-xl border">
        <button
          onClick={() => setKnowledgeGapsExpanded(!knowledgeGapsExpanded)}
          aria-expanded={knowledgeGapsExpanded}
          aria-controls="knowledge-gaps-section"
          className="hover:bg-muted/40 flex w-full cursor-pointer items-center justify-between px-6 py-4 transition-colors"
        >
          <div className="flex items-center gap-3">
            <h2 className="text-foreground text-base font-semibold">Top Knowledge Gaps</h2>
            <span className="text-muted-foreground inline-flex items-center rounded-full border px-2 py-0.5 text-xs tabular-nums">
              {analysisData.knowledgeGaps.length}
            </span>
          </div>
          <ChevronDown
            className={`text-muted-foreground h-4 w-4 transition-transform duration-[400ms] ${knowledgeGapsExpanded ? "" : "-rotate-90"}`}
          />
        </button>

        {knowledgeGapsExpanded && (
          <div id="knowledge-gaps-section" className="animate-in fade-in slide-in-from-top-1 space-y-3 px-6 pt-2 pb-6 duration-[400ms]">
            {(() => {
              const items = showAllKnowledgeGaps ? analysisData.knowledgeGaps : analysisData.knowledgeGaps.slice(0, 5);
              const maxCount = items[0]?.queryCount ?? 1;
              return items.map((item, index) => renderAreaItem(item, index, "knowledge-gap", "amber", maxCount));
            })()}
            {analysisData.knowledgeGaps.length > 5 && (
              <button
                onClick={() => setShowAllKnowledgeGaps(!showAllKnowledgeGaps)}
                className="text-warning hover:text-warning hover:bg-warning/10 w-full cursor-pointer rounded-lg py-2.5 text-sm font-semibold transition-colors"
              >
                {showAllKnowledgeGaps ? "Show less" : `Show all ${analysisData.knowledgeGaps.length} gaps`}
              </button>
            )}
          </div>
        )}
      </div>

      <EditTicketModal
        ticketId={selectedTicketId}
        open={isTicketModalOpen}
        onOpenChange={(open) => {
          setIsTicketModalOpen(open);
          if (!open) {
            setSelectedTicketId(null);
          }
        }}
        onSuccess={handleTicketModalSuccess}
      />
    </div>
  );
}
