import React from 'react';
import {
  Table,
  TableColumn
} from '@backstage/core-components';
import { Ticket } from '../../models/ticket';
import { useLocation, useNavigate } from 'react-router-dom';
import { Escalation } from '../../models/escalation';
import { useRouteRef } from '@backstage/core-plugin-api';
import { ticketRouteRef } from '../../routes';
import { EscalationDetail } from '../EscalationDetail/EscalationDetail';
import { cleanTeamName, Team } from '../../models/team';

type EscalationTableComponentProps = {
  escalations: Escalation[];
  tickets: Ticket[];
  filters: {
    status?: string;
    team?: string;
  }
  teams: Team[];
  onFilterChange?: (filters: { status?: string; team?: string }) => void;
};

type EscalationRow = {
  id: string,
  ticketId: string,
  thread: string,
  assignedTeam: string,
  dateCreated: string | null,
  dateResolved: string | null,
  isOpen: string,
  tags: string,
}

export const EscalationTableComponent = ({ escalations, tickets }: EscalationTableComponentProps) => {
  const navigate = useNavigate();
  const location = useLocation();
  const ticketsLink = useRouteRef(ticketRouteRef);

  const statusLookup: {[key: string]: string} = {
    Open: 'Open',
    Closed: 'Closed'
  };

  const escalationId = new URLSearchParams(location.search).get('escalationId');
  const selectedEscalation = escalationId ? escalations.find(escalation => escalation.id.toString() === escalationId) : null;
  const selectedEscalationTicket = selectedEscalation ? tickets.find(ticket => ticket.id === selectedEscalation.ticketId.toString()) : null;

  const handleRowClick = (escalation: EscalationRow | undefined) => {
    if (escalation) {
      navigate(`?escalationId=${escalation.id}`);
    }
  }
  const handleCloseModal = () => {
    navigate(location.pathname);
  }

  const columns: TableColumn<any>[] = [
    { title: 'Escalation ID', field: 'id', highlight: true, width: 'auto', grouping: false, type: 'numeric' },
    { title: 'Ticket ID', field: 'ticketId', width: 'auto', type: 'numeric',
        render: (rowData: EscalationRow | string, type) => {
          const ticketId =
            typeof rowData === 'string' ? rowData : rowData.ticketId;
          return type === "row"
              ? (<a href={`${ticketsLink()}?ticketId=${ticketId}`}>{ticketId}</a>)
              : (<span>{ticketId} <em>({escalations.filter(e => e.ticketId === ticketId).length} escalations)</em></span>)
        }
    },
    { title: 'Thread', field: 'thread', width: 'auto', filtering: false, render: (rowData) => <a href={rowData.thread}>{rowData.thread}</a> },
    { title: 'Date Created', field: 'dateCreated', type: 'datetime', width: 'auto'},
    { title: 'Escalation Team',
      field: 'assignedTeam',
      width: 'auto',
      defaultGroupOrder: 0,
      render: (rowData: EscalationRow | string, type): any => {
        const teamName = typeof rowData === 'string'
            ? rowData
            : rowData.assignedTeam;
        const teamNameToDisplay = cleanTeamName(teamName);
        return type === 'group'
          ? (<span>{teamNameToDisplay} <em>({escalations.filter(e => e.team.name === teamName).length} escalations, {escalations.filter(e => e.team.name === teamName && e.isOpen).length} open)</em></span>)
          : teamNameToDisplay;
      },
    },
    { title: 'Status', field: 'isOpen', lookup: statusLookup, width: '70px' }
  ];

  const data = escalations.map(escalation => {
    return {
      id: escalation.id,
      ticketId: escalation.ticketId,
      thread: escalation.threadLink,
      assignedTeam: escalation.team.name,
      dateCreated: escalation.openedAt.toRelative({ locale: 'en' }),
      dateResolved: escalation.resolvedAt ? escalation.resolvedAt.toRelative({ locale: 'en' }) : 'Unresolved',
      isOpen: escalation.isOpen ? "Open" : "Closed",
      tags: escalation.tags.join(", "),
    } as EscalationRow;
  });

  const rowStyle = (row: any, _index: number, _level: number): React.CSSProperties => {
    const isOpen = row.status === 'unresolved' || row.status === 'escalated';
    const isBreakingProd = row.impact === 'Breaking Prod';
    const requiresUrgentAttention = isOpen && isBreakingProd;
    return {
      backgroundColor: requiresUrgentAttention ? 'red' : undefined,
    };
  };

  const eligibleData = data.filter(_ => {
    return true;
  });

  return (
    <>
      <Table<EscalationRow>
        title="Support Ticket Escalations"
        options={{ search: true, paging: true, pageSize: 20, filtering: true, rowStyle, grouping: true }}
        columns={columns}
        data={eligibleData}
        onRowClick={(_, rowData) => handleRowClick(rowData)}
      />
      <EscalationDetail
        open={!!selectedEscalation}
        escalation={selectedEscalation}
        ticket={selectedEscalationTicket}
        onClose={handleCloseModal}
      />
    </>
  );
};
