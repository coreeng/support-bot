export interface TicketsService {
  listTickets(): Promise<{ items: object[] }>;

  getTicket(request: { id: string }): Promise<object>;

  getTeams(): Promise<object[]>;

  getEscalations(): Promise<{ items: object[] }>;

  getEscalation(request: { id: string }): Promise<object>;

  getUserTeams(request: { email: string }): Promise<object>;

  getStats(): Promise<object>;
}
