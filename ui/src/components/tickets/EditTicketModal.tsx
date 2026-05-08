'use client'

import { useState, useEffect } from 'react'
import { useTicket, useTenantTeams, useTeamSuggestions, useRegistry, useSupportMembers, useAssignmentEnabled } from '@/lib/hooks'
import { TicketWithLogs, TicketImpact, TicketTag, Escalation, SupportMember } from '@/lib/types'
import { useAuth } from '@/hooks/useAuth'
import {
    Dialog,
    DialogContent,
    DialogHeader,
    DialogTitle,
    DialogDescription,
    DialogFooter,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import {
    Select,
    SelectContent,
    SelectGroup,
    SelectItem,
    SelectLabel,
    SelectSeparator,
    SelectTrigger,
    SelectValue,
} from '@/components/ui/select'
import { Label } from '@/components/ui/label'
import { MultiSelect } from '@/components/ui/multi-select'
import { Skeleton } from '@/components/ui/skeleton'
import { Ticket, AlertCircle, Tag, User, Clock, Slack, X, MessageSquare } from 'lucide-react'
import SlackMessageRenderer from '@/components/ui/SlackMessageRenderer'

interface EditTicketModalProps {
    ticketId: string | null
    open: boolean
    onOpenChange: (open: boolean) => void
    onSuccess?: () => void
}

export default function EditTicketModal({
    ticketId,
    open,
    onOpenChange,
    onSuccess,
}: EditTicketModalProps) {
    const { isSupportEngineer } = useAuth()
    const { data: ticketDetails, isLoading: isTicketLoading, error: ticketError } = useTicket(ticketId || undefined)
    const { data: teamsData } = useTenantTeams()
    const { data: teamSuggestionsData, isError: isTeamSuggestionsError } = useTeamSuggestions(
        ticketId ? Number(ticketId) : undefined
    )
    const { data: registryData } = useRegistry()
    const { data: supportMembers } = useSupportMembers()
    const { data: isAssignmentEnabled } = useAssignmentEnabled()

    // Form state
    const [status, setStatus] = useState<string>('')
    const [impact, setImpact] = useState<string>('')
    const [selectedTags, setSelectedTags] = useState<string[]>([])
    const [authorTeam, setAuthorTeam] = useState<string>('')
    const [assignedTo, setAssignedTo] = useState<string>('')
    const [isSaving, setIsSaving] = useState(false)
    const [error, setError] = useState<string | null>(null)
    const [validationErrors, setValidationErrors] = useState<Record<string, string>>({})
    const [originalStatus, setOriginalStatus] = useState<string>('')
    const [showEscalationWarning, setShowEscalationWarning] = useState(false)

    const clearValidationError = (field: string) => {
        setValidationErrors(prev => {
            const next = { ...prev }
            delete next[field]
            return next
        })
    }

    // Initialize form when ticket data loads
    useEffect(() => {
        if (ticketDetails) {
            setStatus(ticketDetails.status || '')
            setOriginalStatus(ticketDetails.status || '')
            setImpact(ticketDetails.impact || '')
            setSelectedTags(ticketDetails.tags || [])
            setAuthorTeam(ticketDetails.team?.name || '')
            setAssignedTo(ticketDetails.assignedTo || '')
        }
    }, [ticketDetails])

    // Reset form when modal closes
    useEffect(() => {
        if (!open) {
            setError(null)
            setIsSaving(false)
            setValidationErrors({})
            setShowEscalationWarning(false)
        }
    }, [open])

    // Check for unresolved escalations when status changes to closed
    useEffect(() => {
        if (!ticketDetails) return

        const hasUnresolvedEscalations = ticketDetails.escalations?.some(
            (esc: Escalation) => !esc.resolvedAt
        ) || false

        const isChangingToClosed = status === 'closed' && originalStatus !== 'closed'

        setShowEscalationWarning(isChangingToClosed && hasUnresolvedEscalations)
    }, [status, originalStatus, ticketDetails])

    const statusColors: Record<string, string> = {
        opened: 'bg-info/10 text-info',
        closed: 'bg-success/10 text-success',
        stale: 'bg-warning/10 text-warning',
    }

    const handleTagToggle = (tagCode: string) => {
        setSelectedTags(prev =>
            prev.includes(tagCode)
                ? prev.filter(t => t !== tagCode)
                : [...prev, tagCode]
        )
    }

    const validateForm = (): boolean => {
        const errors: Record<string, string> = {}

        if (!status.trim()) {
            errors.status = 'Status is required'
        }

        if (!impact.trim()) {
            errors.impact = 'Impact is required'
        }

        if (!authorTeam.trim()) {
            errors.authorTeam = 'Author\'s team is required'
        }

        if (selectedTags.length === 0) {
            errors.tags = 'At least one tag is required'
        }

        setValidationErrors(errors)
        return Object.keys(errors).length === 0
    }

    const handleSave = async () => {
        if (!ticketId || !ticketDetails) return

        // Validate form before saving
        if (!validateForm()) {
            setError('Please fill in all required fields')
            return
        }

        setIsSaving(true)
        setError(null)
        setValidationErrors({})

        try {
            // Convert email (assignedTo) to userId for the API call (only if assignment is enabled)
            const assignedUserId = isAssignmentEnabled && assignedTo
                ? supportMembers?.find(m => m.displayName === assignedTo)?.userId || null
                : null

            const updatePayload: Record<string, unknown> = {
                status,
                authorsTeam: authorTeam,
                tags: selectedTags,
                impact,
            }
            
            // Only include assignedTo if assignment feature is enabled
            if (isAssignmentEnabled) {
                updatePayload.assignedTo = assignedUserId
            }

            const response = await fetch(`/api/tickets/${ticketId}`, {
                method: 'PATCH',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify(updatePayload),
            })

            if (!response.ok) {
                throw new Error(`Failed to update ticket: ${response.status}`)
            }
            
            onOpenChange(false)
            onSuccess?.()
        } catch (err) {
            setError(err instanceof Error ? err.message : 'Failed to update ticket')
            console.error('Error updating ticket:', err)
        } finally {
            setIsSaving(false)
        }
    }

    const displayTicket: TicketWithLogs = ticketDetails ?? {
        id: ticketId || '',
        status: status || '',
        impact: impact || '',
        tags: selectedTags,
        team: authorTeam ? { name: authorTeam } : null,
        escalations: [],
        logs: [],
        query: undefined,
    }

    const canEdit = isSupportEngineer

    return (
        <Dialog open={open} onOpenChange={onOpenChange}>
            <DialogContent data-testid="edit-ticket-modal" className="min-w-2xl max-w-3xl max-h-[90vh] overflow-y-auto">
                <DialogHeader>
                    <DialogTitle className="flex items-center gap-2 text-xl">
                        <Ticket className="h-5 w-5 text-primary" />
                        Ticket #{ticketId}
                        {!canEdit && (
                            <span className="text-xs font-normal text-muted-foreground ml-1">
                                (Read-only)
                            </span>
                        )}
                    </DialogTitle>
                    <DialogDescription className="text-sm">
                        {canEdit
                            ? 'Edit ticket details below.'
                            : 'View ticket details. Only support engineers can edit tickets.'}
                    </DialogDescription>
                </DialogHeader>

                {ticketError && (
                    <div className="bg-destructive/10 border-2 border-destructive/30 rounded-lg p-4 text-destructive text-sm font-medium">
                        Failed to load ticket details.
                    </div>
                )}
                {isTicketLoading ? (
                    <div data-testid="ticket-loading-skeleton" className="space-y-4 pt-2">
                        {[1, 2, 3, 4, 5, 6].map((i) => (
                            <div key={i} className="space-y-1.5">
                                <Skeleton className="h-4 w-32" />
                                <Skeleton className="h-9 w-full" />
                            </div>
                        ))}
                    </div>
                ) : (
                <div className="space-y-4 pt-2">
                        {/* Ticket Message */}
                        {displayTicket?.query?.text && (
                            <div className="space-y-1.5">
                                <Label className="text-sm font-medium text-foreground">Ticket Message</Label>
                                <div className="rounded-md border bg-muted/40 px-3 py-2">
                                    <SlackMessageRenderer text={displayTicket.query.text} className="break-words text-sm" />
                                    {displayTicket.query.date && (
                                        <p className="mt-2 text-xs text-muted-foreground">
                                            {new Date(displayTicket.query.date).toLocaleString()}
                                        </p>
                                    )}
                                </div>
                            </div>
                        )}

                        {displayTicket?.summary?.trim() && (
                            <div className="space-y-1.5">
                                <Label className="text-sm font-medium text-foreground">AI Summary</Label>
                                <div className="rounded-md border bg-muted/40 px-3 py-2">
                                    <p className="text-sm leading-6 text-foreground whitespace-pre-wrap break-words">
                                        {displayTicket.summary}
                                    </p>
                                </div>
                            </div>
                        )}

                        {/* Status History */}
                        <div className="space-y-1.5">
                            <Label className="text-sm font-medium text-foreground">Status History</Label>
                            <div className="max-h-40 overflow-y-auto rounded-md border bg-muted/40 text-sm">
                                <ul className="divide-y">
                                    {displayTicket.logs?.map((log: { event: string; date: string }, idx: number) => (
                                        <li key={idx} className="flex h-9 items-center justify-between px-3 hover:bg-muted/60 transition">
                                            <span className="font-medium text-foreground">{log.event}</span>
                                            <span className="text-muted-foreground text-xs">
                                                {new Date(log.date).toLocaleString()}
                                            </span>
                                        </li>
                                    ))}
                                </ul>
                            </div>
                        </div>

                        {/* Escalations */}
                        <div className="space-y-1.5">
                            <Label className="text-sm font-medium text-foreground">Escalations</Label>
                            <div className="rounded-md border bg-muted/40 text-sm">
                                {displayTicket.escalations?.length ? (
                                    <ul className="divide-y">
                                        {displayTicket.escalations.map((esc, idx) => (
                                            <li key={idx} className="flex h-9 items-center px-3 text-foreground">
                                                Escalated to <span className="ml-1 font-medium">{esc.team?.name || 'Unknown team'}</span>
                                            </li>
                                        ))}
                                    </ul>
                                ) : (
                                    <div className="flex h-9 items-center px-3 text-muted-foreground">No escalations</div>
                                )}
                            </div>
                        </div>

                        {/* Change Status */}
                        <div className="space-y-1.5">
                            <Label htmlFor="status-select" className="text-sm font-medium text-foreground">
                                Change Status <span className="text-destructive">*</span>
                            </Label>
                            {canEdit ? (
                                <div className="space-y-1">
                                    <Select
                                        value={status}
                                        onValueChange={(v) => {
                                            setStatus(v)
                                            if (validationErrors.status) {
                                                clearValidationError('status')
                                            }
                                        }}
                                    >
                                        <SelectTrigger
                                            id="status-select"
                                            data-testid="status-select"
                                            className={`w-full ${validationErrors.status ? 'border-destructive' : ''}`}
                                        >
                                            <SelectValue placeholder="Select status..." />
                                        </SelectTrigger>
                                        <SelectContent>
                                            <SelectItem value="opened">Opened</SelectItem>
                                            <SelectItem value="closed">Closed</SelectItem>
                                            <SelectItem value="stale">Stale</SelectItem>
                                        </SelectContent>
                                    </Select>
                                    {validationErrors.status && (
                                        <p className="text-sm text-destructive">{validationErrors.status}</p>
                                    )}
                                    {showEscalationWarning && (
                                        <div className="mt-3 p-3 bg-warning/10 border border-warning/30 rounded-md">
                                            <div className="flex gap-2">
                                                <AlertCircle className="w-5 h-5 text-warning flex-shrink-0 mt-0.5" />
                                                <div className="flex-1">
                                                    <p className="text-sm font-semibold text-warning">
                                                        Ticket has {ticketDetails?.escalations?.filter((esc: Escalation) => !esc.resolvedAt).length} unresolved escalation{(ticketDetails?.escalations?.filter((esc: Escalation) => !esc.resolvedAt).length || 0) > 1 ? 's' : ''}.
                                                    </p>
                                                    <p className="text-sm text-warning mt-1">
                                                        Closing the ticket will close all related escalations.
                                                    </p>
                                                </div>
                                            </div>
                                        </div>
                                    )}
                                </div>
                            ) : (
                                <span className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-semibold ${
                                    statusColors[displayTicket.status] || 'bg-muted text-foreground'
                                }`}>
                                    {displayTicket.status || '-'}
                                </span>
                            )}
                        </div>

                        {/* Assignee */}
                        {isAssignmentEnabled && (
                            <div className="space-y-1.5">
                                <Label htmlFor="assignee-select" className="text-sm font-medium text-foreground">
                                    Support Engineer
                                </Label>
                                {canEdit ? (
                                    <Select
                                        value={assignedTo || '__unassigned'}
                                        onValueChange={(v) => setAssignedTo(v === '__unassigned' ? '' : v)}
                                    >
                                        <SelectTrigger id="assignee-select" className="w-full">
                                            <SelectValue placeholder="Unassigned" />
                                        </SelectTrigger>
                                        <SelectContent>
                                            <SelectItem value="__unassigned">Unassigned</SelectItem>
                                            {supportMembers?.map((member: SupportMember) => (
                                                <SelectItem key={member.userId} value={member.displayName}>
                                                    {member.displayName}
                                                </SelectItem>
                                            ))}
                                        </SelectContent>
                                    </Select>
                                ) : (
                                    <p className="text-sm text-foreground">{assignedTo || 'Unassigned'}</p>
                                )}
                            </div>
                        )}

                        {/* Author's Team */}
                        <div className="space-y-1.5">
                            <Label htmlFor="team-select" className="text-sm font-medium text-foreground">
                                Author&apos;s Team <span className="text-destructive">*</span>
                            </Label>
                            {canEdit ? (() => {
                                const CLEAR_SENTINEL = '__clear__'
                                const suggested = isTeamSuggestionsError
                                    ? []
                                    : (teamSuggestionsData?.suggestedTeams ?? [])
                                const others = isTeamSuggestionsError
                                    ? (teamsData?.map((t) => t.name) ?? [])
                                    : (teamSuggestionsData?.otherTeams ?? [])
                                return (
                                    <div className="space-y-1">
                                        <Select
                                            value={authorTeam || undefined}
                                            onValueChange={(v) => {
                                                const next = v === CLEAR_SENTINEL ? '' : v
                                                setAuthorTeam(next)
                                                if (validationErrors.authorTeam && next) {
                                                    clearValidationError('authorTeam')
                                                }
                                            }}
                                        >
                                            <SelectTrigger
                                                id="team-select"
                                                className={`w-full ${validationErrors.authorTeam ? 'border-destructive' : ''}`}
                                            >
                                                <SelectValue placeholder="Select team..." />
                                            </SelectTrigger>
                                            <SelectContent>
                                                {suggested.length > 0 && (
                                                    <SelectGroup>
                                                        <SelectLabel>Suggested teams</SelectLabel>
                                                        {suggested.map((name) => (
                                                            <SelectItem key={`s-${name}`} value={name}>{name}</SelectItem>
                                                        ))}
                                                    </SelectGroup>
                                                )}
                                                {others.length > 0 && (
                                                    <SelectGroup>
                                                        {suggested.length > 0 && <SelectSeparator />}
                                                        <SelectLabel>Others</SelectLabel>
                                                        {others.map((name) => (
                                                            <SelectItem key={`o-${name}`} value={name}>{name}</SelectItem>
                                                        ))}
                                                    </SelectGroup>
                                                )}
                                                {authorTeam && (
                                                    <>
                                                        <SelectSeparator />
                                                        <SelectItem value={CLEAR_SENTINEL} className="text-muted-foreground">
                                                            Clear selection
                                                        </SelectItem>
                                                    </>
                                                )}
                                            </SelectContent>
                                        </Select>
                                        {validationErrors.authorTeam && (
                                            <p className="text-sm text-destructive">{validationErrors.authorTeam}</p>
                                        )}
                                    </div>
                                )
                            })() : (
                                <p className="text-sm text-foreground">{displayTicket.team?.name || '-'}</p>
                            )}
                        </div>

                        {/* Tags */}
                        <div className="space-y-1.5">
                            <Label htmlFor="tags-select" className="text-sm font-medium text-foreground">
                                Tags <span className="text-destructive">*</span>
                            </Label>
                            {canEdit ? (
                                <div className="space-y-1">
                                    <MultiSelect
                                        triggerId="tags-select"
                                        placeholder="Select tags..."
                                        searchPlaceholder="Search tags..."
                                        error={!!validationErrors.tags}
                                        options={(registryData?.tags ?? []).map((t: TicketTag) => ({
                                            label: t.label,
                                            value: t.code,
                                        }))}
                                        selected={selectedTags}
                                        onChange={(next) => {
                                            setSelectedTags(next)
                                            if (validationErrors.tags && next.length > 0) {
                                                clearValidationError('tags')
                                            }
                                        }}
                                    />
                                    {validationErrors.tags && (
                                        <p className="text-sm text-destructive">{validationErrors.tags}</p>
                                    )}
                                </div>
                            ) : (
                                <div className="flex flex-wrap gap-1.5">
                                    {(displayTicket.tags ?? []).length ? (
                                        (displayTicket.tags as string[]).map((tag: string, idx: number) => (
                                            <span key={idx} className="rounded-full border px-2 py-0.5 text-xs text-foreground">
                                                {tag}
                                            </span>
                                        ))
                                    ) : (
                                        <span className="text-sm text-muted-foreground">-</span>
                                    )}
                                </div>
                            )}
                        </div>

                        {/* Impact */}
                        <div className="space-y-1.5">
                            <Label htmlFor="impact-select" className="text-sm font-medium text-foreground">
                                Impact <span className="text-destructive">*</span>
                            </Label>
                            {canEdit ? (
                                <div className="space-y-1">
                                    <Select
                                        value={impact}
                                        onValueChange={(v) => {
                                            setImpact(v)
                                            if (validationErrors.impact) {
                                                clearValidationError('impact')
                                            }
                                        }}
                                    >
                                        <SelectTrigger
                                            id="impact-select"
                                            data-testid="impact-select"
                                            className={`w-full ${validationErrors.impact ? 'border-destructive' : ''}`}
                                        >
                                            <SelectValue placeholder="Select impact..." />
                                        </SelectTrigger>
                                        <SelectContent>
                                            {registryData?.impacts.map((imp: TicketImpact) => (
                                                <SelectItem key={imp.code} value={imp.code}>
                                                    {imp.label}
                                                </SelectItem>
                                            ))}
                                        </SelectContent>
                                    </Select>
                                    {validationErrors.impact && (
                                        <p className="text-sm text-destructive">{validationErrors.impact}</p>
                                    )}
                                </div>
                            ) : (
                                <p className="text-sm text-foreground">
                                    {registryData?.impacts.find((i: TicketImpact) => i.code === displayTicket.impact)?.label || displayTicket.impact || '-'}
                                </p>
                            )}
                        </div>

                        {/* Error Message */}
                        {error && (
                            <div className="bg-destructive/10 border-2 border-destructive/30 rounded-lg p-4 text-destructive text-sm font-medium">
                                {error}
                            </div>
                        )}
                </div>
                )}

                <DialogFooter className="flex items-center justify-between w-full pt-2">
                    <div className="flex-1">
                        {displayTicket?.query?.link && (
                            <Button asChild variant="outline" size="sm">
                                <a
                                    href={displayTicket.query.link}
                                    target="_blank"
                                    rel="noopener noreferrer"
                                >
                                    <Slack className="h-4 w-4" />
                                    Open in Slack
                                </a>
                            </Button>
                        )}
                    </div>
                    <div className="flex gap-2">
                        {canEdit && (
                            <>
                                <Button
                                    variant="outline"
                                    onClick={() => onOpenChange(false)}
                                    disabled={isSaving}
                                >
                                    Cancel
                                </Button>
                                <Button
                                    onClick={handleSave}
                                    disabled={isSaving || !status || !impact || !authorTeam || selectedTags.length === 0}
                                >
                                    {isSaving ? 'Saving...' : 'Save Changes'}
                                </Button>
                            </>
                        )}
                        {!canEdit && (
                            <Button onClick={() => onOpenChange(false)}>Close</Button>
                        )}
                    </div>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    )
}
