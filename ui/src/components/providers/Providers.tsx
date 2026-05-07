'use client'

import { ReactNode, useState } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { Toaster } from 'sonner'

import { SessionProvider } from './SessionProvider'
import { TeamFilterProvider } from '@/contexts/TeamFilterContext'

type ProvidersProps = {
    children: ReactNode
}

export function GlobalProviders({ children }: ProvidersProps) {
    const [queryClient] = useState(() => new QueryClient({
        defaultOptions: {
            queries: {
                staleTime: 1000 * 60 * 5,
            },
        },
    }))

    return (
        <SessionProvider>
            <QueryClientProvider client={queryClient}>
                <TeamFilterProvider>
                    {children}
                    <Toaster position="top-center" richColors duration={5000} closeButton />
                </TeamFilterProvider>
            </QueryClientProvider>
        </SessionProvider>
    )
}
