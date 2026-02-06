'use client'

import React, { useState, useEffect } from 'react'
import StatsPage from '@/components/stats/stats'
import TicketsPage from '@/components/tickets/tickets'
import EscalationsPage from '@/components/escalations/escalations'
import HealthPage from '@/components/health/health'
import { AlertCircle, BarChart2, Home, Ticket, Headphones, ChevronDown, ChevronRight, ChevronLeft } from 'lucide-react'
import { useUser } from '@/contexts/UserContext'
import { useTeamFilter } from '@/contexts/TeamFilterContext'
import Image from 'next/image'
import DashboardsPage from '@/components/dashboards/dashboards'
import TeamSelector from '@/components/TeamSelector'
import { signIn, signOut } from 'next-auth/react'
import { LogOut, BookOpen } from 'lucide-react'
import KnowledgeGapsPage from '@/components/knowledgegaps/knowledge-gaps'


// 1. Define support sub-tabs
const supportSubTabs = [
    { key: 'home', label: 'Home', icon: <Home className="w-5 h-5 mr-2" /> },
    { key: 'tickets', label: 'Tickets', icon: <Ticket className="w-5 h-5 mr-2" /> },
    { key: 'escalations', label: 'Escalations', icon: <AlertCircle className="w-5 h-5 mr-2" /> },
    { key: 'knowledge-gaps', label: 'Knowledge Gaps', icon: <BookOpen className="w-5 h-5 mr-2" /> },
    { key: 'health', label: 'Analytics & Operations', icon: <BarChart2 className="w-5 h-5 mr-2" /> },
    { key: 'sla', label: 'SLA Dashboard', icon: <BarChart2 className="w-5 h-5 mr-2" /> }
] as const

// 2. Derive TabKey types
type SupportSubTabKey = typeof supportSubTabs[number]['key']

