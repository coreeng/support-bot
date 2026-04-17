'use client'

import { ShieldX, LogOut } from 'lucide-react'
import { useAuth } from '@/hooks/useAuth'

export function AccessDenied() {
    const { user, logout, isLoading } = useAuth()

    if (isLoading) return null

    return (
        <div className="flex items-center justify-center h-full bg-gray-50 min-h-[400px]">
            <div className="max-w-md w-full mx-4 text-center">
                <div className="bg-white rounded-2xl shadow-lg p-8 space-y-6">
                    <div className="inline-flex items-center justify-center w-16 h-16 rounded-full bg-amber-100">
                        <ShieldX className="w-8 h-8 text-amber-600" />
                    </div>
                    <div>
                        <h1 className="text-2xl font-semibold text-gray-900">Access Restricted</h1>
                        <p className="mt-2 text-gray-600">
                            Your account does not have the required role to view this page.
                            A <span className="font-medium">Support Engineer</span> or{' '}
                            <span className="font-medium">Leadership</span> role is required.
                        </p>
                    </div>
                    {user?.email && (
                        <p className="text-sm text-gray-500">
                            Signed in as <span className="font-medium text-gray-700">{user.email}</span>
                        </p>
                    )}
                    <div className="pt-2 space-y-3">
                        <p className="text-sm text-gray-500">
                            Contact your administrator to request the appropriate role.
                        </p>
                        <button
                            onClick={logout}
                            className="inline-flex items-center gap-2 px-4 py-2 bg-gray-100 hover:bg-gray-200 text-gray-700 rounded-lg transition-colors text-sm font-medium"
                        >
                            <LogOut className="w-4 h-4" />
                            Sign in with a different account
                        </button>
                    </div>
                </div>
            </div>
        </div>
    )
}

export function RequireDashboardAccess({ children }: { children: React.ReactNode }) {
    const { isLeadership, isSupportEngineer, isLoading } = useAuth()

    if (isLoading) {
        return (
            <div className="flex items-center justify-center h-screen bg-gray-50">
                <div className="text-center">
                    <div className="inline-block animate-spin rounded-full h-12 w-12 border-b-2 border-blue-500 mb-4" />
                    <p className="text-gray-600">Loading...</p>
                </div>
            </div>
        )
    }

    if (!isLeadership && !isSupportEngineer) {
        return <AccessDenied />
    }

    return <>{children}</>
}
