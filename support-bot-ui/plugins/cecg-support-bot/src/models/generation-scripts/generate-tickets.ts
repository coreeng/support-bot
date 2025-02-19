import { Ticket, TicketStatus, StatusLog, Impact } from '../ticket';
import { Escalation, EscalationStatus } from '../escalation';
import { supportTeams } from '../data/example_teams';
import { UserWithTeams } from '../user';
import { DateTime } from 'luxon';
import { getRandom } from '../../../util/get-random';
import { tenantUsers, supportEngineers } from '../data/example_users';

export const apps = ['tv-service', 'schedule-service', 'native-app-analytics'];
export const platforms = ['AWS', 'Azure', 'GCP', 'On-Prem'];
export const environments = ['dev', 'dev-analytics', 'test', 'pre-prod', 'production', 'shadow-prod'];
export const channels = ['Core Support', 'Application Support', 'Cloud Support'];
const issues = [
  'Pod stuck in CrashLoopBackOff due to secret misconfiguration',
  'API server throttling requests due to quota limits',
  'Cloud provider API downtime impacting deployments',
  'Service account lacks necessary permissions for accessing resources',
  'PersistentVolume mount failure on nodes',
  'DNS resolution issues for internal services',
  'Load balancer health checks failing intermittently',
  'Node resource exhaustion due to rogue processes',
  'Cluster autoscaler stuck scaling down unnecessarily',
  'Pod image pull failures due to expired credentials',
];

function generateConversation(issue: string, resolved: boolean, queryUser: UserWithTeams, startDate: DateTime = DateTime.now()): StatusLog[] {
  const supportEngineer = getRandom(supportEngineers);

  const conversation = [
    {
      user: queryUser,
      status: TicketStatus.open,
      comment: `Query: "${issue}"`,
      timestamp: startDate,
    },
  ];

  if (resolved) {
    conversation.push(
      {
        user: supportEngineer,
        status: TicketStatus.resolved,
        comment: `Support: Resolved issue with query "${issue}". Fix details added to logs.`,
        timestamp: startDate.plus({ hours: 2 }),
      }
    );
  } else {
    conversation.push(
      {
        user: supportEngineer,
        status: TicketStatus.open,
        comment: `Support: Continuing investigation on issue "${issue}".`,
        timestamp: startDate.plus({ hours: 2 }),
      }
    );
  }

  return conversation;
}

let ticketCounter = 2315;
function generateTicketId(clientStr: string = "SKY"): string {
  const id = `${clientStr}-${ticketCounter}`;
  ticketCounter += 1;
  return id;
}

/**
 * Creates Escalation objects for a given ticket, respecting rules:
 *  - 50% chance to create one or more escalations
 *  - 15% chance (of those 50%) to create multiple escalations
 *  - If the ticket is resolved, escalations must resolve first
 *  - If the ticket is escalated, at least one escalation remains open
 */

let escalationCounter = 142;

function maybeGenerateEscalations(ticket: Ticket): Escalation[] {
  if (Math.random() < 0.5) {
    return [];
  }

  // 15% chance of multiple escalations, otherwise just one
  let teams = supportTeams;
  const multiple = Math.random() < 0.15;
  const count = multiple ? getRandom([2, 2, 2, 3]) : 1; // e.g. mostly 2, sometimes 3
  const escalations: Escalation[] = [];
  for (let i = 0; i < count; i++) {
    const specialistTeam = getRandom(teams);
    teams = teams.filter(t => t.name !== specialistTeam.name); // Ensure no duplicates
    // Create the escalation
    const escalation = new Escalation({
      id: `${escalationCounter}`,
      ticketId: ticket.id,
      createdTs: ticket.queryTs, // add some time?
      createdBy: getRandom(supportEngineers),
      specialistTeam
    });
    escalationCounter += 1;
    // By default, createNew() sets the escalation to open
    escalations.push(escalation);
  }

  return escalations;
}

function resolveEscalationsBeforeTicket(
  escalations: Escalation[],
  ticket: Ticket,
  finalResolveTime: DateTime,
) {
  // We'll resolve each escalation a bit earlier than the ticket
  // so they end before the ticket's final resolution
  // e.g. 1 hour before the ticket
  escalations.forEach(esc => {
    if (esc.status === EscalationStatus.Open) {
      esc.resolve(getRandom(supportEngineers), 'Escalation resolved prior to ticket resolution');
      // Overwrite the last resolution timestamp to ensure it's before the ticket
      const lastIndex = esc.statusHistory.length - 1;
      esc.statusHistory[lastIndex].timestamp = finalResolveTime.minus({ hours: 1 });
    }
  });
}

