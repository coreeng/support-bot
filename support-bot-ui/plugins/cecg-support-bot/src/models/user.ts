import {isSupportTeam, Team} from './team';

export interface User {
  name: string;
  teams?: Team[];
}

export function isSupportUser(user: User): boolean {
  return user.teams
    ? user.teams.findIndex((t, _) => isSupportTeam(t)) !== -1
    : false;
}
