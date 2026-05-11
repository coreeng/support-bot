"use client";

import TeamSelector from "@/components/TeamSelector";
import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbPage,
  BreadcrumbSeparator,
} from "@/components/ui/breadcrumb";
import { ModeToggle } from "@/components/ui/mode-toggle";
import { Separator } from "@/components/ui/separator";
import { SidebarTrigger } from "@/components/ui/sidebar";
import { Home } from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import React from "react";
import { UserDropdown } from "./user-dropdown";

const SEGMENT_LABELS: Record<string, string> = {
  tickets: "Tickets",
  escalations: "Escalations",
  "knowledge-gaps": "Support Area Summary",
  health: "Analytics & Operations",
  sla: "SLA",
  "tenant-requests": "Tenant Requests",
};

function kebabToTitleCase(segment: string): string {
  if (!segment.includes("-")) return segment;
  return segment
    .split("-")
    .map((part) => (part ? part.charAt(0).toUpperCase() + part.slice(1) : part))
    .join(" ");
}

function renderBreadcrumb(pathname: string) {
  const segments = pathname.split("/").filter(Boolean);
  return (
    <Breadcrumb>
      <BreadcrumbList>
        <BreadcrumbItem>
          <Home className="h-[1.2rem] w-[1.2rem]" />
        </BreadcrumbItem>
        {segments.map((segment, idx) => {
          const href = "/" + segments.slice(0, idx + 1).join("/");
          const isLast = idx === segments.length - 1;
          const display = SEGMENT_LABELS[segment.toLowerCase()] ?? kebabToTitleCase(segment);
          return (
            <React.Fragment key={href}>
              <BreadcrumbSeparator />
              <BreadcrumbItem>
                {isLast ? (
                  <BreadcrumbPage>{display}</BreadcrumbPage>
                ) : (
                  <BreadcrumbLink asChild>
                    <Link href={href}>{display}</Link>
                  </BreadcrumbLink>
                )}
              </BreadcrumbItem>
            </React.Fragment>
          );
        })}
      </BreadcrumbList>
    </Breadcrumb>
  );
}

export function SiteHeader() {
  const pathname = usePathname();

  return (
    <header className="flex h-(--header-height) shrink-0 items-center gap-2 border-b transition-[width,height] ease-linear group-has-data-[collapsible=icon]/sidebar-wrapper:h-(--header-height)">
      <div className="flex w-full items-center gap-1 px-4 lg:gap-2 lg:px-6">
        <SidebarTrigger className="-ml-1" />
        <Separator orientation="vertical" className="mx-2 data-[orientation=vertical]:h-4" />
        <div className="hidden md:block">{renderBreadcrumb(pathname)}</div>
        <div className="ml-auto flex items-center gap-2">
          {/* `Button asChild` would forward props to ModeToggle's root, but that root is
              a Radix DropdownMenu (not a DOM element), so the responsive class would be
              dropped. Wrap with a real div instead. */}
          <div className="hidden sm:flex">
            <ModeToggle />
          </div>
          <TeamSelector />
          <UserDropdown />
        </div>
      </div>
    </header>
  );
}
