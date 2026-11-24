// src/components/dashboards/EscalationRateByTeam.tsx
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { useTenantTeams } from "@/lib/hooks";
import type { TeamEscalationRate } from "@/lib/types/dashboard";

interface EscalationRateByTeamProps {
  data: TeamEscalationRate[] | undefined;
}

export function EscalationRateByTeam({ data }: EscalationRateByTeamProps) {
  const { data: tenantTeams } = useTenantTeams();
  const teamLabel = (code: string) => tenantTeams?.find((t) => t.name === code)?.label || code;
  const rows = Array.isArray(data) ? data : [];

  return (
    <div className="bg-card rounded-xl border p-6">
      <h2 className="text-foreground text-base font-semibold">Escalation Rate by Requesting Team</h2>
      <p className="text-muted-foreground mb-4 text-sm">Teams whose tickets were escalated most in this range.</p>

      <div className="overflow-hidden rounded-lg border">
        <Table>
          <TableHeader className="bg-muted">
            <TableRow>
              <TableHead>Team</TableHead>
              <TableHead className="text-right">Tickets</TableHead>
              <TableHead className="text-right">Escalated</TableHead>
              <TableHead className="w-40">Rate</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {rows.length > 0 ? (
              rows.map((row) => {
                const percent = `${(row.rate * 100).toFixed(1)}%`;
                return (
                  <TableRow key={row.team}>
                    <TableCell className="text-foreground">{teamLabel(row.team)}</TableCell>
                    <TableCell className="text-right font-mono text-sm tabular-nums">{row.tickets}</TableCell>
                    <TableCell className="text-right font-mono text-sm tabular-nums">{row.escalatedTickets}</TableCell>
                    <TableCell>
                      <div className="flex items-center gap-2">
                        <div className="bg-muted h-2 flex-1 overflow-hidden rounded-full">
                          <div className="bg-chart-7 h-full rounded-full" style={{ width: `${Math.min(row.rate, 1) * 100}%` }} />
                        </div>
                        <span className="text-foreground w-14 text-right font-mono text-sm tabular-nums">{percent}</span>
                      </div>
                    </TableCell>
                  </TableRow>
                );
              })
            ) : (
              <TableRow>
                <TableCell colSpan={4} className="text-muted-foreground p-16 text-center text-sm">
                  No escalated tickets in this range
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </div>
    </div>
  );
}
