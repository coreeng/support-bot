// src/components/providers/Providers.tsx
'use client'

import { ReactNode, useState } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

import { ThemeProvider } from './theme-provider'
import { SessionProvider } from './SessionProvider'
import { TeamFilterProvider } from '@/contexts/TeamFilterContext'

type ProvidersProps = {
    children: ReactNode
}

export function GlobalProviders({ children }: ProvidersProps) {
    const [queryClient] = useState(() => new QueryClient({
        defaultOptions: {
            queries: {
                staleTime: 1000 * 60 * 5, // 5 minutes
            },
        },
    }))

    return (
        <SessionProvider>
            <QueryClientProvider client={queryClient}>
                <ThemeProvider attribute="class" defaultTheme="light" forcedTheme="light" disableTransitionOnChange>
                    <TeamFilterProvider>
                        {children}
                    </TeamFilterProvider>
                </ThemeProvider>
            </QueryClientProvider>
        </SessionProvider>
    )
}
