"use client";

import * as React from "react";
import {
  type LucideIcon,
  AlertCircle,
  BarChart2,
  BarChart3,
  BookOpen,
  Home,
  Ticket,
} from "lucide-react";
import { usePathname } from "next/navigation";
import Link from "next/link";
import Image from "next/image";

import { NavMain } from "@/components/dashboard-layout/nav-main";
import {
  Sidebar,
  SidebarContent,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  useSidebar,
} from "@/components/ui/sidebar";
import { useAuth } from "@/hooks/useAuth";
import { useTeamFilter } from "@/contexts/TeamFilterContext";
import { useKnowledgeGapsEnabled } from "@/lib/hooks";

type NavItem = {
  title: string;
  url: string;
  icon: LucideIcon;
  isActive: boolean;
  items?: NavItem[];
};

type TabVisibility = {
  requiresFullAccess?: boolean;
  requiresFeatureFlag?: "knowledgeGaps" | "tenantInsights";
};

type SupportTab = {
  path: string;
  title: string;
  icon: LucideIcon;
  visibility?: TabVisibility;
};

const SUPPORT_TABS: SupportTab[] = [
  { path: "/", title: "Home", icon: Home },
  { path: "/tickets", title: "Tickets", icon: Ticket },
  { path: "/escalations", title: "Escalations", icon: AlertCircle },
  {
    path: "/knowledge-gaps",
    title: "Support Area Summary",
    icon: BookOpen,
    visibility: { requiresFullAccess: true, requiresFeatureFlag: "knowledgeGaps" },
  },
  {
    path: "/health",
    title: "Analytics & Operations",
    icon: BarChart2,
    visibility: { requiresFullAccess: true },
  },
  {
    path: "/sla",
    title: "SLA Dashboard",
    icon: BarChart3,
    visibility: { requiresFullAccess: true },
  },
  {
    path: "/tenant-requests",
    title: "Tenant Requests",
    icon: BarChart2,
    visibility: { requiresFullAccess: true, requiresFeatureFlag: "tenantInsights" },
  },
];

export function AppSidebar(props: React.ComponentProps<typeof Sidebar>) {
  const pathname = usePathname();
  const { state } = useSidebar();
  useAuth();
  const { hasFullAccess } = useTeamFilter();
  const { data: isKnowledgeGapsEnabled } = useKnowledgeGapsEnabled();
  const isTenantInsightsEnabled = true;

  const flags: Record<NonNullable<TabVisibility["requiresFeatureFlag"]>, boolean | undefined> = {
    knowledgeGaps: isKnowledgeGapsEnabled,
    tenantInsights: isTenantInsightsEnabled,
  };

  const isVisible = (tab: SupportTab) => {
    const v = tab.visibility;
    if (!v) return true;
    if (v.requiresFullAccess && !hasFullAccess) return false;
    if (v.requiresFeatureFlag && flags[v.requiresFeatureFlag] !== true) return false;
    return true;
  };

  const items: NavItem[] = SUPPORT_TABS.filter(isVisible).map((tab) => ({
    title: tab.title,
    url: tab.path,
    icon: tab.icon,
    isActive: pathname === tab.path,
  }));

  const navGroups = [{ label: "Support", items }];

  return (
    <Sidebar collapsible="icon" variant="inset" {...props}>
      <SidebarHeader>
        <SidebarMenu>
          <SidebarMenuItem>
            <SidebarMenuButton asChild className="data-[slot=sidebar-menu-button]:p-1.5!">
              <Link href="/">
                {state === "collapsed" ? (
                  <Image src="/favicon.ico" alt="Support Bot" width={20} height={20} priority />
                ) : (
                  <>
                    <Image
                      src="/logo-dark.png"
                      className="hidden dark:block"
                      alt="Support Bot"
                      width={170}
                      height={20}
                      priority
                    />
                    <Image
                      src="/logo.png"
                      className="block dark:hidden"
                      alt="Support Bot"
                      width={170}
                      height={20}
                      priority
                    />
                  </>
                )}
              </Link>
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarHeader>
      <SidebarContent>
        <NavMain groups={navGroups} />
      </SidebarContent>
    </Sidebar>
  );
}
