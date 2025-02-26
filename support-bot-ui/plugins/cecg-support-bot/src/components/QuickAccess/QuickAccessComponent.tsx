import React from 'react';
import {isSupportTeam, Team} from '../../models/team';
import { Ticket } from '../../models/ticket';
import { Button, Grid, Typography } from '@material-ui/core';
import {NavLink} from 'react-router-dom';
import { useRouteRef } from '@backstage/core-plugin-api';
import { escalationRouteRef, ticketRouteRef } from '../../routes';
import { QuickAccessTable } from './QuickAccessTable';
import { CategoryChart } from '../CategoryChart/CategoryChart';

type QuickAccessProps = {
    team: Team;
    tickets: Ticket[];
}

export const QuickAccessComponent = ({ team, tickets }: QuickAccessProps) => {
    const isSupport = isSupportTeam(team);
    const pageLink = useRouteRef(isSupport ? escalationRouteRef: ticketRouteRef);
    const teamParam = encodeURIComponent(team.name);
    const myItemsUrl = `${pageLink()}?team=${teamParam}`;

    const teamTotalTickets: number = tickets.length;
    const totalEscalations = tickets.map(t => t.escalations).flat().filter(e => e.team.name === team.name).length;

    return (
        <>
            <hr/>
            <Typography variant="h6">
                Quick Access for {team.name} team.
            </Typography>
            <Grid container spacing={3}>
                <Grid item xs={12} sm={6} md={3}>
                {isSupport ? (
                    <Button variant="contained" color="primary" component={NavLink} to={myItemsUrl}>
                        {team.name} Team's escalations ({totalEscalations})
                    </Button>
                ) : (
                    <Button variant="contained" color="primary" component={NavLink} to={myItemsUrl}>
                        {team.name} Team's tickets ({teamTotalTickets})
                    </Button>
                )}
                </Grid>
            </Grid>
            {isSupport ? null : (
                <Grid container spacing={3}>
                    <Grid item xs={12} md={8}>
                        <QuickAccessTable tickets={tickets}/>
                    </Grid>
                    <Grid item xs={12} md={4}>
                        <CategoryChart tickets={tickets}/>
                    </Grid>
                </Grid>
            )}            
            <br/>
        </>
    )
}
