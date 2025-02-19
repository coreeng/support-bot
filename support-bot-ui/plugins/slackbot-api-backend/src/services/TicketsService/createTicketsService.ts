import { LoggerService } from '@backstage/backend-plugin-api';
import { NotFoundError } from '@backstage/errors';
import { TicketsService } from './types';

export async function createTicketsService({
  logger
}: {
  logger: LoggerService;
}): Promise<TicketsService> {
  logger.info('Initializing Tickets service');

  return {
    async listTickets(): Promise<{ items: object[] }> {
      try {
          let allTickets: object[] = [];
          let currentPage = 0; // api pagination is zero-based for some reason
          let totalPages = 1;
          
          while (currentPage < totalPages) {
            const url = `http://localhost:8080/ticket?page=${currentPage}&pageSize=10`;
            const response = await fetch(url);
            if (!response.ok) {
              logger.error(`External API request failed with status ${response.status} for tickets`);
              throw new NotFoundError(`Could not fetch tickets; received status ${response.status}`);
            }
            const responseJson = await response.json();
            allTickets = [...allTickets, ...responseJson.content];
            totalPages = responseJson.totalPages;
            currentPage++;
          }
          return { items: allTickets };
      } catch (error) {
        logger.error(`Failed to fetch ticket data: ${error}`);
        throw error; // or throw a specific Backstage error, e.g., new NotFoundError(...)
      }
    },

    async getTicket(request: { id: string }) {
      try {
        const response = await fetch(`http://localhost:8080/ticket/${request.id}`);
        if (!response.ok) {
          logger.error(`External API request failed with status ${response.status} for ticket ID: ${request.id}`,);
          throw new NotFoundError(`Could not fetch ticket with ID ${request.id}; received status ${response.status}`);
        }
        const data = await response.json();
        return data;
      } catch (error) {
        logger.error(`Failed to fetch ticket data: ${error}`);
        throw error;
      }
    },

    async getTeams(): Promise<object[]> {
      try {
        const response = await fetch('http://localhost:8080/team');
        if (!response.ok) {
          logger.error(`External API request failed with status ${response.status} for teams`);
          throw new NotFoundError(`Could not fetch teams; received status ${response.status}`);
        }
        const data = await response.json() as object[];
        return data;
      } catch (error) {
        logger.error(`Failed to fetch team data: ${error}`);
        throw error;
      }
    },

    async getEscalations(): Promise<{ items: object[] }> {
      try {
          let allEscalations: object[] = [];
          let currentPage = 0; // api pagination is zero-based for some reason
          let totalPages = 1;
          
          while (currentPage < totalPages) {
            const url = `http://localhost:8080/escalation?page=${currentPage}&pageSize=10`;
            const response = await fetch(url);
            if (!response.ok) {
              logger.error(`External API request failed with status ${response.status} for tickets`);
              throw new NotFoundError(`Could not fetch tickets; received status ${response.status}`);
            }
            const responseJson = await response.json();
            allEscalations = [...allEscalations, ...responseJson.content];
            totalPages = responseJson.totalPages;
            currentPage++;
          }
          return { items: allEscalations };
      } catch (error) {
        logger.error(`Failed to fetch ticket data: ${error}`);
        throw error; // or throw a specific Backstage error, e.g., new NotFoundError(...)
      }
    },

    async getEscalation(request: { id: string }) {
      try {
        const response = await fetch(`http://localhost:8080/escalation/${request.id}`);
        if (!response.ok) {
          logger.error(`External API request failed with status ${response.status} for escalation ID: ${request.id}`,);
          throw new NotFoundError(`Could not fetch escalation with ID ${request.id}; received status ${response.status}`);
        }
        const data = await response.json();
        return data;
      } catch (error) {
        logger.error(`Failed to fetch team data: ${error}`);
        throw error;
      }
    },

    async getUserTeams(request: { email: string }) {
      try {
        const response = await fetch(`http://localhost:8080/user?email=${request.email}`);
        if (!response.ok) {
          logger.error(`External API request failed with status ${response.status} for user email: ${request.email}`,);
          throw new NotFoundError(`Could not fetch user teams for email ${request.email}; received status ${response.status}`);
        }
        const data = await response.json();
        return data;
      } catch (error) {
        logger.error(`Failed to fetch user team data: ${error}`);
        throw error;
      }
    },

    async getStats(): Promise<object> {
      try {
        const response = await fetch(`http://localhost:8080/stats`, {
          method: 'POST',
          headers: {
            "Content-Type": "application/json"
          },
          body: JSON.stringify([
            {
              "type": "ticket-sentiments-count",
              "from": "2025-01-13",
              "to": "2025-02-03"
            }
          ])
        });
        const data = await response.json();
        return data[0];
      } catch (error) {
        logger.error(`Failed to fetch stats data: ${error}`);
        throw error;
      }
      // try {
        const sentimentSummary = {
            "type": "ticket-sentiments-count",
            "from": "2025-01-13",
            "to": "2025-02-03",
            "values": [
              {
                "date": "2025-01-17",
                "authorSentiments": {
                  "positives": 0,
                  "neutrals": 3,
                  "negatives": 1
                },
                "supportSentiments": {
                  "positives": 0,
                  "neutrals": 1,
                  "negatives": 1
                },
                "othersSentiments": {
                  "positives": 2,
                  "neutrals": 0,
                  "negatives": 0
                }
              },
              {
                "date": "2025-01-18",
                "authorSentiments": {
                  "positives": 0,
                  "neutrals": 3,
                  "negatives": 1
                },
                "supportSentiments": {
                  "positives": 0,
                  "neutrals": 1,
                  "negatives": 1
                },
                "othersSentiments": {
                  "positives": 2,
                  "neutrals": 0,
                  "negatives": 0
                }
              },
              {
                "date": "2025-01-19",
                "authorSentiments": {
                  "positives": 0,
                  "neutrals": 3,
                  "negatives": 1
                },
                "supportSentiments": {
                  "positives": 0,
                  "neutrals": 1,
                  "negatives": 1
                },
                "othersSentiments": {
                  "positives": 2,
                  "neutrals": 0,
                  "negatives": 0
                }
              },
              {
                "date": "2025-01-20",
                "authorSentiments": {
                  "positives": 0,
                  "neutrals": 3,
                  "negatives": 1
                },
                "supportSentiments": {
                  "positives": 0,
                  "neutrals": 1,
                  "negatives": 1
                },
                "othersSentiments": {
                  "positives": 2,
                  "neutrals": 0,
                  "negatives": 0
                }
              },
              {
                "date": "2025-01-21",
                "authorSentiments": {
                  "positives": 0,
                  "neutrals": 3,
                  "negatives": 1
                },
                "supportSentiments": {
                  "positives": 0,
                  "neutrals": 1,
                  "negatives": 1
                },
                "othersSentiments": {
                  "positives": 2,
                  "neutrals": 0,
                  "negatives": 0
                }
              },
              {
                "date": "2025-01-22",
                "authorSentiments": {
                  "positives": 0,
                  "neutrals": 3,
                  "negatives": 1
                },
                "supportSentiments": {
                  "positives": 0,
                  "neutrals": 1,
                  "negatives": 1
                },
                "othersSentiments": {
                  "positives": 2,
                  "neutrals": 0,
                  "negatives": 0
                }
              },
              {
                "date": "2025-01-23",
                "authorSentiments": {
                  "positives": 0,
                  "neutrals": 3,
                  "negatives": 1
                },
                "supportSentiments": {
                  "positives": 0,
                  "neutrals": 1,
                  "negatives": 1
                },
                "othersSentiments": {
                  "positives": 2,
                  "neutrals": 0,
                  "negatives": 0
                }
              },
              {
                "date": "2025-01-24",
                "authorSentiments": {
                  "positives": 0,
                  "neutrals": 3,
                  "negatives": 1
                },
                "supportSentiments": {
                  "positives": 0,
                  "neutrals": 1,
                  "negatives": 1
                },
                "othersSentiments": {
                  "positives": 2,
                  "neutrals": 0,
                  "negatives": 0
                }
              },
              {
                "date": "2025-01-25",
                "authorSentiments": {
                  "positives": 0,
                  "neutrals": 3,
                  "negatives": 1
                },
                "supportSentiments": {
                  "positives": 0,
                  "neutrals": 1,
                  "negatives": 1
                },
                "othersSentiments": {
                  "positives": 2,
                  "neutrals": 0,
                  "negatives": 0
                }
              },
              {
                "date": "2025-01-26",
                "authorSentiments": {
                  "positives": 0,
                  "neutrals": 3,
                  "negatives": 1
                },
                "supportSentiments": {
                  "positives": 0,
                  "neutrals": 1,
                  "negatives": 1
                },
                "othersSentiments": {
                  "positives": 2,
                  "neutrals": 0,
                  "negatives": 0
                }
              },
              {
                "date": "2025-01-27",
                "authorSentiments": {
                  "positives": 0,
                  "neutrals": 3,
                  "negatives": 1
                },
                "supportSentiments": {
                  "positives": 0,
                  "neutrals": 1,
                  "negatives": 1
                },
                "othersSentiments": {
                  "positives": 2,
                  "neutrals": 0,
                  "negatives": 0
                }
              },
              {
                "date": "2025-01-28",
                "authorSentiments": {
                  "positives": 0,
                  "neutrals": 3,
                  "negatives": 1
                },
                "supportSentiments": {
                  "positives": 0,
                  "neutrals": 1,
                  "negatives": 1
                },
                "othersSentiments": {
                  "positives": 2,
                  "neutrals": 0,
                  "negatives": 0
                }
              },
              {
                "date": "2025-01-29",
                "authorSentiments": {
                  "positives": 0,
                  "neutrals": 3,
                  "negatives": 1
                },
                "supportSentiments": {
                  "positives": 0,
                  "neutrals": 1,
                  "negatives": 1
                },
                "othersSentiments": {
                  "positives": 2,
                  "neutrals": 0,
                  "negatives": 0
                }
              },
              {
                "date": "2025-01-30",
                "authorSentiments": {
                  "positives": 0,
                  "neutrals": 3,
                  "negatives": 1
                },
                "supportSentiments": {
                  "positives": 0,
                  "neutrals": 1,
                  "negatives": 1
                },
                "othersSentiments": {
                  "positives": 2,
                  "neutrals": 0,
                  "negatives": 0
                }
              },
              {
                "date": "2025-01-31",
                "authorSentiments": {
                  "positives": 0,
                  "neutrals": 3,
                  "negatives": 1
                },
                "supportSentiments": {
                  "positives": 0,
                  "neutrals": 1,
                  "negatives": 1
                },
                "othersSentiments": {
                  "positives": 2,
                  "neutrals": 0,
                  "negatives": 0
                }
              },
              {
                "date": "2025-02-01",
                "authorSentiments": {
                  "positives": 0,
                  "neutrals": 3,
                  "negatives": 1
                },
                "supportSentiments": {
                  "positives": 0,
                  "neutrals": 1,
                  "negatives": 1
                },
                "othersSentiments": {
                  "positives": 2,
                  "neutrals": 0,
                  "negatives": 0
                }
              },
              {
                "date": "2025-02-02",
                "authorSentiments": {
                  "positives": 0,
                  "neutrals": 3,
                  "negatives": 1
                },
                "supportSentiments": {
                  "positives": 0,
                  "neutrals": 1,
                  "negatives": 1
                },
                "othersSentiments": {
                  "positives": 2,
                  "neutrals": 0,
                  "negatives": 0
                }
              }
            ]
        };
        return Promise.resolve(sentimentSummary);
        // CURRENTLY HAVING A SERVER ISSUE

        // if (!response.ok) {
        //   logger.error(`External API request failed with status ${response.status} for user email: ${request.email}`,);
        //   throw new NotFoundError(`Could not fetch user teams for email ${request.email}; received status ${response.status}`);
        // }
        // const data = await response.json();
        // return data;
        
      // } catch (error) {
      //   logger.error(`Failed to fetch user team data: ${error}`);
      //   throw error;
      // }
    }

  };
}
