/**
 * Consolidated hooks for client-side data fetching.
 * All hooks use React Query and call the Next.js API routes.
 */
import { useQuery } from "@tanstack/react-query";
import type {
  PaginatedTickets,
  PaginatedEscalations,
  EscalationTeam,
  SupportMember,
  TicketWithLogs,
  KnowledgeGapsStatus,
  AnalysisData,
} from "@/lib/types";

// ===== Shared API Helper =====

async function apiGet<T>(path: string): Promise<T> {
  const res = await fetch(`/api${path}`);
  if (!res.ok) {
    if (res.status === 401) {
      window.location.href = `/login?callbackUrl=${encodeURIComponent(window.location.pathname)}`;
    }
    throw new Error(`API error: ${res.status}`);
  }
  return res.json();
}

function buildParams(dateFrom?: string, dateTo?: string): string {
  const params = new URLSearchParams();
  if (dateFrom) params.append("dateFrom", dateFrom);
  if (dateTo) params.append("dateTo", dateTo);
  const queryString = params.toString();
  return queryString ? `?${queryString}` : "";
}

// ===== Ticket Hooks =====

export function useTickets(
  page: number = 0,
  pageSize: number = 50,
  from?: string,
  to?: string
) {
  return useQuery<PaginatedTickets>({
    queryKey: ["tickets", page, pageSize, from, to],
    queryFn: async () => {
      const params = new URLSearchParams({
        page: page.toString(),
        pageSize: pageSize.toString(),
      });
      if (from) params.append("dateFrom", from);
      if (to) params.append("dateTo", to);
      return apiGet(`/tickets?${params}`);
    },
  });
}

export function useAllTickets(
  pageSize: number = 200,
  from?: string,
  to?: string,
  enabled = true
) {
  return useQuery<PaginatedTickets>({
    queryKey: ["tickets", "all", pageSize, from, to],
    enabled,
    queryFn: async () => {
      const params = new URLSearchParams({
        page: "0",
        pageSize: pageSize.toString(),
      });
      if (from) params.append("dateFrom", from);
      if (to) params.append("dateTo", to);

      const first = await apiGet<PaginatedTickets>(`/tickets?${params}`);
      const totalPages = first.totalPages ?? 1;

      if (totalPages <= 1) return first;

      const pagesToFetch = [];
      for (let p = 1; p < totalPages; p++) {
        const pageParams = new URLSearchParams({
          page: p.toString(),
          pageSize: pageSize.toString(),
        });
        if (from) pageParams.append("dateFrom", from);
        if (to) pageParams.append("dateTo", to);
        pagesToFetch.push(apiGet<PaginatedTickets>(`/tickets?${pageParams}`));
      }

      const rest = await Promise.all(pagesToFetch);
      const allContent = [
        ...(first.content || []),
        ...rest.flatMap((res) => res.content || []),
      ] as TicketWithLogs[];

      return {
        page: 0,
        totalPages,
        totalElements: allContent.length,
        content: allContent,
      };
    },
  });
}

export function useTicket(id: string | undefined) {
  return useQuery<TicketWithLogs>({
    queryKey: ["ticket", id],
    queryFn: () => apiGet(`/tickets/${id}`),
    enabled: !!id,
  });
}

// ===== Team Hooks =====

export function useEscalationTeams(enabled: boolean = true) {
  return useQuery<EscalationTeam[]>({
    queryKey: ["team", "ESCALATION"],
    enabled,
    queryFn: () => apiGet("/teams?type=ESCALATION"),
  });
}

export interface Team {
  name: string;
}

export function useTenantTeams() {
  return useQuery<Team[]>({
    queryKey: ["team", "TENANT"],
    queryFn: () => apiGet("/teams?type=TENANT"),
  });
}

// ===== User Hooks =====

export function useSupportMembers() {
  return useQuery<SupportMember[]>({
    queryKey: ["user", "support"],
    queryFn: () => apiGet("/users/support"),
  });
}

// ===== Assignment Hooks =====

export function useAssignmentEnabled() {
  return useQuery<boolean>({
    queryKey: ["assignment", "enabled"],
    queryFn: async () => {
      const response = await apiGet<{ enabled: boolean }>("/assignment/enabled");
      return response.enabled;
    },
    staleTime: 5 * 60 * 1000,
  });
}

// ===== Escalation Hooks =====

