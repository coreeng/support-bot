import { Team } from './team';

export enum UserType {
  tenant = 'tenant',
  firstLineSupport = 'firstLineSupport',
  secondLineSupport = 'secondLineSupport',
  leadership = 'leadership',
}

export interface User {
  name: string;
  teams?: Team[];
}
