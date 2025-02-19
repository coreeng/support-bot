import { useState, useEffect } from 'react';
import { supportBotApiRef } from '../api/SupportBotApi';
import { useApi } from '@backstage/core-plugin-api';
import { Team } from '../models/team';

export const useTeams = () => {
  const ticketApi = useApi(supportBotApiRef);
  const [teams, setTeams] = useState<Team[] | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const fetchData = async () => {
      setLoading(true);
      setError(null);
      try {
        const data = await ticketApi.getTeams();
        setTeams(data);
      } catch (err) {
        setError(err.message || 'Failed to fetch teams');
      } finally {
        setLoading(false);
      }
    };
    fetchData();
  }, [ticketApi]);

  return { teams, loading, error };
};
