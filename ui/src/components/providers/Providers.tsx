// src/components/providers/Providers.tsx
'use client'

import * as React from 'react'
import { ReactNode, useState } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

import { ThemeProvider } from './theme-provider'
import { AuthProvider } from '@/contexts/AuthContext'
import { TeamFilterProvider } from '@/contexts/TeamFilterContext'
import { IframeProvider } from '@/contexts/IframeContext'
import { AuthGuard } from '@/components/AuthGuard'

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
        <IframeProvider>
            <QueryClientProvider client={queryClient}>
                <AuthProvider>
                    <ThemeProvider attribute="class" defaultTheme="light" forcedTheme="light" disableTransitionOnChange>
                        <AuthGuard>
                            <TeamFilterProvider>
                                {children}
                            </TeamFilterProvider>
                        </AuthGuard>
                    </ThemeProvider>
                </AuthProvider>
            </QueryClientProvider>
        </IframeProvider>
    )
}
