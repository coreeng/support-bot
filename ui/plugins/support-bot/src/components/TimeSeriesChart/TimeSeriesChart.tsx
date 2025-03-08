import React, { useState } from 'react';
import {
  InfoCard
} from '@backstage/core-components';
import {
  Typography,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
} from '@material-ui/core';
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  Tooltip,
  CartesianGrid,
  ResponsiveContainer,
} from 'recharts';
import { DateTime } from 'luxon';
import { Ticket } from '../../models/ticket';
import { cleanTeamName, Team } from '../../models/team';

type ChartData = {
  day: string;
  teams: {
    [teamName: string]: number; // Dynamic keys for each team
  }
};

function findEarliestTicket(tickets: Ticket[]): Ticket {
  let earliest = tickets[0];
  for (const ticket of tickets) {
      if (ticket.dateCreated < earliest.dateCreated) {
      earliest = ticket;
      }
  }
  return earliest;
}

function getTicketResolvedTime(ticket: Ticket): DateTime {
  // Look in statusHistory for the first time status = 'resolved'
  const resolvedEvent = ticket.logs.find(t => t.event === "closed");
  if (resolvedEvent) {
    return resolvedEvent.timestamp;
  }

  // If not resolved, treat it as open indefinitely
  // You can pick a large future date or just use DateTime.now()
  return DateTime.now(); // ~1e16 ms in the future
}

export function aggregateActiveTickets(tickets: Ticket[]): { day: string; count: number }[] {
  if (!tickets || tickets.length === 0) {
    return [];
  }

  // 1. Figure out earliest day among all tickets
  const earliestTicket = findEarliestTicket(tickets);
  const earliestDay = earliestTicket.dateCreated.startOf('day');

  // 2. Figure out how many calendar days from earliestDay to today
  const today = DateTime.now().startOf('day');
  const daysBetween = Math.floor(today.diff(earliestDay, 'days').days);

  const now = DateTime.now(); // used for minus() calls
  const data: { day: string; count: number }[] = [];

  // 3. For each day from earliest to today...
  function wasTicketOpenOnDay(ticket: Ticket, dayStart: DateTime, dayEnd: DateTime): boolean {
    const openTime = ticket.dateCreated;           // The moment it was opened
    const resolvedTime = getTicketResolvedTime(ticket); // The moment it first resolved (or far future)
    // Overlap check:
    // openTime <= dayEnd  AND  resolvedTime > dayStart
    return openTime <= dayEnd && resolvedTime > dayStart;
  }
  for (let i = daysBetween; i >= 0; i--) {
    const label = i === 0 ? 'Today' : `-${i}d`;
    const dayStart = now.minus({ days: i }).startOf('day');
    const dayEnd = now.minus({ days: i }).endOf('day');

    // 4. Count tickets that were open at any point that day
    const count = tickets.filter(ticket =>
      wasTicketOpenOnDay(ticket, dayStart, dayEnd),
    ).length;

    data.push({ day: label, count: count });
  }

  return data;
}

// Example aggregator #1: "Tickets Opened" by day
// @ts-ignore
function aggregateTicketsOpened(tickets: Ticket[]) {
    const now = DateTime.now();
    const result: { day: string; count: number }[] = [];
    const earliest = findEarliestTicket(tickets).dateCreated.startOf('day');
    const today = DateTime.now().startOf('day');
    const daysBetween = Math.floor(today.diff(earliest, 'days').days);
    for (let i = daysBetween; i >= 0; i--) {
      const label = i === 0 ? 'Today' : `-${i}d`;
      const dayStart = now.minus({ days: i }).startOf('day');
      const dayEnd = now.minus({ days: i }).endOf('day');
      const count = tickets.filter(ticket => {
        return ticket.dateCreated >= dayStart && ticket.dateCreated <= dayEnd;
      }).length;
      result.push({
        day: label,
        count: count,
      });
    }
    return result;
}

