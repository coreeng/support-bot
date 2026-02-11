'use client'

import { useAuth } from '@/hooks/useAuth'
import { useRouter } from 'next/navigation'
import TeamSelector from '@/components/TeamSelector'

export default function Navbar() {
    const { user, isLoading, isAuthenticated, logout } = useAuth()
    const router = useRouter()

    const handleLogout = () => {
        logout()
    }

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
                    className="px-4 py-2 bg-blue-500 text-white rounded hover:bg-blue-600 transition-colors"
                    onClick={() => router.push('/login')}
                >
                    Sign In
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
                onClick={handleLogout}
            >
                Sign Out
            </button>
        </div>
    )
}
