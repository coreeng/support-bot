"use client";

import { useTeamFilter } from "@/contexts/TeamFilterContext";
import { useAuth } from "@/hooks/useAuth";
import { useEscalations, useRegistry } from "@/lib/hooks";
import { useCallback, useMemo } from "react";
import { Cell, Legend, Pie, PieChart, ResponsiveContainer, Tooltip } from "recharts";

export default function EscalatedToMyTeamWidget() {
  const { isEscalationTeam, actualEscalationTeams } = useAuth();
  const { selectedTeam } = useTeamFilter();
  const { data: escalationsData, isLoading, error: escalationsError } = useEscalations();
  const { data: registryData } = useRegistry();

  // Only show when viewing one of the user's actual escalation teams
  const isViewingEscalationsOnly = useMemo(() => {
    if (!selectedTeam || actualEscalationTeams.length === 0) return false;
    return actualEscalationTeams.includes(selectedTeam);
  }, [selectedTeam, actualEscalationTeams]);

  // Count escalations escalated TO the currently selected escalation team
  // "Escalated TO my team" means escalations that were escalated TO your team (not tickets you own)
  const escalatedToMyTeam = useMemo(() => {
    if (!escalationsData?.content || !selectedTeam) return { total: 0, active: 0, resolved: 0 };

    // Filter escalations where the target team (escalated TO) matches the selected escalation team
    // Use case-insensitive comparison to handle format differences
    const toMyTeam = escalationsData.content.filter((esc) => {
      if (!esc.team?.name) return false;
      return esc.team.name.trim().toLowerCase() === selectedTeam.trim().toLowerCase();
    });

    return {
      total: toMyTeam.length,
      active: toMyTeam.filter((esc) => !esc.resolvedAt).length,
      resolved: toMyTeam.filter((esc) => esc.resolvedAt).length,
    };
  }, [escalationsData, selectedTeam]);

  // Get filtered escalations for this team
  // "Escalated TO my team" means escalations that were escalated TO your team (not tickets you own)
  const myTeamEscalations = useMemo(() => {
    if (!escalationsData?.content || !selectedTeam) return [];
    return escalationsData.content.filter((esc) => {
      if (!esc.team?.name) return false;
      return esc.team.name.trim().toLowerCase() === selectedTeam.trim().toLowerCase();
    });
  }, [escalationsData, selectedTeam]);

  // Escalations by Status
  const escalationsByStatus = useMemo(() => {
    const counts: Record<string, number> = {
      Active: 0,
      Resolved: 0,
    };
    myTeamEscalations.forEach((esc) => {
      if (esc.resolvedAt) {
        counts["Resolved"]++;
      } else {
        counts["Active"]++;
      }
    });
    return Object.entries(counts)
      .filter(([, value]) => value > 0)
      .map(([name, value]) => ({ name, value }));
  }, [myTeamEscalations]);

  const impactLabel = useCallback(
    (code?: string | null) => {
      if (!code) return "Not specified";
      const match = registryData?.impacts?.find((i: { code: string; label: string }) => i.code === code);
      return match?.label || code;
    },
    [registryData]
  );

  // Escalations by Impact
  const escalationsByImpact = useMemo(() => {
    const counts: Record<string, number> = {};
    myTeamEscalations.forEach((esc) => {
      const label = impactLabel(esc.impact || undefined);
      counts[label] = (counts[label] || 0) + 1;
    });
    return Object.entries(counts).map(([name, value]) => ({ name, value }));
  }, [myTeamEscalations, impactLabel]);

  const COLORS = ["#0088FE", "#00C49F", "#FFBB28", "#FF8042", "#A28EFF"];

  // Don't show if not in escalation team OR not viewing escalations team specifically
  if (!isEscalationTeam || !isViewingEscalationsOnly) {
    return null;
  }

  if (isLoading) {
    return (
      <div className="bg-card border-border rounded-lg border p-6 shadow-md">
        <p className="text-muted-foreground">Loading...</p>
      </div>
    );
  }

  if (escalationsError) {
    return (
      <div className="border-destructive/30 bg-destructive/10 text-destructive rounded-lg border p-4">
        <p className="font-semibold">Error loading escalations</p>
        <p className="mt-1 text-sm">Unable to load escalation data. Please try refreshing the page.</p>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Summary tiles */}
      <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
        <div className="bg-card rounded-xl border p-5">
          <p className="text-muted-foreground text-sm font-medium">Total Received</p>
          <p className="text-primary mt-2 font-mono text-3xl font-semibold tracking-tight tabular-nums">{escalatedToMyTeam.total}</p>
        </div>
        <div className="bg-card rounded-xl border p-5">
          <p className="text-muted-foreground text-sm font-medium">Active</p>
          <p className="text-warning mt-2 font-mono text-3xl font-semibold tracking-tight tabular-nums">{escalatedToMyTeam.active}</p>
        </div>
        <div className="bg-card rounded-xl border p-5">
          <p className="text-muted-foreground text-sm font-medium">Resolved</p>
          <p className="text-success mt-2 font-mono text-3xl font-semibold tracking-tight tabular-nums">{escalatedToMyTeam.resolved}</p>
        </div>
      </div>

      {/* Charts */}
      <div className="grid grid-cols-1 gap-6 md:grid-cols-2">
        <div className="bg-card rounded-xl border p-5">
          <h3 className="text-muted-foreground mb-3 text-sm font-medium">Escalations by Status</h3>
          <ResponsiveContainer width="100%" height={300}>
            <PieChart>
              <Pie data={escalationsByStatus} dataKey="value" nameKey="name" cx="50%" cy="40%" outerRadius={80} fill="#8884d8" label>
                {escalationsByStatus.map((entry, index) => (
                  <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                ))}
              </Pie>
              <Tooltip
                contentStyle={{
                  background: "var(--popover)",
                  color: "var(--popover-foreground)",
                  border: "1px solid var(--border)",
                  borderRadius: "8px",
                  boxShadow: "0 4px 12px rgba(0,0,0,0.15)",
                }}
                itemStyle={{ color: "var(--popover-foreground)" }}
                labelStyle={{ color: "var(--popover-foreground)" }}
              />
              <Legend verticalAlign="bottom" height={60} />
            </PieChart>
          </ResponsiveContainer>
        </div>

        <div className="bg-card rounded-xl border p-5">
          <h3 className="text-muted-foreground mb-3 text-sm font-medium">Escalations by Impact</h3>
          <ResponsiveContainer width="100%" height={300}>
            <PieChart>
              <Pie data={escalationsByImpact} dataKey="value" nameKey="name" cx="50%" cy="40%" outerRadius={80} fill="#82ca9d" label>
                {escalationsByImpact.map((entry, index) => (
                  <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                ))}
              </Pie>
              <Tooltip
                contentStyle={{
                  background: "var(--popover)",
                  color: "var(--popover-foreground)",
                  border: "1px solid var(--border)",
                  borderRadius: "8px",
                  boxShadow: "0 4px 12px rgba(0,0,0,0.15)",
                }}
                itemStyle={{ color: "var(--popover-foreground)" }}
                labelStyle={{ color: "var(--popover-foreground)" }}
              />
              <Legend verticalAlign="bottom" height={60} />
            </PieChart>
          </ResponsiveContainer>
        </div>
      </div>
    </div>
  );
}
