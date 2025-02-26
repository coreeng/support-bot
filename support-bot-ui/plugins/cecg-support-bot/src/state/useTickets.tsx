import { useState, useEffect } from 'react';
import { Ticket } from '../models/ticket';
import { supportBotApiRef } from '../api/SupportBotApi';
import { useApi } from '@backstage/core-plugin-api';
import {wrapError} from "../util/errors";

export const useTickets = () => {
  const ticketApi = useApi(supportBotApiRef);
  const [tickets, setTickets] = useState<Ticket[] | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);

  useEffect(() => {
    const fetchData = async () => {
      setLoading(true);
      setError(null);
      try {
        const data = await ticketApi.getTickets();
        setTickets(data);
      } catch (err) {
        setError(wrapError(err, 'Failed to fetch tickets'));
      } finally {
        setLoading(false);
      }
    };
    fetchData();
  }, [ticketApi]);

  return { tickets, loading, error };
};