/**
 * Generates tickets with optional escalations that follow rules:
 *  - Ticket can't be resolved unless all escalations are resolved
 *  - If final ticket status is 'escalated', at least one escalation remains open
 */
function generateTickets(count: number): Ticket[] {
  const tickets: Ticket[] = [];
  const now = DateTime.now();

  for (let i = 0; i < count; i++) {
    const issue = getRandom(issues);
    const ticketId = generateTicketId();
    const queryTs = now.minus({
      days: Math.floor(Math.random() * 14),
      hours: Math.floor(Math.random() * 24),
    });
    const channelId = getRandom(channels);

    // Example tags
    const tags = [
      { value: getRandom(apps), label: 'App' },
      { value: getRandom(platforms), label: 'Platform' },
      { value: getRandom(environments), label: 'Environment' },
    ];

    const impact = getRandom(Object.values(Impact));
    const isBreakingProd = impact === Impact.BreakingProd;
    const isRecent = queryTs.diff(now, 'days').days >= -3; // Within last 3 days

    // Decide final ticket status
    // Breaking prod & recent => open or escalated
    // else => open or resolved
    let statusOptions: TicketStatus[];
    if (isBreakingProd && isRecent) {
      statusOptions = [TicketStatus.open, TicketStatus.escalated];
    } else {
      statusOptions = [TicketStatus.open, TicketStatus.resolved];
    }
    const finalStatus = getRandom(statusOptions);

    const createdBy = getRandom(tenantUsers);
    const resolved = finalStatus === TicketStatus.resolved;
    const statusHistory = generateConversation(issue, resolved, createdBy, queryTs);

    // Create the ticket
    const ticket = new Ticket({
      id: ticketId,
      channelId,
      queryTs,
      createdBy,
      team: getRandom([...createdBy.teams]),
      status: finalStatus,
      tags,
      impact,
      statusHistory
    });

    // Possibly add escalations
    const escalations = maybeGenerateEscalations(ticket);
    ticket.escalations = escalations;

    // Enforce relationship between ticket status & escalations
    if (finalStatus === TicketStatus.resolved) {
      // If ticket is resolved => must ensure all escalations resolved first
      if (escalations.length > 0) {
        // Find ticket's final resolution time from statusHistory
        const finalResolveLog = statusHistory.find(
          log => log.status === TicketStatus.resolved
        );
        if (finalResolveLog) {
          const ticketResolveTime = finalResolveLog.timestamp;
          // Resolve each escalation ~1 hour before ticket
          resolveEscalationsBeforeTicket(escalations, ticket, ticketResolveTime);
        }
      }
    } else if (finalStatus === TicketStatus.escalated) {
      // If final ticket status is 'escalated',
      // ensure at least one escalation remains open
      if (escalations.length > 0) {
        // We can decide to close some escalations but keep at least one open
        const keepOpen = getRandom(escalations);
        escalations.forEach(esc => {
          if (esc !== keepOpen && Math.random() < 0.5) {
            esc.resolve(getRandom(supportEngineers), 'Escalation resolved partially');
          }
        });
      }
    } else if (finalStatus === TicketStatus.open) {
      // If ticket is open, it can still have escalations
      // possibly keep them open or partially resolved. We'll do a coin toss:
      if (escalations.length > 0) {
        escalations.forEach(esc => {
          // 50% chance to close each escalation
          if (Math.random() < 0.5) {
            esc.resolve(getRandom(supportEngineers), 'Escalation resolved while ticket still open');
          }
        });
      }
    }

    // Re-check final states
    // if any open escalation remains, ticket must not be resolved
    const stillOpenEscalation = escalations.some(e => e.status === EscalationStatus.Open);
    if (stillOpenEscalation && ticket.status === TicketStatus.resolved) {
      // Force the ticket to be escalated or open
      ticket.status = getRandom([TicketStatus.open, TicketStatus.escalated]);
      // Could also adjust statusHistory if you want to reflect that
    }

    // push the ticket
    tickets.push(ticket);
  }

  return tickets;
}

// Generate 62 tickets
export const tickets = generateTickets(140);
