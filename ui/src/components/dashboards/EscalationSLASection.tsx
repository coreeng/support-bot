// src/components/dashboards/EscalationSLASection.tsx
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { useEscalationTeams } from "@/lib/hooks";
import type {
  EscalationRate,
  EscalationTeamScorecardRow,
  EscalationTopic,
  ResolutionByEscalation,
  TeamEscalationRate,
  TimeBeforeEscalation,
} from "@/lib/types/dashboard";
import { formatDuration } from "@/lib/utils";
import { EscalationRateByTeam } from "./EscalationRateByTeam";
import { MetricCard } from "./MetricCard";
import { PercentileCard } from "./PercentileCard";
import { RefreshButton } from "./RefreshButton";
import { ResolutionComparison } from "./ResolutionComparison";
import { TimeSeriesChart } from "./TimeSeriesChart";

interface EscalationSLASectionProps {
  escalationRate: EscalationRate | undefined;
  resolutionEscalatedVsNot: ResolutionByEscalation[] | undefined;
  escalationTeamScorecard: EscalationTeamScorecardRow[] | undefined;
  escalationTopics: EscalationTopic[] | undefined;
  timeBeforeEscalation: TimeBeforeEscalation | undefined;
  escalationRateByTeam: TeamEscalationRate[] | undefined;
  isRefreshing: boolean;
  onRefresh: () => void;
}

function formatPercent(fraction: number): string {
  return `${(fraction * 100).toFixed(1)}%`;
}

function ticketCount(count: number): string {
  return `${count} ${count === 1 ? "ticket" : "tickets"}`;
}

function formatOptionalDuration(seconds: number | null | undefined): string {
  return seconds == null ? "—" : formatDuration(seconds);
}

