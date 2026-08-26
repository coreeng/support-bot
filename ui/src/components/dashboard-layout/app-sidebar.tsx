"use client";

import { type LucideIcon, Activity, AlertCircle, BookOpen, Cable, GaugeCircle, GitPullRequest, Home, Sparkles, Ticket } from "lucide-react";
import Image from "next/image";
import Link from "next/link";
import { usePathname } from "next/navigation";
import * as React from "react";

import { NavMain } from "@/components/dashboard-layout/nav-main";
import {
  Sidebar,
  SidebarContent,
  SidebarGroup,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarMenuSkeleton,
  useSidebar,
} from "@/components/ui/sidebar";
import { useAuth } from "@/hooks/useAuth";
import { type UiCapability, hasUiCapability, UI_CAPABILITIES } from "@/lib/auth/capabilities";
import { useElevateEnabled, useKnowledgeGapsEnabled, useSummaryEnabled, useTenantInsightsEnabled } from "@/lib/hooks";

type NavItem = {
  title: string;
  url: string;
  icon: LucideIcon;
  isActive: boolean;
  items?: NavItem[];
};

type TabVisibility = {
  requiredCapability?: UiCapability;
  requiresFeatureFlag?: "knowledgeGaps" | "summary" | "tenantInsights" | "elevate";
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
    visibility: { requiredCapability: UI_CAPABILITIES.VIEW_RESTRICTED_DASHBOARDS, requiresFeatureFlag: "knowledgeGaps" },
  },
  {
    path: "/summary",
    title: "Support Summary",
    icon: Sparkles,
    visibility: { requiredCapability: UI_CAPABILITIES.VIEW_RESTRICTED_DASHBOARDS, requiresFeatureFlag: "summary" },
  },
  {
    path: "/health",
    title: "Analytics & Operations",
    icon: Activity,
    visibility: { requiredCapability: UI_CAPABILITIES.VIEW_RESTRICTED_DASHBOARDS },
  },
  {
    path: "/sla",
    title: "SLA Dashboard",
    icon: GaugeCircle,
    visibility: { requiredCapability: UI_CAPABILITIES.VIEW_RESTRICTED_DASHBOARDS },
  },
  {
    path: "/tenant-requests",
    title: "Tenant Requests",
    icon: GitPullRequest,
    visibility: { requiredCapability: UI_CAPABILITIES.VIEW_RESTRICTED_DASHBOARDS, requiresFeatureFlag: "tenantInsights" },
  },
];

const INTEGRATION_TABS: SupportTab[] = [
  {
    path: "/elevate",
    title: "Elevate",
    icon: Cable,
    visibility: { requiredCapability: UI_CAPABILITIES.VIEW_RESTRICTED_DASHBOARDS, requiresFeatureFlag: "elevate" },
  },
];

export function AppSidebar(props: React.ComponentProps<typeof Sidebar>) {
  const pathname = usePathname();
  const { state } = useSidebar();
  const { user, isLoading } = useAuth();
  const { data: isKnowledgeGapsEnabled } = useKnowledgeGapsEnabled();
  const { data: isSummaryEnabled } = useSummaryEnabled();
  const { data: isTenantInsightsEnabled } = useTenantInsightsEnabled();
  const { data: isElevateEnabled } = useElevateEnabled();

  const flags: Record<NonNullable<TabVisibility["requiresFeatureFlag"]>, boolean | undefined> = {
    knowledgeGaps: isKnowledgeGapsEnabled,
    summary: isSummaryEnabled,
    tenantInsights: isTenantInsightsEnabled,
    elevate: isElevateEnabled,
  };

  const isVisible = (tab: SupportTab) => {
    const v = tab.visibility;
    if (!v) return true;
    if (v.requiredCapability && !hasUiCapability(user?.roles, v.requiredCapability)) return false;
    if (v.requiresFeatureFlag && flags[v.requiresFeatureFlag] !== true) return false;
    return true;
  };

  const mapTabs = (tabs: SupportTab[]): NavItem[] =>
    tabs.filter(isVisible).map((tab) => ({
      title: tab.title,
      url: tab.path,
      icon: tab.icon,
      isActive: pathname === tab.path,
    }));

  const navGroups = [
    { label: "Support", items: mapTabs(SUPPORT_TABS) },
    { label: "Integrations", items: mapTabs(INTEGRATION_TABS) },
  ].filter((group) => group.items.length > 0);

  return (
    <Sidebar collapsible="icon" variant="inset" {...props}>
      <SidebarHeader>
        <SidebarMenu>
          <SidebarMenuItem>
            <SidebarMenuButton asChild className="data-[slot=sidebar-menu-button]:p-1.5!">
              <Link href="/">
                {state === "collapsed" ? (
                  <Image src="/favicon.ico" alt="Core Community" width={20} height={20} priority />
                ) : (
                  <>
                    <Image src="/logo-dark.png" className="hidden dark:block" alt="Core Community" width={170} height={20} priority />
                    <Image src="/logo.png" className="block dark:hidden" alt="Core Community" width={170} height={20} priority />
                  </>
                )}
              </Link>
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarHeader>
      <SidebarContent aria-busy={isLoading || undefined}>
        {isLoading ? (
          <SidebarGroup>
            <SidebarGroupLabel>Support</SidebarGroupLabel>
            <SidebarMenu>
              {Array.from({ length: 7 }, (_, index) => (
                <SidebarMenuItem key={index}>
                  <SidebarMenuSkeleton showIcon />
                </SidebarMenuItem>
              ))}
            </SidebarMenu>
          </SidebarGroup>
        ) : (
          <NavMain groups={navGroups} />
        )}
      </SidebarContent>
    </Sidebar>
  );
}
