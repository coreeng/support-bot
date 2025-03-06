import React from 'react';
import { Table, TableColumn } from '@backstage/core-components';
import { Ticket } from '../../models/ticket';
import { TicketDetail } from '../TicketDetail/ticket-detail';
import { useLocation, useNavigate } from 'react-router-dom';

type TicketTableComponentProps = {
  tickets: Ticket[];
};

export const QuickAccessTable = ({ tickets }: TicketTableComponentProps) => {
  const navigate = useNavigate();
  const location = useLocation();

  const ticketId = new URLSearchParams(location.search).get('ticketId');
  const selectedTicket = ticketId ? tickets.find(ticket => ticket.id === ticketId) : null;

  const handleRowClick = (ticket: Ticket) => {
    navigate(`?ticketId=${ticket.id}`);
  }
  const handleCloseModal = () => {
    navigate(location.pathname);
  }

  const columns: TableColumn<any>[] = [
    { title: 'Ticket ID', field: 'id', highlight: true, width: 'auto' },
    { title: 'Thread', field: 'thread', filtering: false,
      render: rowData => <a href={rowData.thread}>{rowData.thread}</a> },
    {
      title: 'Created At',
      field: 'dateCreated',
      render: rowData => rowData.dateCreated.toRelative({ locale: 'en' }),
      defaultSort: 'desc',
      type: 'datetime',
      width: '80px',
      filtering: false
    },
    { title: 'Impact', field: 'impact' },
    { title: 'Escalations', field: 'escalationCount', type: 'numeric', width: 'auto' }
  ];

  const data = tickets.map(ticket => {
    return {
      id: ticket.id,
      thread: ticket.thread,
      dateCreated: ticket.dateCreated, // Assuming queryTs is a Luxon DateTime
      team: ticket.team?.name,
      status: ticket.status,
      impact: ticket.impact,
      escalationCount: ticket.escalations.length,
    };
  });

  const rowStyle = (data: any, _index: number, _level: number): React.CSSProperties => {
    const isOpen = data.status === 'unresolved' || data.status === 'escalated';
    const isBreakingProd = data.impact === 'Breaking Prod';
    const requiresUrgentAttention = isOpen && isBreakingProd;
    return {
      backgroundColor: requiresUrgentAttention ? 'red' : undefined,
    };
  };

  const maxPageSize = Math.min(5, tickets.length);

  return (
    <>
      <Table
        title="Open Tickets"
        options={{ search: false, paging: true, pageSize: maxPageSize, filtering: false, rowStyle }}
        columns={columns}
        data={data}
        onRowClick={(_event, rowData) => handleRowClick(rowData)}
      />
      <TicketDetail
        open={!!selectedTicket}
        ticket={selectedTicket}
        onClose={handleCloseModal}
      />
    </>
  );
};
