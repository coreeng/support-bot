'use client'

import * as React from 'react'

import { AppSidebar } from '@/components/dashboard-layout/app-sidebar'
import { SiteHeader } from '@/components/dashboard-layout/site-header'
import { SidebarInset, SidebarProvider } from '@/components/ui/sidebar'

export function DashboardLayout({ children }: { children: React.ReactNode }) {
    return (
        <SidebarProvider
            style={
                {
                    '--header-height': 'calc(var(--spacing) * 12)',
                } as React.CSSProperties
            }
        >
            <AppSidebar />
            <SidebarInset className="z-50 min-w-0">
                <SiteHeader />
                <div className="flex flex-col gap-4 p-4 md:gap-6 md:p-6 flex-1 max-w-[2000px] w-full mx-auto">
                    {children}
                </div>
            </SidebarInset>
        </SidebarProvider>
    )
}
