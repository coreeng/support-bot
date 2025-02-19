import { Team, TeamType } from '../team';

export const clientTeams: Team[] = [
    { name: "Entertainment Production", type: TeamType.tenant },
    { name: "Sports Production", type: TeamType.tenant },
    { name: "Sky News", type: TeamType.tenant },
    { name: "Sky Glass", type: TeamType.tenant },
    { name: "Sky Protect", type: TeamType.tenant },
    { name: "NowTV", type: TeamType.tenant },
    { name: "Sky Go", type: TeamType.tenant },
    { name: "Netflix Integration Services", type: TeamType.tenant },
    { name: "Discovery+ Integration Services", type: TeamType.tenant },
    { name: "BT Sport Integration Services", type: TeamType.tenant },
    { name: "Dotcom & Client apps", type: TeamType.tenant },
    { name: "Mobile Services", type: TeamType.tenant },
    { name: "Broadband Services", type: TeamType.tenant },
    { name: "Business Services", type: TeamType.tenant },
    { name: "UK Network Infrastructure", type: TeamType.tenant },
];

export const supportTeams = [
    { name: "Platform Support", type: TeamType.firstLineSupport },
    { name: "Application Support", type: TeamType.firstLineSupport },
    { name: "Data Support", type: TeamType.firstLineSupport },
];

export const secondLineSupportTeams = [
    { name: "GCP Support", type: TeamType.secondLineSupport },
    { name: "AWS Support", type: TeamType.secondLineSupport },
    { name: "Networking Support", type: TeamType.secondLineSupport },
];

export const leadershipTeams = [
    { name: "Senior Platform Leadership", type: TeamType.leadership },
    { name: "Finance", type: TeamType.leadership }
];