export default function Dashboard() {
    const { user, isLoading, isAuthenticated } = useUser()
    const { hasFullAccess } = useTeamFilter()

    // Helper function to detect if running in iframe
    const isInIframe = () => {
        try {
            return window.self !== window.top
        } catch {
            return true // Assume iframe if access is blocked
        }
    }

    // State
    const [activeSupportSubTab, setActiveSupportSubTab] = useState<SupportSubTabKey>('home')

    // Sidebar state
    const [sidebarCollapsed, setSidebarCollapsed] = useState(false)
    const [supportExpanded, setSupportExpanded] = useState(true)

    // Sign in loading state
    const [isSigningIn, setIsSigningIn] = useState(false)

    // Show all tabs based on access level (same logic for iframe and non-iframe)
    const supportTabs = hasFullAccess
        ? supportSubTabs
        : supportSubTabs.filter(tab => tab.key !== 'health' && tab.key !== 'sla')

    // Ensure activeSupportSubTab is valid
    useEffect(() => {
        if (!supportTabs.some(tab => tab.key === activeSupportSubTab)) {
            setActiveSupportSubTab('home')
        }
    }, [supportTabs, activeSupportSubTab])


    // Show loading state (after all hooks are called)
    if (isLoading) {
        return (
            <div className="flex items-center justify-center h-screen bg-gray-50">
                <div className="text-center">
                    <div className="inline-block animate-spin rounded-full h-12 w-12 border-b-2 border-blue-500 mb-4"></div>
                    <p className="text-gray-600">Loading...</p>
                </div>
            </div>
        )
    }

    // Show login prompt if not authenticated (after all hooks are called)
    if (!isAuthenticated || !user) {
        return (
            <div className="relative flex items-center justify-center h-screen overflow-hidden bg-gradient-to-br from-blue-600 via-purple-600 to-pink-500 animate-gradient-xy">
                {/* Animated background shapes */}
                <div className="absolute inset-0 overflow-hidden">
                    <div className="absolute -top-40 -right-40 w-80 h-80 bg-white/10 rounded-full blur-3xl animate-blob"></div>
                    <div className="absolute -bottom-40 -left-40 w-80 h-80 bg-white/10 rounded-full blur-3xl animate-blob animation-delay-2000"></div>
                    <div className="absolute top-1/2 left-1/2 transform -translate-x-1/2 -translate-y-1/2 w-80 h-80 bg-white/10 rounded-full blur-3xl animate-blob animation-delay-4000"></div>
                </div>

                <div className="max-w-md w-full mx-4 z-10 animate-fade-in">
                    <div className="backdrop-blur-xl bg-white/90 rounded-2xl shadow-2xl p-8 border border-white/20">
                        <div className="text-center mb-8">
                            <Image src="/banner.png" alt="Core Community" width={200} height={250} className="mx-auto mb-6" priority />
                            <h1 className="text-3xl font-bold text-gray-900 mb-2">Support Dashboard</h1>
                            <p className="text-gray-600 text-sm">
                                Access your support metrics and insights
                            </p>
                        </div>

                        <div className="space-y-4">
                            <p className="text-sm font-semibold text-gray-700 text-center mb-4">Sign in with:</p>

                            {/* Azure AD Sign In */}
                            <button
                                onClick={() => {
                                    setIsSigningIn(true)

                                    if (isInIframe()) {
                                        // Use popup authentication for iframes
                                        const baseUrl = typeof window !== 'undefined' ? window.location.origin : ''
                                        const popup = window.open(
                                            `${baseUrl}/api/auth/popup?provider=azure-ad`,
                                            'Login',
                                            'width=500,height=600,scrollbars=yes,resizable=yes'
                                        )

                                        // Listen for messages from the popup
                                        const handleMessage = (event: MessageEvent) => {
                                            // Security: Check origin
                                            if (event.origin !== baseUrl) return

                                            if (event.data.type === 'LOGIN_SUCCESS') {
                                                // Authentication successful, refresh the page
                                                window.location.reload()
                                            } else if (event.data.type === 'LOGIN_FAILED' || event.data.type === 'LOGIN_ERROR') {
                                                setIsSigningIn(false)
                                                console.error('Authentication failed:', event.data.error)
                                            }

                                            // Clean up listener
                                            window.removeEventListener('message', handleMessage)
                                        }

                                        window.addEventListener('message', handleMessage)

                                        // Handle popup close without authentication
                                        const checkClosed = setInterval(() => {
                                            if (popup?.closed) {
                                                clearInterval(checkClosed)
                                                setIsSigningIn(false)
                                            }
                                        }, 1000)
                                    } else {
                                        // Use regular redirect authentication for non-iframes
                                        signIn('azure-ad')
                                    }
                                }}
                                disabled={isSigningIn}
                                className="w-full flex items-center justify-center gap-3 px-6 py-3.5 bg-gradient-to-r from-blue-600 to-blue-700 text-white rounded-lg hover:from-blue-700 hover:to-blue-800 transition-all duration-200 font-medium shadow-md hover:shadow-lg transform hover:-translate-y-0.5 disabled:opacity-70 disabled:cursor-not-allowed disabled:transform-none"
                            >
                                {isSigningIn ? (
                                    <>
                                        <svg className="animate-spin h-5 w-5" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                                            <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                                            <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                                        </svg>
                                        <span>Signing in...</span>
                                    </>
                                ) : (
                                    <>
                                        <svg className="w-5 h-5" viewBox="0 0 23 23" fill="none">
                                            <path d="M10.5 0H0V10.5H10.5V0Z" fill="white" />
                                            <path d="M23 0H12.5V10.5H23V0Z" fill="white" />
                                            <path d="M10.5 12.5H0V23H10.5V12.5Z" fill="white" />
                                            <path d="M23 12.5H12.5V23H23V12.5Z" fill="white" />
                                        </svg>
                                        <span>Azure AD</span>
                                    </>
                                )}
                            </button>

                            {/* Google Sign In */}
                            <button
                                onClick={() => {
                                    setIsSigningIn(true)

                                    if (isInIframe()) {
                                        // Use popup authentication for iframes
                                        const baseUrl = typeof window !== 'undefined' ? window.location.origin : ''
                                        const popup = window.open(
                                            `${baseUrl}/api/auth/popup?provider=google`,
                                            'Login',
                                            'width=500,height=600,scrollbars=yes,resizable=yes'
                                        )

                                        // Listen for messages from the popup
                                        const handleMessage = (event: MessageEvent) => {
                                            // Security: Check origin
                                            if (event.origin !== baseUrl) return

                                            if (event.data.type === 'LOGIN_SUCCESS') {
                                                // Authentication successful, refresh the page
                                                window.location.reload()
                                            } else if (event.data.type === 'LOGIN_FAILED' || event.data.type === 'LOGIN_ERROR') {
                                                setIsSigningIn(false)
                                                console.error('Authentication failed:', event.data.error)
                                            }

                                            // Clean up listener
                                            window.removeEventListener('message', handleMessage)
                                        }

                                        window.addEventListener('message', handleMessage)

                                        // Handle popup close without authentication
                                        const checkClosed = setInterval(() => {
                                            if (popup?.closed) {
                                                clearInterval(checkClosed)
                                                setIsSigningIn(false)
                                            }
                                        }, 1000)
                                    } else {
                                        // Use regular redirect authentication for non-iframes
                                        signIn('google')
                                    }
                                }}
                                disabled={isSigningIn}
                                className="w-full flex items-center justify-center gap-3 px-6 py-3.5 bg-white text-gray-700 rounded-lg hover:bg-gray-50 transition-all duration-200 font-medium shadow-md hover:shadow-lg transform hover:-translate-y-0.5 disabled:opacity-70 disabled:cursor-not-allowed disabled:transform-none border border-gray-300"
                            >
                                {isSigningIn ? (
                                    <>
                                        <svg className="animate-spin h-5 w-5" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                                            <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                                            <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                                        </svg>
                                        <span>Signing in...</span>
                                    </>
                                ) : (
                                    <>
                                        <svg className="w-5 h-5" viewBox="0 0 24 24">
                                            <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z" />
                                            <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" />
                                            <path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z" />
                                            <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z" />
                                        </svg>
                                        <span>Google</span>
                                    </>
                                )}
                            </button>
                        </div>

                        <div className="mt-8 pt-6 border-t border-gray-100 text-center">
                            <p className="text-xs text-gray-500">
                                By signing in, you agree to our Terms of Service
                            </p>
                        </div>
                    </div>
                </div>
            </div>
        )
    }

    return (
        <div className="flex h-screen bg-gray-50">
            {/* Main Layout */}
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
                                    <Image src="/banner.png" alt="Core Community" width={160} height={40} priority />
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
                                    <Image src="/banner.png" alt="Core Community" width={32} height={32} priority />
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
                                    {supportTabs.map(tab => {
                                        const isActive = activeSupportSubTab === tab.key
                                        return (
                                            <button
                                                key={tab.key}
                                                onClick={() => setActiveSupportSubTab(tab.key)}
                                                className={`w-full flex items-center gap-3 px-8 py-2.5 text-sm hover:bg-gray-700 transition-colors ${isActive ? 'bg-blue-600 text-white' : 'text-gray-300'
                                                    }`}
                                            >
                                                <span>{tab.label}</span>
                                            </button>
                                        )
                                    })}
                                </div>
                            )}
                        </div>
                    </nav>

                    {/* Sidebar Footer */}
                    <div className="border-t border-gray-700 p-4 space-y-3">
                        {user ? (
                            // Authenticated mode - show user info and controls (same for iframe and non-iframe)
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
                                                            {user.teams.map(t => t.name).join(', ')}
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
                                            onClick={() => signOut({ callbackUrl: '/' })}
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
                                                    <p>{user.teams.map(t => t.name).join(', ')}</p>
                                                </div>
                                            </div>
                                        </div>
                                        <button
                                            onClick={() => signOut({ callbackUrl: '/' })}
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
                    {/* Content */}
                    <div className="h-full">
                        {activeSupportSubTab === 'home' && <StatsPage />}
                        {activeSupportSubTab === 'tickets' && <TicketsPage />}
                        {activeSupportSubTab === 'escalations' && <EscalationsPage />}
                        {activeSupportSubTab === 'knowledge-gaps' && <KnowledgeGapsPage />}
                        {activeSupportSubTab === 'health' && hasFullAccess && <HealthPage />}
                        {activeSupportSubTab === 'sla' && <DashboardsPage />}
                    </div>
                </div>
            </div>
        </div>
    )
}
