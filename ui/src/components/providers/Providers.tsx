// src/components/providers/Providers.tsx
'use client'

import * as React from 'react'
import { ReactNode, useState } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { SessionProvider } from 'next-auth/react'

import { ThemeProvider } from './theme-provider'
import { UserProvider } from '@/contexts/UserContext'
import { TeamFilterProvider } from '@/contexts/TeamFilterContext'
import { IframeProvider } from '@/contexts/IframeContext'

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
        <SessionProvider>
            <QueryClientProvider client={queryClient}>
                <ThemeProvider attribute="class" defaultTheme="light" forcedTheme="light" disableTransitionOnChange>
                    <UserProvider>
                        <TeamFilterProvider>
                            {children}
                        </TeamFilterProvider>
                    </UserProvider>
                </ThemeProvider>
            </QueryClientProvider>
        </SessionProvider>
        </IframeProvider>
    )
}
