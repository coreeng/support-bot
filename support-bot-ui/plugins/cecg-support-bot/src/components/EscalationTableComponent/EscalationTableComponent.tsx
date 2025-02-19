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

export const EscalationTableComponent = ({ escalations, tickets, teams, filters, onFilterChange }: EscalationTableComponentProps) => {
  const navigate = useNavigate();
  const location = useLocation();
  const ticketsLink = useRouteRef(ticketRouteRef);

  const teamLookup: {[key: string]: string} = teams.sort((a, b) => a.name.localeCompare(b.name)).reduce((acc: {[key: string]: string}, team: Team) => {
    if (!team || !team.name) return acc;
    acc[team.name] = cleanTeamName(team.name);
    return acc;
  }, {} as {[key: string]: string});
  const statusLookup: {[key: string]: string} = {
    Open: 'Open',
    Closed: 'Closed'
  };

  const escalationId = new URLSearchParams(location.search).get('escalationId');
  const selectedEscalation = escalationId ? escalations.find(escalation => escalation.id.toString() === escalationId) : null;
  const selectedEscalationTicket = selectedEscalation ? tickets.find(ticket => ticket.id === selectedEscalation.ticketId.toString()) : null;

  console.log(`Escalation Id: `, escalationId, typeof escalationId);
  console.log(`Selected Escalation: `, selectedEscalation, typeof selectedEscalation?.id);
  console.log(`Selected Escalation Ticket: `, selectedEscalationTicket);

  const handleRowClick = (ticket: Ticket) => {
    navigate(`?escalationId=${ticket.id}`);
  }
  const handleCloseModal = () => {
    navigate(location.pathname);
  }

  const columns: TableColumn[] = [
    { title: 'Escalation ID', field: 'id', highlight: true, width: 'auto', grouping: false, type: 'numeric' },
    { title: 'Ticket ID', field: 'ticketId', width: 'auto', type: 'numeric',
        render: (rowData, type) => (
          type == "row"
          ? (<a href={`${ticketsLink()}?ticketId=${rowData.ticketId}`}>{rowData.ticketId}</a>)
          : (<span>{rowData} <em>({escalations.filter(e => e.ticketId == rowData).length} escalations)</em></span>)
        )
    },
    { title: 'Thread', field: 'thread', width: 'auto', filtering: false, render: (rowData) => <a href={rowData.thread}>{rowData.thread}</a> },
    { title: 'Date Created', field: 'dateCreated', type: 'datetime', width: 'auto'},
    { title: 'Escalation Team',
      field: 'assignedTeam',
      lookup: teamLookup,
      width: 'auto',
      defaultGroupOrder: 0,
      render: (rowData, type) => {
        return type === 'group'
          ? (<span>{cleanTeamName(rowData)} <em>({escalations.filter(e => cleanTeamName(e.team.name) === rowData).length} escalations, {escalations.filter(e => cleanTeamName(e.team.name) === rowData && e.isOpen).length} open)</em></span>)
          : cleanTeamName(rowData.assignedTeam);
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
      dateCreated: escalation.dateCreated.toRelative({ locale: 'en' }),
      dateResolved: escalation.resolvedAt ? escalation.resolvedAt.toRelative({ locale: 'en' }) : 'Unresolved',
      isOpen: escalation.isOpen ? "Open" : "Closed",
      tags: escalation.tags.join(", "),
    };
  });

  const rowStyle = (data, index, level) => {
    const isOpen = data.status === 'unresolved' || data.status === 'escalated';
    const isBreakingProd = data.impact === 'Breaking Prod';
    const requiresUrgentAttention = isOpen && isBreakingProd;
    return {
      backgroundColor: requiresUrgentAttention ? 'red' : null,
    };
  };

  const eligibleData = data.filter(row => {
    return true;
  });

  return (
    <>
      <Table
        title="Support Ticket Escalations"
        options={{ search: true, paging: true, pageSize: 20, filtering: true, rowStyle, grouping: true }}
        columns={columns}
        data={eligibleData}
        onRowClick={(event, rowData) => handleRowClick(rowData)}
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
