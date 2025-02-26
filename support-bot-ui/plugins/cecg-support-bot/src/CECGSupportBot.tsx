import React, { useState, useEffect } from 'react';
import { Routes, Route, useLocation } from 'react-router-dom';

import { Page, Content, ResponseErrorPanel, Progress } from '@backstage/core-components';
import { useApi } from '@backstage/core-plugin-api';

import { tenantUsers } from './models/data/example_users';
import {Team} from './models/team';
import {isSupportUser, User} from './models/user';

import { useTickets, useEscalations, useStats, useTeams } from './state';
import { HomePage, TicketsPage, EscalationsPage, HealthAndStatsPage } from './pages';

import { NavigationTabs, getCurrentTab } from './components/NavigationTabs/NavigationTabs';
import { AppHeader } from './components/AppHeader/AppHeader';
import { supportBotApiRef } from './api/SupportBotApi';
import {wrapError} from "./util/errors";

export const CECGSupportBot = () => {
  const location = useLocation();
  const currentTab = getCurrentTab(location.pathname);
  const ticketApi = useApi(supportBotApiRef);

  const [currentUser, setCurrentUser] = useState<User>({ ...tenantUsers[0], teams: undefined });
  const [loadingUserTeams, setLoadingUserTeams] = useState(false);
  const [userTeamsError, setUserTeamsError] = useState<Error | null>(null);
  
  useEffect(() => {
    const fetchUser = async () => {
      setLoadingUserTeams(true);
      setUserTeamsError(null);
      try {
        console.log(`Fetching teams for user: ${currentUser.name}`);
        const data = await ticketApi.getUser(currentUser.name);
        console.log(`Fetched teams: `, data.teams);
        setCurrentUser(prev => ({ ...prev, teams: data.teams }));
      } catch (err) {
        if (err instanceof Error) {
          setUserTeamsError(wrapError(err, "Failed to fetch user teams"));
        }
      } finally {
        setLoadingUserTeams(false);
      }
    };
    fetchUser();
  }, [currentUser.name, ticketApi]); // Re-fetch teams when user changes

  const isSupport = isSupportUser(currentUser);

  const { tickets, loading: ticketsLoading, error: ticketsError } = useTickets();
  const { escalations, loading: escalationsLoading, error: escalationsError } = useEscalations();
  const { teams, loading: teamsLoading, error: teamsError } = useTeams();

  let possibleLoading = [loadingUserTeams, ticketsLoading, teamsLoading, escalationsLoading];
  let possibleErrors = [userTeamsError, ticketsError, teamsError, escalationsError];

  let { stats: stats, loading: statsLoading, error: statsError } = useStats();
  if (isSupport) {
    possibleLoading.push(statsLoading);
    possibleErrors.push(statsError);
  }

  const anyLoading = possibleLoading.some(e => e);
  const anyError = possibleErrors.some(e => !!e);
  
  console.log(`Current User: `, currentUser);
  console.log(`Tickets: `);
  console.table(tickets);
  console.log(`Escalations: `);
  console.table(escalations);
  console.log(`Teams: `, teams);
  console.table(teams);
  console.log(`Stats: `);
  console.table(stats);

  if (anyLoading) {
    return (
      <Page themeId="tool">
        <AppHeader currentUser={currentUser} onUserChange={setCurrentUser}/>
        <Content>
          <Progress />
        </Content>
      </Page>
    );
  }

  if (anyError) {
    return (
      <Page themeId="tool">
        <AppHeader currentUser={currentUser} onUserChange={setCurrentUser}/>
        <Content>
          {possibleErrors
            .filter(Boolean)
            .map((error, index) => (
              <ResponseErrorPanel key={index} error={error!!}/>
          ))}
        </Content>
      </Page>
    );
  }

  // TODO: do not load all the data that is available at the same time
  return (
    <Page themeId="tool">
      <AppHeader currentUser={currentUser} onUserChange={setCurrentUser}/>
      <Content>
        <NavigationTabs currentTab={currentTab} isSupport={isSupport}/>
        <Routes>
          <Route path="/" element={<HomePage tickets={tickets!!} escalations={escalations!!} user={currentUser}/>}/>
          <Route path="/tickets" element={<TicketsPage tickets={tickets!!} teams={teams as Team[]}/>}/>
          <Route path="/escalations" element={<EscalationsPage tickets={tickets!!} escalations={escalations!!} teams={teams!!}/>}/>
          {isSupport ? (
            <Route path="/health-and-stats" element={<HealthAndStatsPage tickets={tickets!!} teams={teams!!} stats={stats!!}/>}/>
          ) : null }
        </Routes>
      </Content>
    </Page>
  );
};
