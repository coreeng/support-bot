import { createApiRef, DiscoveryApi, IdentityApi } from '@backstage/core-plugin-api';
import { Ticket } from '../models/ticket';
import { RawTicketResponse, RawTicket } from '../models/raw_ticket';
import { Team } from '../models/team';
import { User, UserWithTeams } from '../models/user';
import { Escalation } from '../models/escalation';
import { DateTime } from 'luxon';
import { SentimentSummary } from '../models/sentiment-summary';

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
    const tickets = responseJson.items.map(item => Ticket.fromRawApi(item));
    return tickets;
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
    const responseJson = await response.json() as Team[];
    return responseJson;
  }

  async getEscalations(): Promise<Escalation[]> {
    const url = `${await this.discoveryApi.getBaseUrl('slackbot-api')}/escalations`;
    const { token } = await this.identityApi.getCredentials();
    const response = await fetch(url, {
      headers: {
        Authorization: `Bearer ${token}`,
      },
    });
    const responseJson = await response.json() as { items: Escalation[] };
    return responseJson.items.map(i => {
      let e = new Escalation(i);
      e.dateCreated = DateTime.fromISO(i["openedAt"]);
      e.resolvedAt = DateTime.fromISO(i["resolvedAt"]);
      return e;
    });
  }

  async getUserTeams(email_address: string): Promise<UserWithTeams> {
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
    const responseJson = await response.json();
    return responseJson;
  }
}