export function useEscalations(pageSize: number = 50) {
  return useQuery<PaginatedEscalations>({
    queryKey: ["escalations", pageSize],
    queryFn: async () => {
      const first = await apiGet<PaginatedEscalations>(
        `/escalations?page=0&pageSize=${pageSize}`
      );
      const totalPages = first.totalPages ?? 1;

      if (totalPages <= 1) return first;

      const pagesToFetch = [];
      for (let p = 1; p < totalPages; p++) {
        pagesToFetch.push(
          apiGet<PaginatedEscalations>(
            `/escalations?page=${p}&pageSize=${pageSize}`
          )
        );
      }

      const rest = await Promise.all(pagesToFetch);
      const allContent = [
        ...(first.content || []),
        ...rest.flatMap((res) => res.content || []),
      ];

      return {
        page: 0,
        totalPages,
        totalElements: allContent.length,
        content: allContent,
      };
    },
  });
}

// ===== Registry & Ratings Hooks =====

export type RatingWeekly = {
  weekStart: string;
  average: number | null;
  count: number | null;
};
export type RatingsResult = {
  average: number | null;
  count: number | null;
  weekly?: RatingWeekly[];
};

export const useRatings = (from?: string, to?: string) => {
  return useQuery({
    queryKey: ["ratings", from, to],
    queryFn: async (): Promise<RatingsResult> => {
      const params = new URLSearchParams();
      if (from) params.append("from", from);
      if (to) params.append("to", to);
      const query = params.toString();
      return apiGet(`/stats/ratings${query ? `?${query}` : ""}`);
    },
  });
};

export interface Registry {
  impacts: { label: string; code: string }[];
  tags: { label: string; code: string }[];
}

export function useRegistry() {
  return useQuery<Registry>({
    queryKey: ["registry"],
    queryFn: () => apiGet("/registry"),
  });
}

// ===== Dashboard / SLA Hooks =====

export function useFirstResponseDurationDistribution(
  enabled = true,
  dateFrom?: string,
  dateTo?: string
) {
  return useQuery<number[]>({
    queryKey: ["firstResponseDurationDistribution", dateFrom, dateTo],
    queryFn: () =>
      apiGet(`/dashboard/first-response-distribution${buildParams(dateFrom, dateTo)}`),
    enabled,
  });
}

export function useFirstResponsePercentiles(
  enabled = true,
  dateFrom?: string,
  dateTo?: string
) {
  return useQuery<{ p50: number; p90: number }>({
    queryKey: ["firstResponsePercentiles", dateFrom, dateTo],
    queryFn: () =>
      apiGet(`/dashboard/first-response-percentiles${buildParams(dateFrom, dateTo)}`),
    enabled,
  });
}

export function useUnattendedQueriesCount(
  enabled = true,
  dateFrom?: string,
  dateTo?: string
) {
  return useQuery<{ count: number }>({
    queryKey: ["unattendedQueriesCount", dateFrom, dateTo],
    queryFn: () =>
      apiGet(`/dashboard/unattended-queries-count${buildParams(dateFrom, dateTo)}`),
    enabled,
  });
}

export type ResolutionDurationBucket = {
  label: string;
  count: number;
  minMinutes: number;
  maxMinutes: number;
};

export function useTicketResolutionPercentiles(
  enabled = true,
  dateFrom?: string,
  dateTo?: string
) {
  return useQuery<{ p50: number; p75: number; p90: number }>({
    queryKey: ["ticketResolutionPercentiles", dateFrom, dateTo],
    queryFn: () =>
      apiGet(`/dashboard/resolution-percentiles${buildParams(dateFrom, dateTo)}`),
    enabled,
  });
}

export function useTicketResolutionDurationDistribution(
  enabled = true,
  dateFrom?: string,
  dateTo?: string
) {
  return useQuery<ResolutionDurationBucket[], Error>({
    queryKey: ["ticketResolutionDurationDistribution", dateFrom, dateTo],
    queryFn: () =>
      apiGet(`/dashboard/resolution-duration-distribution${buildParams(dateFrom, dateTo)}`),
    enabled,
  });
}

export function useResolutionTimesByWeek(
  enabled = true,
  dateFrom?: string,
  dateTo?: string
) {
  return useQuery<{ week: string; p50: number; p75: number; p90: number }[]>({
    queryKey: ["resolutionTimesByWeek", dateFrom, dateTo],
    queryFn: () =>
      apiGet(`/dashboard/resolution-times-by-week${buildParams(dateFrom, dateTo)}`),
    enabled,
  });
}

export function useUnresolvedTicketAges(
  enabled = true,
  dateFrom?: string,
  dateTo?: string
) {
  return useQuery<{ p50: string; p90: string }>({
    queryKey: ["unresolvedTicketAges", dateFrom, dateTo],
    queryFn: () =>
      apiGet(`/dashboard/unresolved-ticket-ages${buildParams(dateFrom, dateTo)}`),
    enabled,
  });
}