export function EscalationSLASection({
  escalationRate,
  resolutionEscalatedVsNot,
  escalationTeamScorecard,
  escalationTopics,
  timeBeforeEscalation,
  escalationRateByTeam,
  isRefreshing,
  onRefresh,
}: EscalationSLASectionProps) {
  const { data: escalationTeams } = useEscalationTeams();

  const escalationCount = timeBeforeEscalation?.escalations ?? 0;
  const waitValue = (seconds: number | undefined) => (escalationCount > 0 && seconds != null ? formatDuration(seconds) : "—");

  const teamLabel = (code: string) => escalationTeams?.find((t) => t.name === code)?.label || code;

  // Tolerate a non-object payload (e.g. an empty-array mock) as "no data".
  const ratePoints = Array.isArray(escalationRate?.data) ? escalationRate.data : [];
  const totalTickets = ratePoints.reduce((sum, p) => sum + p.tickets, 0);
  const totalEscalated = ratePoints.reduce((sum, p) => sum + p.escalatedTickets, 0);
  const rateChartData = ratePoints.map((p) => ({
    week: p.date,
    rate: Math.round(p.rate * 1000) / 10,
    tickets: p.tickets,
    escalatedTickets: p.escalatedTickets,
  }));

  return (
    <div>
      <div className="mb-6 flex items-center justify-between">
        <div>
          <h2 className="text-foreground text-base font-semibold">Escalation Analysis</h2>
          <p className="text-muted-foreground text-sm">How often tickets escalate, how long escalations take, and where the time goes</p>
        </div>
        <RefreshButton onRefresh={onRefresh} isRefreshing={isRefreshing} />
      </div>

      <div className="mb-6 grid grid-cols-1 gap-6 lg:grid-cols-3">
        <div className="lg:col-span-2">
          <ResolutionComparison data={resolutionEscalatedVsNot} />
        </div>
        <div className="flex flex-col gap-6">
          <MetricCard
            title="Escalation Rate"
            value={totalTickets > 0 ? formatPercent(totalEscalated / totalTickets) : "—"}
            description={`${totalEscalated} of ${ticketCount(totalTickets)} opened in this range ${totalEscalated === 1 ? "was" : "were"} escalated`}
            colorScheme="orange"
          />
          <PercentileCard
            title="Time Before Escalating"
            p50={waitValue(timeBeforeEscalation?.p50)}
            p90={waitValue(timeBeforeEscalation?.p90)}
          />
        </div>
      </div>

      <div className="mb-6">
        <TimeSeriesChart
          title={`Escalation Rate Over Time (${escalationRate?.granularity === "WEEK" ? "weekly" : "daily"})`}
          data={rateChartData}
          lines={[{ dataKey: "rate", name: "Escalation rate", color: "var(--chart-7)" }]}
          yAxisLabel="% of tickets"
          tooltipFormatter={(value, name, item) => [
            `${Number(value).toFixed(1)}% (${item.payload?.escalatedTickets ?? 0} of ${item.payload?.tickets ?? 0})`,
            name,
          ]}
        />
      </div>

      <div className="mb-6 grid grid-cols-1 gap-6 xl:grid-cols-2">
        <EscalationRateByTeam data={escalationRateByTeam} />

        <div className="bg-card rounded-xl border p-6">
          <h2 className="text-foreground text-base font-semibold">Escalations by Receiving Team</h2>
          <p className="text-muted-foreground mb-4 text-sm">Escalations opened in this range, by the team they were escalated to.</p>
          <div className="overflow-hidden rounded-lg border">
            <Table>
              <TableHeader className="bg-muted">
                <TableRow>
                  <TableHead>Team</TableHead>
                  <TableHead className="text-right">Escalations</TableHead>
                  <TableHead className="text-right">Open</TableHead>
                  <TableHead className="text-right">Resolution P50</TableHead>
                  <TableHead className="text-right">Resolution P90</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {escalationTeamScorecard && escalationTeamScorecard.length > 0 ? (
                  escalationTeamScorecard.map((row) => (
                    <TableRow key={row.team}>
                      <TableCell className="text-foreground">{teamLabel(row.team)}</TableCell>
                      <TableCell className="text-right font-mono text-sm tabular-nums">{row.escalations}</TableCell>
                      <TableCell className="text-right font-mono text-sm tabular-nums">{row.open}</TableCell>
                      <TableCell className="text-right font-mono text-sm tabular-nums">
                        {formatOptionalDuration(row.resolutionP50)}
                      </TableCell>
                      <TableCell className="text-right font-mono text-sm tabular-nums">
                        {formatOptionalDuration(row.resolutionP90)}
                      </TableCell>
                    </TableRow>
                  ))
                ) : (
                  <TableRow>
                    <TableCell colSpan={5} className="text-muted-foreground p-16 text-center text-sm">
                      No escalations in this range
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </div>
        </div>
      </div>

      <div className="grid grid-cols-1 gap-6">
        <div className="bg-card rounded-xl border p-6">
          <h2 className="text-foreground text-base font-semibold">Most-Escalated Topics</h2>
          <p className="text-muted-foreground mb-4 text-sm">
            Escalation tags ranked by total time spent in escalation (count × average), resolved escalations only. Avg Wait Before is how
            long tickets sat before being manually escalated, across every manual escalation with that tag.
          </p>
          <div className="overflow-hidden rounded-lg border">
            <Table>
              <TableHeader className="bg-muted">
                <TableRow>
                  <TableHead className="w-12">#</TableHead>
                  <TableHead>Tag</TableHead>
                  <TableHead className="text-right">Escalations</TableHead>
                  <TableHead className="text-right">Avg Wait Before</TableHead>
                  <TableHead className="text-right">Avg In Escalation</TableHead>
                  <TableHead className="text-right">Total In Escalation</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {escalationTopics && escalationTopics.length > 0 ? (
                  escalationTopics.map((topic, index) => (
                    <TableRow key={topic.tag}>
                      <TableCell className="text-muted-foreground font-mono text-sm tabular-nums">{index + 1}</TableCell>
                      <TableCell className="text-foreground">{topic.tag}</TableCell>
                      <TableCell className="text-right font-mono text-sm tabular-nums">{topic.escalations}</TableCell>
                      <TableCell className="text-right font-mono text-sm tabular-nums">
                        {formatOptionalDuration(topic.avgWaitSeconds)}
                      </TableCell>
                      <TableCell className="text-right font-mono text-sm tabular-nums">{formatDuration(topic.avgSeconds)}</TableCell>
                      <TableCell className="text-right font-mono text-sm font-semibold tabular-nums">
                        {formatDuration(topic.totalSeconds)}
                      </TableCell>
                    </TableRow>
                  ))
                ) : (
                  <TableRow>
                    <TableCell colSpan={6} className="text-muted-foreground p-16 text-center text-sm">
                      No resolved, tagged escalations in this range
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </div>
        </div>
      </div>
    </div>
  );
}
