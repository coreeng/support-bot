import { DateTime } from 'luxon';
import { User } from './user';
import { Team } from './team';

export enum EscalationStatus {
  Open = 'open',
  Resolved = 'resolved',
}

export interface EscalationStatusLog {
  status: EscalationStatus;
  timestamp: DateTime;
  updatedBy: User;
  comment?: string;
}

export class Escalation {
  id: string;
  ticketId: string;
  threadLink: string;
  openedAt: DateTime;
  resolvedAt: DateTime;
  team: Team;
  tags: string[];
  isOpen: boolean;

  constructor({
    id,
    ticketId,
    threadLink,
    openedAt,
    resolvedAt,
    team,
    tags,
  }: {
    id: string;
    ticketId: string;
    threadLink: string;
    openedAt: DateTime | string;
    resolvedAt: DateTime | string;
    team: Team;
    tags: string[];
  }) {
    this.id = id;
    this.ticketId = ticketId;
    this.threadLink = threadLink;
    this.openedAt = typeof openedAt == 'string' ? DateTime.fromISO(openedAt) : openedAt;
    this.resolvedAt = typeof resolvedAt == 'string' ? DateTime.fromISO(resolvedAt) : resolvedAt;
    this.team = team;
    this.tags = tags;
    this.isOpen = !resolvedAt;
  }
}
