'use client'

import { ShieldX, LogOut } from 'lucide-react'
import { useAuth } from '@/hooks/useAuth'

export function AccessDenied() {
 const { user, logout, isLoading } = useAuth()

 if (isLoading) return null

 return (
 <div className="flex items-center justify-center h-full bg-background min-h-[400px]">
 <div className="max-w-md w-full mx-4 text-center">
 <div className="bg-card rounded-2xl shadow-lg p-8 space-y-6">
 <div className="inline-flex items-center justify-center w-16 h-16 rounded-full bg-warning/10">
 <ShieldX className="w-8 h-8 text-warning" />
 </div>
 <div>
 <h1 className="text-2xl font-semibold text-foreground">Access Restricted</h1>
 <p className="mt-2 text-muted-foreground">
 Your account does not have the required role to view this page.
 A <span className="font-medium">Support Engineer</span> or{' '}
 <span className="font-medium">Leadership</span> role is required.
 </p>
 </div>
 {user?.email && (
 <p className="text-sm text-muted-foreground">
 Signed in as <span className="font-medium text-foreground">{user.email}</span>
 </p>
 )}
 <div className="pt-2 space-y-3">
 <p className="text-sm text-muted-foreground">
 Contact your administrator to request the appropriate role.
 </p>
 <button
 onClick={logout}
 className="inline-flex items-center gap-2 px-4 py-2 bg-muted hover:bg-muted text-foreground rounded-lg transition-colors text-sm font-medium"
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
 <div className="flex items-center justify-center h-screen bg-background">
 <div className="text-center">
 <div className="inline-block animate-spin rounded-full h-12 w-12 border-b-2 border-primary mb-4" />
 <p className="text-muted-foreground">Loading...</p>
 </div>
 </div>
 )
 }

 if (!isLeadership && !isSupportEngineer) {
 return <AccessDenied />
 }

 return <>{children}</>
}
