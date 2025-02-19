import { useState, useEffect } from 'react';
import { supportBotApiRef } from '../api/SupportBotApi';
import { useApi } from '@backstage/core-plugin-api';
import { SentimentSummary } from '../models/sentiment-summary';

export const useStats = () => {
  const ticketApi = useApi(supportBotApiRef);
  const [stats, setStats] = useState<SentimentSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const fetchData = async () => {
      setLoading(true);
      setError(null);
      try {
        const data = await ticketApi.getStats();
        setStats(data);
      } catch (err) {
        setError(err.message || 'Failed to fetch tickets');
      } finally {
        setLoading(false);
      }
    };
    fetchData();
  }, [ticketApi]);

  return { stats, loading, error };
};
