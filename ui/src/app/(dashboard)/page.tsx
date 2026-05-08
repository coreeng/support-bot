'use client'

import StatsPage from '@/components/stats/stats'
import { useAuth } from '@/hooks/useAuth'
import LoadingSkeleton from '@/components/LoadingSkeleton'

export default function Home() {
    const { isLoading } = useAuth()

    if (isLoading) {
        return <LoadingSkeleton />
    }

    return <StatsPage />
}
