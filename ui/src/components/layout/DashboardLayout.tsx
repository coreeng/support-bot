'use client'

import React, { useState } from 'react'
import { usePathname } from 'next/navigation'
import Link from 'next/link'
import { AlertCircle, BarChart2, Home, Ticket, Headphones, ChevronDown, ChevronRight, ChevronLeft, LogOut, BookOpen } from 'lucide-react'
import { useAuth } from '@/hooks/useAuth'
import { useTeamFilter } from '@/contexts/TeamFilterContext'
import { buildHref } from '@/lib/utils'
import Image from 'next/image'
import TeamSelector from '@/components/TeamSelector'
import { useKnowledgeGapsEnabled, useTenantInsightsEnabled } from '@/lib/hooks'

// Types for tab visibility requirements
type TabVisibilityRequirements = {
    requiresFullAccess?: boolean
    requiresRoles?: string[]
    requiresFeatureFlag?: 'knowledgeGaps' | 'tenantInsights'
}

type SupportTab = {
    path: string
    label: string
    icon: React.ReactNode
    visibility?: TabVisibilityRequirements
}

// Define support tabs with their routes
const supportTabs: SupportTab[] = [
    { path: '/', label: 'Home', icon: <Home className="w-5 h-5 mr-2" /> },
    { path: '/tickets', label: 'Tickets', icon: <Ticket className="w-5 h-5 mr-2" /> },
    { path: '/escalations', label: 'Escalations', icon: <AlertCircle className="w-5 h-5 mr-2" /> },
    {
        path: '/knowledge-gaps',
        label: 'Support Area Summary',
        icon: <BookOpen className="w-5 h-5 mr-2" />,
        visibility: {
            requiresFullAccess: true,
            requiresFeatureFlag: 'knowledgeGaps'
        }
    },
    {
        path: '/health',
        label: 'Analytics & Operations',
        icon: <BarChart2 className="w-5 h-5 mr-2" />,
        visibility: { requiresFullAccess: true }
    },
    {
        path: '/sla',
        label: 'SLA Dashboard',
        icon: <BarChart2 className="w-5 h-5 mr-2" />,
        visibility: { requiresFullAccess: true }
    },
    {
        path: '/tenant-requests',
        label: 'Tenant Requests',
        icon: <BarChart2 className="w-5 h-5 mr-2" />,
        visibility: {
            requiresFullAccess: true,
            requiresFeatureFlag: 'tenantInsights'
        }
    }
]

