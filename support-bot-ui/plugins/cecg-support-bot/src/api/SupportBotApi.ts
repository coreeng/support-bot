import {
  createApiRef,
  DiscoveryApi,
  IdentityApi,
} from '@backstage/core-plugin-api';
import { Ticket } from '../models/ticket';
import { RawTicket, RawTicketResponse } from '../models/raw_ticket';
import { Team } from '../models/team';
import { Escalation } from '../models/escalation';
import { SentimentSummary } from '../models/sentiment-summary';
import { User } from '../models/user';

export const supportBotApiRef = createApiRef<SupportBotApi>({
  id: 'plugin.cecg.slackbot-api',
});

export class SupportBotApi {
  private readonly discoveryApi: DiscoveryApi;
  private readonly identityApi: IdentityApi;

  constructor({ discoveryApi, identityApi }: { discoveryApi: DiscoveryApi; identityApi: IdentityApi }) {
    this.discoveryApi = discoveryApi;
    this.identityApi = identityApi;
  }

  async getTickets(): Promise<Ticket[]> {
    const url = `${await this.discoveryApi.getBaseUrl('slackbot-api')}/tickets`;
    const { token } = await this.identityApi.getCredentials();
    const response = await fetch(url, {
      headers: {
        Authorization: `Bearer ${token}`,
      },
    });
    if (!response.ok) {
      throw new Error(`Failed to fetch tickets: ${response.statusText}`);
    }
    const responseJson = await response.json() as RawTicketResponse;
    return responseJson.items.map(item => Ticket.fromRawApi(item));
  }

  async getTicket(id: string): Promise<Ticket> {
    const url = `${await this.discoveryApi.getBaseUrl('slackbot-api')}/ticket/${id}`;
    const { token } = await this.identityApi.getCredentials();
    const response = await fetch(url, {
      headers: {
        Authorization: `Bearer ${token}`,
      },
    });
    const responseJson = await response.json() as RawTicket;
    return Ticket.fromRawApi(responseJson);
  }

  async getTeams(): Promise<Team[]> {
    const url = `${await this.discoveryApi.getBaseUrl('slackbot-api')}/teams`;
    const { token } = await this.identityApi.getCredentials();
    const response = await fetch(url, {
      headers: {
        Authorization: `Bearer ${token}`,
      },
    });
    return (await response.json()) as Team[];
  }

  async getEscalations(): Promise<Escalation[]> {
    const url = `${await this.discoveryApi.getBaseUrl('slackbot-api')}/escalations`;
    const { token } = await this.identityApi.getCredentials();
    const response = await fetch(url, {
      headers: {
        Authorization: `Bearer ${token}`,
      },
    });
    const responseJson = await response.json() as { items: any[] };
    return responseJson.items.map(i => new Escalation(i));
  }

  async getUser(email_address: string): Promise<User> {
    const url = `${await this.discoveryApi.getBaseUrl('slackbot-api')}/user?email=${email_address}`;
    const { token } = await this.identityApi.getCredentials();
    const response = await fetch(url, {
      headers: {
        Authorization: `Bearer ${token}`,
      },
    });
    const responseJson = await response.json();
    return {name: responseJson.email, teams: responseJson.teams };
  }

  async getStats(): Promise<SentimentSummary> {
    const url = `${await this.discoveryApi.getBaseUrl('slackbot-api')}/stats`;
    const { token } = await this.identityApi.getCredentials();
    const response = await fetch(url, {
      headers: {
        Authorization: `Bearer ${token}`,
      }
    });
    return await response.json();
  }
}
