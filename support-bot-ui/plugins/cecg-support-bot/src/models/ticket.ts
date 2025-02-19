import { DateTime } from 'luxon';
import { Escalation } from './escalation';
import { Team, TeamType } from './team';
import { RawTicket } from './raw_ticket';

export enum TicketStatus {
  open = 'opened',
  resolved = 'closed'
}

export enum Impact {
  BreakingProd = 'productionBlocking',
  BreakingBAU = 'Breaking BAU',
  AbnormalBehaviour = 'Abnormal Behaviour',
}

export interface Tag {
  value: string;
  label?: string;
}

export interface StatusLog {
  event: TicketStatus;
  timestamp: DateTime;
}

export class Ticket {
  id: string;
  thread: string;
  escalated: boolean;
  team?: Team;
  dateCreated: DateTime;
  status: TicketStatus;
  logs: StatusLog[];
  tags: Tag[];
  impact: Impact;
  escalations: Escalation[] = [];

  constructor({
    id,
    thread,
    escalated,
    team,
    dateCreated,
    status = TicketStatus.open,
    logs = [],
    tags = [],
    impact
  }: {
    id: string;
    thread: string;
    escalated: boolean;
    team?: Team;
    dateCreated: DateTime;
    status?: TicketStatus;
    logs: StatusLog[];
    tags?: Tag[];
    impact: Impact;
  }) {
    this.id = id;
    this.thread = thread;
    this.escalated = escalated;
    this.team = team;
    this.dateCreated = dateCreated;
    this.status = status;
    this.logs = logs;
    this.tags = tags;
    this.impact = impact;
  }

  static fromRawApi(raw: RawTicket): Ticket {
    return new Ticket({
      id: raw.id.toString(),
      thread: raw.query.link,
      escalated: raw.escalated,
      team: raw.team ? { name: raw.team.name, type: raw.team.type as TeamType } : undefined,
      dateCreated: DateTime.fromISO(raw.query.date),
      status: raw.status as TicketStatus,
      logs: raw.logs.map(log => ({ event: log.event as TicketStatus, timestamp: DateTime.fromISO(log.date) })),
      tags: [],
      impact: raw.impact as Impact,
    });
  }
}
