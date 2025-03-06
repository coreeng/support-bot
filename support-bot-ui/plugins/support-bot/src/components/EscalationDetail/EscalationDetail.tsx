import React from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Typography,
  Divider,
  Chip,
} from '@material-ui/core';
import { Escalation } from '../../models/escalation';
import { Ticket } from '../../models/ticket';

type TicketModalProps = {
  open: boolean;
  ticket: Ticket | null | undefined;
  escalation: Escalation | null | undefined;
  onClose: () => void;
};

export const EscalationDetail = ({ open, escalation, ticket, onClose }: TicketModalProps) => {
  if (!escalation) return null;

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>Escalation ID: {escalation.id} Summary</DialogTitle>
      <DialogContent>
        {/* Ticket Summary */}
        <Typography variant="h6">Summary</Typography>
        <Typography variant="body1">
          <strong>Ticket Team</strong> {ticket?.team?.name}
        </Typography>
        <Typography variant="body1">
          <strong>Time:</strong> {escalation.openedAt.toLocaleString()}
        </Typography>

        <Divider style={{ margin: '1em 0' }} />

        {/* Status History */}
        <Typography variant="h6">Tags</Typography>
        {escalation.tags.map((tag, index) => (
          <Chip key={index} label={tag} style={{ marginRight: '0.5em' }} />
        ))}
{/* 
        {escalation.} */}
        {/* {escalation.map((log, index) => (
          <Box key={index} style={{ marginBottom: '1em' }}>
            <Typography variant="body2">
              <Chip
                label={log.status}
                style={{
                  backgroundColor:
                    log.status === 'resolved'
                      ? '#4caf50'
                      : '#f44336',
                  color: 'white',
                  marginRight: '0.5em',
                }}
              />
              {log.timestamp.toFormat('dd/MM/yyyy HH:mm')}
            </Typography>
            <Typography variant="body2">
              <strong>{log.status}</strong>
            </Typography>
          </Box>
        ))} */}

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