export function DashboardLayout({ children }: { children: React.ReactNode }) {
    const { user, logout } = useAuth()
    const { hasFullAccess, selectedTeam } = useTeamFilter()
    const { data: isKnowledgeGapsEnabled, error: knowledgeGapsError } = useKnowledgeGapsEnabled()
    // const { data: isTenantInsightsEnabled, error: tenantInsightsError } = useTenantInsightsEnabled()
  const isTenantInsightsEnabled = true   // or false
  const tenantInsightsError = null

    if (knowledgeGapsError) console.warn('Failed to check knowledge-gaps feature flag:', knowledgeGapsError)
    if (tenantInsightsError) console.warn('Failed to check tenant-insights feature flag:', tenantInsightsError)
    const pathname = usePathname()

    // Sidebar state
    const [sidebarCollapsed, setSidebarCollapsed] = useState(false)
    const [supportExpanded, setSupportExpanded] = useState(true)

    const handleLogout = async () => {
        await logout()
    }

    // Helper function to check if a tab should be visible
    const isTabVisible = (tab: SupportTab): boolean => {
        if (!tab.visibility) {
            return true // No restrictions
        }

        const { requiresFullAccess, requiresRoles, requiresFeatureFlag } = tab.visibility

        // Check full access requirement
        if (requiresFullAccess && !hasFullAccess) {
            return false
        }

        // Check role requirement
        if (requiresRoles && requiresRoles.length > 0) {
            const hasRequiredRole = requiresRoles.some(role => user?.roles?.includes(role))
            if (!hasRequiredRole) {
                return false
            }
        }

        // Check feature flag requirement
        if (requiresFeatureFlag) {
            const flags: Record<string, boolean | undefined> = {
                knowledgeGaps: isKnowledgeGapsEnabled,
                tenantInsights: isTenantInsightsEnabled,
            }
            if (flags[requiresFeatureFlag] !== true) {
                return false
            }
        }

        return true
    }

    // Filter tabs based on visibility
    const visibleTabs = supportTabs.filter(isTabVisible)

    return (
        <div className="flex h-screen bg-gray-50">
            <div className="flex flex-1 overflow-hidden">
                {/* Left Sidebar */}
                <div
                    className={`${sidebarCollapsed ? 'w-16' : 'w-56'
                        } bg-gradient-to-b from-gray-900 to-gray-800 text-white flex flex-col transition-all duration-300 shadow-2xl relative`}
                >
                    {/* Sidebar Header */}
                    <div className="p-4 border-b border-gray-700 flex items-center justify-between">
                        {!sidebarCollapsed ? (
                            <>
                                <div className="flex items-center gap-2 px-3 py-2 rounded-lg">
                                    <Image src="/logo-dark.png" alt="Core Community" width={160} height={40} priority />
                                </div>
                                <button
                                    onClick={() => setSidebarCollapsed(true)}
                                    className="p-2 hover:bg-gray-700 rounded-lg transition-colors"
                                    title="Collapse sidebar"
                                >
                                    <ChevronLeft className="w-5 h-5" />
                                </button>
                            </>
                        ) : (
                            <button
                                onClick={() => setSidebarCollapsed(false)}
                                className="w-full flex flex-col items-center gap-2 hover:bg-gray-700 rounded-lg transition-colors p-2"
                                title="Expand sidebar"
                            >
                                <div className="w-10 h-10 rounded-lg flex items-center justify-center p-1">
                                    {/* Plain <img> rather than next/image — .ico is multi-resolution
                                        and small; sharp optimization is unnecessary here. */}
                                    {/* eslint-disable-next-line @next/next/no-img-element */}
                                    <img src="/favicon.ico" alt="Core Community" width={24} height={24} className="w-6 h-6" />
                                </div>
                                <ChevronRight className="w-4 h-4" />
                            </button>
                        )}
                    </div>

                    {/* Navigation */}
                    <nav className="flex-1 overflow-y-auto py-4">
                        {/* Support Section */}
                        <div className="mb-2">
                            <button
                                onClick={() => setSupportExpanded(!supportExpanded)}
                                className="w-full flex items-center justify-between px-4 py-3 hover:bg-gray-700 transition-colors bg-gray-700 border-l-4 border-blue-500"
                            >
                                <div className="flex items-center gap-3">
                                    <Headphones className="w-5 h-5" />
                                    {!sidebarCollapsed && <span className="font-medium">Support</span>}
                                </div>
                                {!sidebarCollapsed && (
                                    <ChevronDown className={`w-4 h-4 transition-transform ${supportExpanded ? '' : '-rotate-90'}`} />
                                )}
                            </button>

                            {/* Support Sub-items */}
                            {supportExpanded && !sidebarCollapsed && (
                                <div className="bg-gray-800/50">
                                    {visibleTabs.map(tab => {
                                        const isActive = pathname === tab.path
                                        return (
                                            <Link
                                                key={tab.path}
                                                href={buildHref(tab.path, { team: selectedTeam })}
                                                className={`w-full flex items-center gap-3 px-8 py-2.5 text-sm hover:bg-gray-700 transition-colors ${isActive ? 'bg-blue-600 text-white' : 'text-gray-300'
                                                    }`}
                                            >
                                                <span>{tab.label}</span>
                                            </Link>
                                        )
                                    })}
                                </div>
                            )}
                        </div>
                    </nav>

                    {/* Sidebar Footer */}
                    <div className="border-t border-gray-700 p-4 space-y-3">
                        {user ? (
                            <>
                                {!sidebarCollapsed ? (
                                    <>
                                        <div className="text-sm">
                                            <p className="text-gray-400 text-xs mb-1">Signed in as</p>
                                            <p className="text-white font-medium truncate" title={user.email}>
                                                {user.email}
                                            </p>
                                            {user.teams.length > 0 && (
                                                <div className="group relative">
                                                    <p className="text-gray-400 text-xs mt-1 cursor-help">
                                                        {user.teams.length} {user.teams.length === 1 ? 'team' : 'teams'}
                                                    </p>
                                                    <div className="absolute left-0 bottom-full mb-2 hidden group-hover:block z-50 w-max max-w-xs">
                                                        <div className="bg-gray-900 text-white text-xs rounded py-2 px-3 shadow-lg border border-gray-700">
                                                            {user.teams.map(t => t.label).join(', ')}
                                                        </div>
                                                    </div>
                                                </div>
                                            )}
                                        </div>

                                        {/* Team Selector */}
                                        <div className="pt-2">
                                            <TeamSelector />
                                        </div>

                                        {/* Logout Button */}
                                        <button
                                            onClick={handleLogout}
                                            className="w-full flex items-center justify-center gap-2 px-3 py-2 bg-gray-700 hover:bg-gray-600 rounded transition-colors text-sm"
                                            title="Sign out"
                                        >
                                            <LogOut className="w-4 h-4" />
                                            <span>Sign Out</span>
                                        </button>
                                    </>
                                ) : (
                                    <div className="flex flex-col items-center gap-3">
                                        <div className="group relative">
                                            <div
                                                className="w-8 h-8 rounded-full bg-blue-500 flex items-center justify-center text-white font-semibold text-sm cursor-help"
                                            >
                                                {user.email.charAt(0).toUpperCase()}
                                            </div>
                                            <div className="absolute left-full ml-2 top-0 hidden group-hover:block z-50 w-max max-w-xs">
                                                <div className="bg-gray-900 text-white text-xs rounded py-2 px-3 shadow-lg border border-gray-700">
                                                    <p className="font-semibold mb-1">{user.email}</p>
                                                    <p className="text-gray-400">{user.teams.length} {user.teams.length === 1 ? 'team' : 'teams'}:</p>
                                                    <p>{user.teams.map(t => t.label).join(', ')}</p>
                                                </div>
                                            </div>
                                        </div>
                                        <button
                                            onClick={handleLogout}
                                            className="w-8 h-8 flex items-center justify-center bg-gray-700 hover:bg-gray-600 rounded transition-colors"
                                            title="Sign out"
                                        >
                                            <LogOut className="w-4 h-4" />
                                        </button>
                                    </div>
                                )}
                            </>
                        ) : null}
                    </div>
                </div>

                {/* Main Content Area */}
                <div className="flex-1 overflow-auto">
                    <div className="h-full">
                        {children}
                    </div>
                </div>
            </div>
        </div>
    )
}

