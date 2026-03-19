'use client'

import StatsPage from '@/components/stats/stats'
import { useAuth } from '@/hooks/useAuth'

export default function Home() {
    const { isLoading } = useAuth()

    // Show loading state while session is being fetched
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

    return <StatsPage />
}

