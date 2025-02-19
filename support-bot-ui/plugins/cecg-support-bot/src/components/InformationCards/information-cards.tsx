import React from 'react';
import { Card, CardContent, Typography, Grid } from '@material-ui/core';
import { Ticket } from '../../models/ticket';

type InformationCardsProps = {
  tickets: Ticket[];
};

export const InformationCards = ({ tickets }: InformationCardsProps) => {
  const totalTickets = tickets.length;
  const openTickets = tickets.filter(ticket => ticket.status === 'open').length;
  const escalatedTickets = tickets.filter(ticket => ticket.status === 'escalated').length;
  const resolvedTickets = tickets.filter(ticket => ticket.status === 'resolved').length;

  return (
    <Grid container spacing={2} style={{ marginBottom: '1rem' }}>
      <Grid item xs={3}>
        <Card>
          <CardContent>
            <Typography variant="h6">Total Tickets</Typography>
            <Typography variant="h4">{totalTickets}</Typography>
          </CardContent>
        </Card>
      </Grid>
      <Grid item xs={3}>
        <Card>
          <CardContent>
            <Typography variant="h6">Open Tickets</Typography>
            <Typography variant="h4">{openTickets}</Typography>
          </CardContent>
        </Card>
      </Grid>
      <Grid item xs={3}>
        <Card>
          <CardContent>
            <Typography variant="h6">Escalated Tickets</Typography>
            <Typography variant="h4">{escalatedTickets}</Typography>
          </CardContent>
        </Card>
      </Grid>
      <Grid item xs={3}>
        <Card>
          <CardContent>
            <Typography variant="h6">Resolved Tickets</Typography>
            <Typography variant="h4">{resolvedTickets}</Typography>
          </CardContent>
        </Card>
      </Grid>
    </Grid>
  );
};