export function useIncomingVsResolvedRate(
  enabled = true,
  dateFrom?: string,
  dateTo?: string
) {
  return useQuery<{ time: string; incoming: number; resolved: number }[]>({
    queryKey: ["incomingVsResolvedRate", dateFrom, dateTo],
    queryFn: () =>
      apiGet(`/dashboard/incoming-vs-resolved-rate${buildParams(dateFrom, dateTo)}`),
    enabled,
  });
}

export function useAvgEscalationDurationByTag(
  enabled = true,
  dateFrom?: string,
  dateTo?: string
) {
  return useQuery<{ tag: string; avgDuration: number }[]>({
    queryKey: ["avgEscalationDurationByTag", dateFrom, dateTo],
    queryFn: () =>
      apiGet(`/dashboard/avg-escalation-duration-by-tag${buildParams(dateFrom, dateTo)}`),
    enabled,
  });
}

export function useEscalationPercentageByTag(
  enabled = true,
  dateFrom?: string,
  dateTo?: string
) {
  return useQuery<{ tag: string; count: number }[]>({
    queryKey: ["escalationPercentageByTag", dateFrom, dateTo],
    queryFn: () =>
      apiGet(`/dashboard/escalation-percentage-by-tag${buildParams(dateFrom, dateTo)}`),
    enabled,
  });
}

export function useEscalationTrendsByDate(
  enabled = true,
  dateFrom?: string,
  dateTo?: string
) {
  return useQuery<{ date: string; escalations: number }[]>({
    queryKey: ["escalationTrendsByDate", dateFrom, dateTo],
    queryFn: () =>
      apiGet(`/dashboard/escalation-trends-by-date${buildParams(dateFrom, dateTo)}`),
    enabled,
  });
}

export function useEscalationsByTeam(
  enabled = true,
  dateFrom?: string,
  dateTo?: string
) {
  return useQuery<{ assigneeName: string; totalEscalations: number }[]>({
    queryKey: ["escalationsByTeam", dateFrom, dateTo],
    queryFn: () =>
      apiGet(`/dashboard/escalations-by-team${buildParams(dateFrom, dateTo)}`),
    enabled,
  });
}

export function useEscalationsByImpact(
  enabled = true,
  dateFrom?: string,
  dateTo?: string
) {
  return useQuery<{ impactLevel: string; totalEscalations: number }[]>({
    queryKey: ["escalationsByImpact", dateFrom, dateTo],
    queryFn: () =>
      apiGet(`/dashboard/escalations-by-impact${buildParams(dateFrom, dateTo)}`),
    enabled,
  });
}

export function useWeeklyTicketCounts(enabled = true) {
  return useQuery<
    { week: string; opened: number; closed: number; escalated: number; stale: number }[]
  >({
    queryKey: ["weeklyTicketCounts"],
    queryFn: () => apiGet("/dashboard/weekly-ticket-counts"),
    enabled,
  });
}

export function useWeeklyComparison(enabled = true) {
  return useQuery<
    { label: string; thisWeek: number; lastWeek: number; change: number }[]
  >({
    queryKey: ["weeklyComparison"],
    queryFn: () => apiGet("/dashboard/weekly-comparison"),
    enabled,
  });
}

export function useTopEscalatedTagsThisWeek(enabled = true) {
  return useQuery<{ tag: string; count: number }[]>({
    queryKey: ["topEscalatedTagsThisWeek"],
    queryFn: () => apiGet("/dashboard/top-escalated-tags-this-week"),
    enabled,
  });
}

export function useResolutionTimeByTag(
  enabled = true,
  startDate?: string,
  endDate?: string
) {
  return useQuery<{ tag: string; p50: number; p90: number }[]>({
    queryKey: ["resolutionTimeByTag", startDate, endDate],
    queryFn: () =>
      apiGet(`/dashboard/resolution-time-by-tag${buildParams(startDate, endDate)}`),
    enabled,
  });
}

// ===== Knowledge Gaps Hooks =====

export function useKnowledgeGapsEnabled() {
  return useQuery<boolean>({
    queryKey: ["knowledge-gaps", "enabled"],
    queryFn: async () => {
      const response = await apiGet<KnowledgeGapsStatus>(
        "/knowledge-gaps/enabled"
      );
      return response.enabled;
    },
    staleTime: 5 * 60 * 1000,
  });
}

export function useAnalysis() {
  return useQuery<AnalysisData>({
    queryKey: ["analysis"],
    queryFn: () => apiGet("/summary-data/results"),
    staleTime: 2 * 60 * 1000,
  });
}
