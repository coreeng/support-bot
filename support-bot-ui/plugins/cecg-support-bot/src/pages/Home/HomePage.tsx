import React from 'react';
import { Content } from '@backstage/core-components';
import { Grid, Typography } from '@material-ui/core';
import { Ticket } from '../../models/ticket';
import { Team } from '../../models/team';
import { UserWithTeams } from '../../models/user';
import { QuickAccessComponent } from '../../components/QuickAccess/QuickAccessComponent';
import { Escalation } from '../../models/escalation';

type HomePageProps = {
    tickets: Ticket[];
    teams: Team[];
    user: UserWithTeams;
    escalations: Escalation[];
};

export const HomePage = (props: HomePageProps) => {
    return (
      <Content>
        <Grid container direction="column" spacing={2}>
          <Grid item>
            <Typography variant="h4">
              Welcome, <span style={{ color: '#0404ff' }}>{props.user.name}</span>
            </Typography>
          </Grid>

          <Grid item>
            <Typography variant="body1" style={{ padding: 30 }}>
              Here you can quickly check your team's tickets.
            </Typography>
          </Grid>
        </Grid>

        {props.user.teams.map(team => {
          let teamTickets = props.tickets.filter(t => t.team?.name === team.name)
          for (let ticket of teamTickets) {
            ticket.escalations = props.escalations.filter(e => `${e.ticketId}` === ticket.id);
          }
          return teamTickets.length > 0 ? (
            <QuickAccessComponent team={team} tickets={teamTickets} key={team.name}/>
          ) : (
            <>
              <hr></hr>
              <Typography key={team.name} variant="h6" color="textSecondary">
                No tickets for team {team.name} 😊 <em>(...yet)</em>
              </Typography>
            </>
          )
        })}
      </Content>
    )
}
