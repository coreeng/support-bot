import React from 'react';
import { Table, TableColumn } from '@backstage/core-components';
import { Ticket } from '../../models/ticket';
import { TicketDetail } from '../TicketDetail/ticket-detail';
import { useLocation, useNavigate } from 'react-router-dom';
import { cleanTeamName, Team } from '../../models/team';
import { DateTime } from 'luxon';

type TicketTableComponentProps = {
  tickets: Ticket[];
  teams: Team[];
  filters: {
    status?: string;
    team?: string;
  };
  maxShown?: number;
  onFilterChange?: (filters: { status?: string; team?: string }) => void;
};

type TicketRow = {
  id: string,
  thread: string,
  creationTeam: string,
  dateCreated: DateTime,
  status: 'Opened' | 'Closed',
  impact: string,
  escalationCount: number,
};

export const TicketTableComponent = ({ tickets, teams, filters, maxShown, onFilterChange }: TicketTableComponentProps) => {
  const navigate = useNavigate();
  const location = useLocation();

  const teamLookup: {[key: string]: string} = teams.sort((a, b) => a.name.localeCompare(b.name)).reduce((acc: {[key: string]: string}, team) => {
    acc[team.name] = cleanTeamName(team.name);
    return acc;
  }, {});
  const statusLookup: {[key: string]: string} = {
    Open: 'Open',
    Closed: 'Closed'
  };

  const defaultTeamFilter = filters.team ? [filters.team] : teamLookup.values;

  const ticketId = new URLSearchParams(location.search).get('ticketId');
  const selectedTicket = ticketId ? tickets.find(ticket => ticket.id === ticketId) : null;

  const handleRowClick = (ticket: TicketRow) => {
    navigate(`?ticketId=${ticket.id}`);
  }
  const handleCloseModal = () => {
    navigate(location.pathname);
  }

  const columns: TableColumn<TicketRow>[] = [
    { title: 'Ticket ID', field: 'id', highlight: true, width: 'auto' },
    { title: 'Thread', field: 'thread', filtering: false, render: (rowData) => <a href={rowData.thread}>{rowData.thread}</a> },
    { 
      title: 'Creation Team',
      field: 'creationTeam',
      width: '250px',
      lookup: teamLookup,
      defaultFilter: defaultTeamFilter,
      render: (rowData) => `${cleanTeamName(rowData.creationTeam)}`,
    },
    {
      title: 'Date Created',
      field: 'queryTs',
      render: rowData => rowData.dateCreated.toRelative({ locale: 'en' }),
      defaultSort: 'desc',
      type: 'datetime',
      width: '80px',
      filtering: false
    },
    { title: 'Status', field: 'status', lookup: statusLookup, width: '70px',  },
    { title: 'Impact', field: 'impact', width: '100px' },
    { title: '# escalations', field: 'escalationCount', type: 'numeric', filtering: false },
  ];

  const data = tickets.map(ticket => {
    return {
      id: ticket.id,
      thread: ticket.thread,
      creationTeam: ticket.team?.name,
      dateCreated: ticket.dateCreated, // Assuming queryTs is a Luxon DateTime
      status: ticket.status == 'opened' ? 'Open' : 'Closed',
      impact: ticket.impact,
      escalationCount: ticket.escalations.length,
    } as TicketRow;
  });

  const rowStyle = (data: TicketRow, _index: number, _level: number): React.CSSProperties => {
    const isOpen = data.status === 'Opened';
    const isBreakingProd = data.impact === 'productionBreaking';
    const requiresUrgentAttention = isOpen && isBreakingProd;
    return {
      backgroundColor: requiresUrgentAttention ? 'red' : undefined,
    };
  };

  const eligibleData = data.filter(row => {
    let isEligible = filters.status ? row.status === filters.status : true;
    if (isEligible && filters.team && filters.team.length) {
      isEligible = filters.team.includes(row.creationTeam);
    }
    return isEligible;
  });

  return (
    <>
      <Table
        title="Support Tickets"
        options={{ search: true, paging: true, pageSize: maxShown || 20, filtering: true, rowStyle }}
        columns={columns}
        data={eligibleData}
        onRowClick={(_event, rowData) => handleRowClick(rowData!!)}
        onFilterChange={(filters) => {
          const statusFilter =  filters.find(filter => filter.column.field === 'status');
          const teamFilter = filters.find(filter => filter.column.field === 'creationTeam');
          let updatedFilters: {team?: string, status?: string} = {};
          if (statusFilter) updatedFilters.status = statusFilter.value;
          if (teamFilter) updatedFilters.team = teamFilter.value;
          if (statusFilter || teamFilter) onFilterChange?.(updatedFilters);
        }}
      />
      <TicketDetail
        open={!!selectedTicket}
        ticket={selectedTicket}
        onClose={handleCloseModal}
      />
    </>
  );
};