function aggregateTicketsByTeam(tickets: Ticket[], teams: Team[], metric: 'opened' | 'active'): ChartData[] {
  if (!tickets || tickets.length === 0) return [];

  // Get the earliest ticket date
  const earliestDay = tickets.reduce((earliest, ticket) => {
    return ticket.dateCreated < earliest ? ticket.dateCreated : earliest;
  }, tickets[0].dateCreated).startOf('day');

  const today = DateTime.now().startOf('day');
  const daysBetween = Math.floor(today.diff(earliestDay, 'days').days);
  const now = DateTime.now();
  
  // Create empty dataset template
  const data: ChartData[] = Array.from({ length: daysBetween + 1 }, (_, i) => ({
    day: i === 0 ? 'Today' : `-${i}d`,
    teams: teams.sort((a, b) => a.name.localeCompare(b.name)).reduce((acc, team) => {
      acc[cleanTeamName(team.name)] = 0;
      return acc;
    }, {} as { [teamName: string]: number })
  }));

  // Helper function to check if a ticket was active on a given day
  function wasTicketOpenOnDay(ticket: Ticket, dayStart: DateTime, dayEnd: DateTime) {
    const resolvedTime = ticket.logs.find(log => log.event === "closed")?.timestamp || DateTime.now();
    return ticket.dateCreated <= dayEnd && resolvedTime > dayStart;
  }

  // Process tickets per team
  for (const ticket of tickets) {
    const teamName = ticket.team?.name || "Unknown Team"; // Default if no team
    for (let i = daysBetween; i >= 0; i--) {
      const dayStart = now.minus({ days: i }).startOf('day');
      const dayEnd = now.minus({ days: i }).endOf('day');

      // Count tickets based on selected metric
      const isRelevant = metric === 'opened'
        ? ticket.dateCreated >= dayStart && ticket.dateCreated <= dayEnd
        : wasTicketOpenOnDay(ticket, dayStart, dayEnd);

      if (isRelevant) {
        data[daysBetween - i].teams[cleanTeamName(teamName)] = (data[daysBetween - i].teams[cleanTeamName(teamName)] || 0) + 1;
      }
    }
  }
  return data;
}


type MultiMetricChartProps = {
  tickets: Ticket[];
  teams: Team[];
};

export function TimeSeriesChart({ tickets, teams }: MultiMetricChartProps) {
  const [metric, setMetric] = useState<'opened' | 'active'>('opened');
  
  const chartData = aggregateTicketsByTeam(tickets, teams, metric);

  const earliest = Math.abs(Math.round(findEarliestTicket(tickets).dateCreated.diffNow('days').days));

  return (
    <InfoCard title={
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Typography variant="h6">
          {metric === 'opened' ? `Tickets Opened (last ${earliest} days)` : `Active Tickets (last ${earliest} days)`}
        </Typography>

        {/* Dropdown to pick the metric */}
        <FormControl variant="outlined" size="small">
          <InputLabel>Metric</InputLabel>
          <Select
            value={metric}
            onChange={e => {
              setMetric(e.target.value as 'opened' | 'active')
            }}
            label="Metric"
          >
            <MenuItem value="opened">Opened Tickets</MenuItem>
            <MenuItem value="active">Active Tickets</MenuItem>
          </Select>
        </FormControl>
      </div>
    }>
      <div style={{ width: '100%', height: 240 }}>
        <ResponsiveContainer>
          <LineChart data={chartData}>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis dataKey="day" />
            <YAxis />
            <Tooltip />
            {teams.map((team, index) => (
              <Line
                type="monotone"
                dataKey={`teams.${cleanTeamName(team.name)}`}
                stroke={`hsl(${index * 40}, 70%, 50%)`}
                strokeWidth={2}
                key={team.name}
              />
            ))}
          </LineChart>
        </ResponsiveContainer>
      </div>
    </InfoCard>
  );
}
