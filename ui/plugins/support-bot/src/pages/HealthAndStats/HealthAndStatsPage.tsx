import React from "react";
import { Ticket } from "../../models/ticket";
import { InfoCard } from '@backstage/core-components';
import { Grid, makeStyles, Typography } from '@material-ui/core';
import { TimeSeriesChart } from "../../components/TimeSeriesChart/TimeSeriesChart";
import { CategoryChart } from "../../components/CategoryChart/CategoryChart";
import { Team } from "../../models/team";
import { SentimentChart } from "../../components/SentimentChart/SentimentChart";
import { SentimentSummary } from "../../models/sentiment-summary";

type HealthAndStatsPageProps = {
    tickets: Ticket[];
    teams: Team[];
    stats: SentimentSummary;
};

export interface ChartDatum {
    day: string;
    openTickets: number;
}

// @ts-ignore
const useStyles = makeStyles(theme => ({
  filterContainer: {
    marginBottom: theme.spacing(4), // Adds vertical spacing around the filter container
    marginTop: theme.spacing(4),
  },
  filterDropdown: {
    padding: theme.spacing(2), // Adds padding inside the dropdown
  },
}));

export const HealthAndStatsPage = (props: HealthAndStatsPageProps) => {
    // const classes = useStyles();
    // const firstContactResolution = 61; // 61%
    // const repeatIssueRate = 16; // 16%
    const averageResponseTime = '1h 9m';
    const averageResolveTime = '4h 25m';
    const largestActiveTicketTime = '5d 6h';

    // const [selectedTeam, setSelectedTeam] = useState<string>("All");
    

    return (
      <>
        {/* Turn this into a multi select form, to allow filtering by teams but more than one at a time. */}
        
        {/* <Grid container spacing={3} style={{ marginBottom: 16 }} className={classes.filterContainer}>
          <Grid item xs={2}>
            <FormControl fullWidth>
              <InputLabel id="team-select-label">Filter by Team</InputLabel>
              <Select
                labelId="team-select-label"
                value={selectedTeam}
                onChange={e => setSelectedTeam(e.target.value)}
                className={classes.filterDropdown}
              >
                <MenuItem value="All">All Teams</MenuItem>
                {props.teams.map(team => (
                  <MenuItem value={team.name} key={team.name}>
                    {team.name}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
          </Grid>
        </Grid> */}

        <Grid container spacing={3}>
          {/* Left side: "Open Tickets" line chart + FCR / Repeat Rate */}
          <Grid item xs={12} md={8} style={{ width: '100%', height: 240 }}>
            <TimeSeriesChart tickets={props.tickets} teams={props.teams}/>

            <SentimentChart data={props.stats} />
          </Grid>

          {/* Right side: "Ticket Severity" bar chart + other stats */}
          <Grid item xs={12} md={4}>
            <CategoryChart tickets={props.tickets}/>

            <Grid container spacing={2} style={{ marginTop: 16 }}>
              <Grid item xs={12}>
                <InfoCard>
                  <div>
                    <Typography>
                      Avg Response Time
                    </Typography>
                    <Typography>
                      {averageResponseTime}
                    </Typography>
                  </div>
                </InfoCard>
              </Grid>
              <Grid item xs={12}>
                <InfoCard>
                  <div>
                    <Typography>
                      Avg Resolve Time
                    </Typography>
                    <Typography>
                      {averageResolveTime}
                    </Typography>
                  </div>
                </InfoCard>
              </Grid>
              <Grid item xs={12}>
                <InfoCard>
                  <div>
                    <Typography>
                      Largest Active Ticket
                    </Typography>
                    <Typography>
                      {largestActiveTicketTime}
                    </Typography>
                  </div>
                </InfoCard>
              </Grid>
            </Grid>
          </Grid>
        </Grid>
        </>
    )
}
