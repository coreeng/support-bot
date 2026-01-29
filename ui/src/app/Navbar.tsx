'use client'

import { useState } from 'react'
import { useUser } from '@/contexts/UserContext'
import { signIn, signOut } from 'next-auth/react'
import TeamSelector from '@/components/TeamSelector'

export default function Navbar() {
    const { user, isLoading, isAuthenticated } = useUser()
    const [isSigningIn, setIsSigningIn] = useState(false)

    if (isLoading) {
        return (
            <div className="flex justify-between items-center p-4 bg-gray-100 border-b">
                <h1 className="font-bold text-lg">Support Dashboard</h1>
                <div className="text-sm text-gray-500">Loading...</div>
            </div>
        )
    }

    if (!isAuthenticated || !user) {
        return (
            <div className="flex justify-between items-center p-4 bg-gray-100 border-b">
                <h1 className="font-bold text-lg">Support Dashboard</h1>
                <button
                    className="px-4 py-2 bg-blue-500 text-white rounded hover:bg-blue-600 transition-colors disabled:opacity-70 disabled:cursor-not-allowed flex items-center gap-2"
                    onClick={() => {
                        setIsSigningIn(true)
                        signIn('azure-ad')
                    }}
                    disabled={isSigningIn}
                >
                    {isSigningIn && (
                        <svg className="animate-spin h-4 w-4" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                            <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                            <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                        </svg>
                    )}
                    {isSigningIn ? 'Signing in...' : 'Sign in with Microsoft'}
                </button>
            </div>
        )
    }

    return (
        <div className="flex justify-between items-center p-4 bg-gray-100 border-b gap-4">
            <div>
                <h1 className="font-bold text-lg">Support Dashboard</h1>
                <p className="text-sm text-gray-600">{user.email}</p>
            </div>
            <div className="flex-1 flex justify-center">
                <TeamSelector />
            </div>
            <button
                className="px-4 py-2 bg-red-500 text-white rounded hover:bg-red-600 transition-colors"
                onClick={() => signOut({ callbackUrl: '/' })}
            >
                Sign Out
            </button>
        </div>
    )
}
