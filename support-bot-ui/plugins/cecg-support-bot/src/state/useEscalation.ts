import { useState, useEffect } from 'react';
import { supportBotApiRef } from '../api/SupportBotApi';
import { useApi } from '@backstage/core-plugin-api';
import { Escalation } from '../models/escalation';

export const useEscalations = () => {
  const ticketApi = useApi(supportBotApiRef);
  const [escalations, setEscalations] = useState<Escalation[] | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const fetchData = async () => {
      setLoading(true);
      setError(null);
      try {
        const data = await ticketApi.getEscalations();
        setEscalations(data);
      } catch (err) {
        setError(err.message || 'Failed to fetch escalations');
      } finally {
        setLoading(false);
      }
    };
    fetchData();
  }, [ticketApi]);

  return { escalations, loading, error };
};
