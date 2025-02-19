import React from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Typography,
  Divider,
  Box,
  Chip,
} from '@material-ui/core';
import { Ticket } from '../../models/ticket'; // Adjust import for your Ticket class

type TicketModalProps = {
  open: boolean;
  ticket: Ticket | null | undefined;
  onClose: () => void;
};

export const TicketDetail = ({ open, ticket, onClose }: TicketModalProps) => {
  if (!ticket) return null;

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>Ticket {ticket.id} Summary</DialogTitle>
      <DialogContent>
        {/* Ticket Summary */}
        <Typography variant="h6">Summary</Typography>
        <Typography variant="body1">
          <strong>Created At:</strong> {ticket.dateCreated.toRelative({locale: 'en'})}
        </Typography>
        <Typography variant="body1">
          <strong>Thread:</strong> <a href={ticket.thread}>{ticket.thread}</a>
        </Typography>
        <Typography variant="body1">
          <strong>Impact:</strong> {ticket.impact}
        </Typography>

        <Divider style={{ margin: '1em 0' }} />

        {/* Status History */}
        <Typography variant="h6">Status History</Typography>
        {ticket.logs.map((log, index) => (
          <Box key={index} style={{ marginBottom: '1em' }}>
            <Typography variant="body2">
              <Chip
                label={log.event}
                style={{
                  backgroundColor:
                    log.event === 'closed'
                      ? '#4caf50'
                      : '#f44336',
                  color: 'white',
                  marginRight: '0.5em',
                }}
              />
              {log.timestamp.toFormat('dd/MM/yyyy HH:mm')}
            </Typography>
          </Box>
        ))}

        <Divider style={{ margin: '1em 0' }} />

        {/* Modify Ticket */}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} color="default">
          Close
        </Button>
      </DialogActions>
    </Dialog>
  );
};
