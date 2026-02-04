'use client'

import { useState, useEffect } from 'react'
import { useTicket, useTenantTeams, useRegistry, useSupportMembers, useAssignmentEnabled } from '@/lib/hooks'
import { TicketWithLogs, TicketImpact, TicketTag, Escalation, SupportMember } from '@/lib/types'
import { useAuth } from '@/contexts/AuthContext'
import {
    Dialog,
    DialogContent,
    DialogHeader,
    DialogTitle,
    DialogDescription,
    DialogFooter,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Ticket, AlertCircle, Tag, User, Clock, Slack, X, MessageSquare } from 'lucide-react'
import { apiPatch } from '@/lib/api'
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
        opened: 'bg-blue-100 text-blue-800',
        closed: 'bg-green-100 text-green-800',
        stale: 'bg-yellow-100 text-yellow-800',
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

        if (!status || status.trim() === '') {
            errors.status = 'Status is required'
        }

        if (!impact || impact.trim() === '') {
            errors.impact = 'Impact is required'
        }

        if (!authorTeam || authorTeam.trim() === '') {
            errors.authorTeam = 'Author\'s team is required'
        }

        if (!selectedTags || selectedTags.length === 0) {
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
                status: status,
                authorsTeam: authorTeam,
                tags: selectedTags,
                impact: impact,
            }
            
            // Only include assignedTo if assignment feature is enabled
            if (isAssignmentEnabled) {
                updatePayload.assignedTo = assignedUserId
            }

            await apiPatch(`/ticket/${ticketId}`, updatePayload)
            
            // Close modal and refresh
            onOpenChange(false)
            if (onSuccess) {
                onSuccess()
            }
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
            <DialogContent data-testid="edit-ticket-modal" className="max-w-4xl max-h-[90vh] overflow-y-auto sm:p-6">
                <DialogHeader className="pb-4 border-b border-gray-200">
                    <DialogTitle className="flex items-center gap-3 text-3xl">
                        <Ticket className="w-7 h-7 text-blue-500" />
                        Ticket #{ticketId}
                        {!canEdit && (
                            <span className="text-base font-normal text-gray-500 ml-2">
                                (Read-only)
                            </span>
                        )}
                    </DialogTitle>
                    <DialogDescription className="leading-relaxed">
                        {canEdit
                            ? 'Edit ticket details below.'
                            : 'View ticket details. Only support engineers can edit tickets.'}
                    </DialogDescription>
                </DialogHeader>

                {ticketError && (
                    <div className="bg-red-50 border-2 border-red-200 rounded-lg p-4 text-red-800 text-sm font-medium">
                        Failed to load ticket details.
                    </div>
                )}
                {isTicketLoading && (
                    <div data-testid="ticket-loading-skeleton" className="space-y-4 pt-4 animate-pulse">
                        {/* Ticket Message Skeleton */}
                        <div className="space-y-3 p-4 border border-gray-200/60 rounded-lg bg-blue-50/30 shadow-sm">
                            <div className="flex items-center gap-2 pb-2 border-b border-gray-200/60">
                                <div className="w-5 h-5 bg-gray-300 rounded"></div>
                                <div className="h-5 bg-gray-300 rounded w-32"></div>
                            </div>
                            <div className="p-4 bg-white border border-gray-200 rounded-md space-y-2">
                                <div className="h-4 bg-gray-200 rounded w-full"></div>
                                <div className="h-4 bg-gray-200 rounded w-3/4"></div>
                                <div className="h-4 bg-gray-200 rounded w-5/6"></div>
                            </div>
                        </div>

                        {/* Status History Skeleton */}
                        <div className="space-y-3 p-4 border border-gray-200/60 rounded-lg bg-gray-50/50 shadow-sm">
                            <div className="flex items-center gap-2 pb-2 border-b border-gray-200/60">
                                <div className="w-5 h-5 bg-gray-300 rounded"></div>
                                <div className="h-5 bg-gray-300 rounded w-28"></div>
                            </div>
                            <div className="space-y-2 border border-gray-200 rounded-md p-3 bg-white">
                                <div className="h-6 bg-gray-200 rounded w-full"></div>
                                <div className="h-6 bg-gray-200 rounded w-full"></div>
                                <div className="h-6 bg-gray-200 rounded w-4/5"></div>
                            </div>
                        </div>

                        {/* Escalations Skeleton */}
                        <div className="space-y-3 p-4 border border-gray-200/60 rounded-lg bg-white shadow-sm">
                            <div className="flex items-center gap-2 pb-2 border-b border-gray-200/60">
                                <div className="w-5 h-5 bg-gray-300 rounded"></div>
                                <div className="h-5 bg-gray-300 rounded w-24"></div>
                            </div>
                            <div className="h-10 bg-gray-200 rounded w-full"></div>
                        </div>

                        {/* Form Fields Skeleton */}
                        {[1, 2, 3, 4].map((i) => (
                            <div key={i} className={`space-y-3 p-4 border border-gray-200/60 rounded-lg shadow-sm ${i % 2 === 0 ? 'bg-gray-50/50' : 'bg-white'}`}>
                                <div className="flex items-center gap-2 pb-2 border-b border-gray-200/60">
                                    <div className="w-5 h-5 bg-gray-300 rounded"></div>
                                    <div className="h-5 bg-gray-300 rounded w-36"></div>
                                </div>
                                <div className="h-10 bg-gray-200 rounded w-full"></div>
                            </div>
                        ))}
                    </div>
                )}
                <div className="space-y-4 pt-4">
                        {/* Ticket Message Section */}
                        {displayTicket?.query?.text && (
                            <div className="space-y-3 p-4 border border-gray-200/60 rounded-lg bg-blue-50/30 shadow-sm">
                                <label className="text-base font-semibold text-gray-900 flex items-center gap-2 pb-2 border-b border-gray-200/60">
                                    <MessageSquare className="w-5 h-5 text-gray-600" />
                                    Ticket Message
                                </label>
                                <div className="p-3 bg-white border border-gray-200 rounded-md">
                                    <SlackMessageRenderer 
                                        text={displayTicket.query.text} 
                                        className="break-words"
                                    />
                                    {displayTicket.query.date && (
                                        <p className="text-xs text-gray-500 mt-3 pt-3 border-t border-gray-100">
                                            {new Date(displayTicket.query.date).toLocaleString()}
                                        </p>
                                    )}
                                </div>
                            </div>
                        )}

                        {/* Status History (Logs Section) */}
                        <div className="space-y-3 p-4 border border-gray-200/60 rounded-lg bg-gray-50/50 shadow-sm">
                            <label className="text-base font-semibold text-gray-900 flex items-center gap-2 pb-2 border-b border-gray-200/60">
                                <Clock className="w-5 h-5 text-gray-600" />
                                Status History
                            </label>
                            <ul className="space-y-1 max-h-40 overflow-y-auto border border-gray-200 rounded-md p-3 bg-white">
                                {displayTicket.logs?.map((log: { event: string; date: string }, idx: number) => (
                                    <li
                                        key={idx}
                                        className="text-sm text-gray-700 flex justify-between hover:bg-gray-50 px-2 py-1.5 rounded transition border-b border-gray-100 last:border-b-0"
                                    >
                                        <span className="font-medium">{log.event}</span>
                                        <span className="text-gray-500 text-xs">
                                            {new Date(log.date).toLocaleString()}
                                        </span>
                                    </li>
                                ))}
                            </ul>
                        </div>

                        {/* Escalations Section */}
                        <div className="space-y-3 p-4 border border-gray-200/60 rounded-lg bg-white shadow-sm">
                            <label className="text-base font-semibold text-gray-900 flex items-center gap-2 pb-2 border-b border-gray-200/60">
                                <AlertCircle className="w-5 h-5 text-gray-600" />
                                Escalations
                            </label>
                            <div className="p-3 border border-gray-200 rounded-md bg-gray-50">
                                {(displayTicket.escalations ?? []).length > 0 ? (
                                    <div className="space-y-2">
                                        {(displayTicket.escalations ?? []).map((esc, idx) => (
                                            <div key={idx} className="text-sm text-gray-700">
                                                <span className="font-medium">Escalated to:</span>{' '}
                                                <span className="text-gray-800 font-semibold">{esc.team?.name || 'Unknown team'}</span>
                                            </div>
                                        ))}
                                    </div>
                                ) : (
                                    <span className="text-gray-500">No escalations</span>
                                )}
                            </div>
                        </div>

                        {/* Change Status Section */}
                        <div className="space-y-3 p-4 border border-gray-200/60 rounded-lg bg-gray-50/50 shadow-sm">
                            <label htmlFor="status-select" className="text-base font-semibold text-gray-900 flex items-center gap-2 pb-2 border-b border-gray-200/60">
                                <AlertCircle className="w-5 h-5 text-gray-600" />
                                Change Status <span className="text-red-500">*</span>
                            </label>
                            {canEdit ? (
                                <div className="space-y-1">
                                    <select
                                        data-testid="status-select"
                                        id="status-select"
                                        value={status}
                                        onChange={(e) => {
                                            setStatus(e.target.value)
                                            if (validationErrors.status) {
                                                setValidationErrors(prev => {
                                                    const next = { ...prev }
                                                    delete next.status
                                                    return next
                                                })
                                            }
                                        }}
                                        className={`w-full p-2.5 border rounded-md bg-white focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 focus:shadow-md transition-all ${
                                            validationErrors.status ? 'border-red-500' : 'border-gray-300'
                                        }`}
                                    >
                                        <option value="">Select status...</option>
                                        <option value="opened">Opened</option>
                                        <option value="closed">Closed</option>
                                    </select>
                                    {validationErrors.status && (
                                        <p className="text-sm text-red-600">{validationErrors.status}</p>
                                    )}
                                    {showEscalationWarning && (
                                        <div className="mt-3 p-3 bg-yellow-50 border border-yellow-300 rounded-md">
                                            <div className="flex gap-2">
                                                <AlertCircle className="w-5 h-5 text-yellow-600 flex-shrink-0 mt-0.5" />
                                                <div className="flex-1">
                                                    <p className="text-sm font-semibold text-yellow-800">
                                                        Ticket has {ticketDetails?.escalations?.filter((esc: Escalation) => !esc.resolvedAt).length} unresolved escalation{(ticketDetails?.escalations?.filter((esc: Escalation) => !esc.resolvedAt).length || 0) > 1 ? 's' : ''}.
                                                    </p>
                                                    <p className="text-sm text-yellow-700 mt-1">
                                                        Closing the ticket will close all related escalations.
                                                    </p>
                                                </div>
                                            </div>
                                        </div>
                                    )}
                                </div>
                            ) : (
                                <span className={`px-4 py-2 rounded-full text-sm font-semibold inline-block shadow-sm ${
                                    statusColors[displayTicket.status] || 'bg-gray-100 text-gray-800'
                                }`}>
                                    {displayTicket.status || '-'}
                                </span>
                            )}
                        </div>

                        {/* Assignee Section - Only show if assignment is enabled */}
                        {isAssignmentEnabled && (
                            <div className="space-y-3 p-4 border border-gray-200/60 rounded-lg bg-blue-50/30 shadow-sm">
                                <label htmlFor="assignee-select" className="text-base font-semibold text-gray-900 flex items-center gap-2 pb-2 border-b border-gray-200/60">
                                    <User className="w-5 h-5 text-gray-600" />
                                    Support Engineer
                                </label>
                                {canEdit ? (
                                    <select
                                        id="assignee-select"
                                        value={assignedTo}
                                        onChange={(e) => setAssignedTo(e.target.value)}
                                        className="w-full p-2.5 border border-gray-300 rounded-md bg-white focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 focus:shadow-md transition-all"
                                    >
                                        <option value="">Unassigned</option>
                                        {supportMembers?.map((member: SupportMember) => (
                                            <option key={member.userId} value={member.displayName}>
                                                {member.displayName}
                                            </option>
                                        ))}
                                    </select>
                                ) : (
                                    <div className="p-3 bg-white border border-gray-200 rounded-md">
                                        <span className="font-medium text-gray-700">
                                            {assignedTo || 'Unassigned'}
                                        </span>
                                    </div>
                                )}
                            </div>
                        )}

                        {/* Select the Author's Team Section */}
                        <div className="space-y-3 p-4 border border-gray-200/60 rounded-lg bg-white shadow-sm">
                            <label htmlFor="team-select" className="text-base font-semibold text-gray-900 flex items-center gap-2 pb-2 border-b border-gray-200/60">
                                <User className="w-5 h-5 text-gray-600" />
                                Select the Author&apos;s Team <span className="text-red-500">*</span>
                            </label>
                            {canEdit ? (
                                <div className="space-y-1">
                                    {(() => {
                                        const options = Array.from(new Set([
                                            displayTicket.team?.name,
                                            ...(teamsData?.map((team: { name: string }) => team.name) ?? []),
                                        ].filter(Boolean))) as string[]
                                        return (
                                            <select
                                                id="team-select"
                                                value={authorTeam}
                                                onChange={(e) => {
                                                    setAuthorTeam(e.target.value)
                                                    if (validationErrors.authorTeam) {
                                                        setValidationErrors(prev => {
                                                            const next = { ...prev }
                                                            delete next.authorTeam
                                                            return next
                                                        })
                                                    }
                                                }}
                                                className={`w-full p-2.5 border rounded-md bg-white focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 focus:shadow-md transition-all ${
                                                    validationErrors.authorTeam ? 'border-red-500' : 'border-gray-300'
                                                }`}
                                            >
                                                <option value="">Select team...</option>
                                                {options.map((name) => (
                                                    <option key={name} value={name}>
                                                        {name}
                                                    </option>
                                                ))}
                                            </select>
                                        )
                                    })()}
                                    {validationErrors.authorTeam && (
                                        <p className="text-sm text-red-600">{validationErrors.authorTeam}</p>
                                    )}
                                </div>
                            ) : (
                                <span className="font-medium text-gray-700 px-3 py-1.5 bg-gray-50 border border-gray-200 rounded-md inline-block">
                                    {displayTicket.team?.name || '-'}
                                </span>
                            )}
                        </div>

                        {/* Tags Section */}
                        <div className="space-y-3 p-4 border border-gray-200/60 rounded-lg bg-gray-50/50 shadow-sm">
                            <label htmlFor="tags-select" className="text-base font-semibold text-gray-900 flex items-center gap-2 pb-2 border-b border-gray-200/60">
                                <Tag className="w-5 h-5 text-gray-600" />
                                Tags <span className="text-red-500">*</span>
                            </label>
                            {canEdit ? (
                                <div className="space-y-3">
                                    {/* Selected Tags Display */}
                                    <div className={`flex flex-wrap gap-2 p-3 border rounded-md bg-white min-h-[50px] ${
                                        validationErrors.tags ? 'border-red-500' : 'border-gray-200'
                                    }`}>
                                        {selectedTags.length === 0 ? (
                                            <span className="text-gray-400 text-sm">No tags selected</span>
                                        ) : (
                                            selectedTags.map((tagCode) => {
                                                const tag = registryData?.tags.find((t: TicketTag) => t.code === tagCode)
                                                return (
                                                    <span
                                                        key={tagCode}
                                                        className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-gray-200 text-gray-700 rounded-full text-sm font-medium shadow-sm hover:bg-gray-300 transition-colors"
                                                    >
                                                        {tag?.label || tagCode}
                                                        <button
                                                            onClick={() => {
                                                                handleTagToggle(tagCode)
                                                                if (validationErrors.tags && selectedTags.length === 1) {
                                                                    // If this was the last tag, clear the error when user adds another
                                                                    setValidationErrors(prev => {
                                                                        const next = { ...prev }
                                                                        delete next.tags
                                                                        return next
                                                                    })
                                                                }
                                                            }}
                                                            className="hover:bg-gray-400/50 rounded-full p-0.5 transition-colors"
                                                            type="button"
                                                        >
                                                            <X className="w-3 h-3" />
                                                        </button>
                                                    </span>
                                                )
                                            })
                                        )}
                                    </div>
                                    {validationErrors.tags && (
                                        <p className="text-sm text-red-600">{validationErrors.tags}</p>
                                    )}
                                    {/* Tags Dropdown */}
                                    <select
                                        id="tags-select"
                                        value=""
                                        onChange={(e) => {
                                            if (e.target.value && !selectedTags.includes(e.target.value)) {
                                                setSelectedTags([...selectedTags, e.target.value])
                                                if (validationErrors.tags) {
                                                    setValidationErrors(prev => {
                                                        const next = { ...prev }
                                                        delete next.tags
                                                        return next
                                                    })
                                                }
                                            }
                                            e.target.value = '' // Reset dropdown
                                        }}
                                        className="w-full p-2.5 border border-gray-300 rounded-md bg-white focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 focus:shadow-md transition-all"
                                    >
                                        <option value="">Select a tag to add...</option>
                                        {registryData?.tags
                                            .filter((tag: TicketTag) => !selectedTags.includes(tag.code))
                                            .map((tag: TicketTag) => (
                                                <option key={tag.code} value={tag.code}>
                                                    {tag.label}
                                                </option>
                                            ))}
                                    </select>
                                </div>
                            ) : (
                                <div className="flex flex-wrap gap-2 p-3 bg-white border border-gray-200 rounded-md">
                                    {(displayTicket.tags ?? []).length ? (
                                        (displayTicket.tags as string[]).map((tag: string, idx: number) => (
                                            <span
                                                key={idx}
                                                className="px-3 py-1 bg-gray-200 text-gray-700 rounded-full text-xs font-medium"
                                            >
                                                {tag}
                                            </span>
                                        ))
                                    ) : (
                                        <span className="text-gray-500">-</span>
                                    )}
                                </div>
                            )}
                        </div>

                        {/* Impact Section */}
                        <div className="space-y-3 p-4 border border-gray-200/60 rounded-lg bg-white shadow-sm">
                            <label htmlFor="impact-select" className="text-base font-semibold text-gray-900 flex items-center gap-2 pb-2 border-b border-gray-200/60">
                                <AlertCircle className="w-5 h-5 text-gray-600" />
                                Impact <span className="text-red-500">*</span>
                            </label>
                            {canEdit ? (
                                <div className="space-y-1">
                                    <select
                                        data-testid="impact-select"
                                        id="impact-select"
                                        value={impact}
                                        onChange={(e) => {
                                            setImpact(e.target.value)
                                            if (validationErrors.impact) {
                                                setValidationErrors(prev => {
                                                    const next = { ...prev }
                                                    delete next.impact
                                                    return next
                                                })
                                            }
                                        }}
                                        className={`w-full p-2.5 border rounded-md bg-white focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 focus:shadow-md transition-all ${
                                            validationErrors.impact ? 'border-red-500' : 'border-gray-300'
                                        }`}
                                    >
                                        <option value="">Select impact...</option>
                                        {registryData?.impacts.map((imp: TicketImpact) => (
                                            <option key={imp.code} value={imp.code}>
                                                {imp.label}
                                            </option>
                                        ))}
                                    </select>
                                    {validationErrors.impact && (
                                        <p className="text-sm text-red-600">{validationErrors.impact}</p>
                                    )}
                                </div>
                            ) : (
                                <span className="font-medium text-gray-700 bg-gray-100 text-gray-800 px-4 py-2 rounded-full inline-block shadow-sm">
                                    {registryData?.impacts.find((i: TicketImpact) => i.code === displayTicket.impact)?.label || displayTicket.impact || '-'}
                                </span>
                            )}
                        </div>

                        {/* Error Message */}
                        {error && (
                            <div className="bg-red-50 border-2 border-red-200 rounded-lg p-4 text-red-800 text-sm font-medium">
                                {error}
                            </div>
                        )}
                </div>

                <DialogFooter className="flex items-center justify-between w-full pt-4 border-t border-gray-200">
                    <div className="flex-1">
                        {displayTicket?.query?.link && (
                            <a
                                href={displayTicket.query.link}
                                target="_blank"
                                rel="noopener noreferrer"
                                className="inline-flex items-center px-5 py-2.5 bg-blue-600 text-white font-semibold rounded-lg shadow-md hover:bg-blue-700 hover:shadow-lg transition-all"
                            >
                                <Slack className="w-5 h-5 mr-2" />
                                Open in Slack
                            </a>
                        )}
                    </div>
                    <div className="flex gap-3">
                        {canEdit && (
                            <>
                                <Button
                                    variant="outline"
                                    onClick={() => onOpenChange(false)}
                                    disabled={isSaving}
                                    className="px-6 py-2.5"
                                >
                                    Cancel
                                </Button>
                            <Button
                                onClick={handleSave}
                                disabled={isSaving || !status || !impact || !authorTeam || selectedTags.length === 0}
                                className="px-6 py-2.5 bg-blue-600 hover:bg-blue-700 shadow-md hover:shadow-lg transition-all"
                            >
                                {isSaving ? 'Saving...' : 'Save Changes'}
                            </Button>
                            </>
                        )}
                        {!canEdit && (
                            <Button onClick={() => onOpenChange(false)} className="px-6 py-2.5">
                                Close
                            </Button>
                        )}
                    </div>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    )
}

