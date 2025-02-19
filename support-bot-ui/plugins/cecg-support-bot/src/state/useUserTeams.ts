import { useState, useEffect } from 'react';
import { supportBotApiRef } from '../api/SupportBotApi';
import { useApi } from '@backstage/core-plugin-api';
import { User } from '../models/user';
import { TeamType } from '../models/team';

export const useUserTeams = (current_user: User) => {
  const ticketApi = useApi(supportBotApiRef);
  const [user, setTeams] = useState<User>(current_user);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const fetchData = async () => {
      setLoading(true);
      setError(null);
      try {
        console.log(`useUserTeams:::::::Current User: `, current_user);
        const data = (await ticketApi.getUserTeams(user.name)) as {email: string, teams: {name: string, type: TeamType}[]};
        console.log(`useUserTeams:::::::User teams data: `, data);
        const newUser = { ...user, teams: data["teams"] };
        console.log(`useUserTeams:::::::New User: `, newUser);
        setTeams(newUser);
      } catch (err) {
        setError(err.message || 'Failed to fetch user teams');
      } finally {
        setLoading(false);
      }
    };
    fetchData();
  }, [ticketApi, current_user]);

  return { user, loading, error };
};
