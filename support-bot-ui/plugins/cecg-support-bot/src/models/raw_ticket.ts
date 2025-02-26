// raw interfaces that come directly from the backstage backend plugin API, before being transformed in any way

export interface RawQuery {
    link: string;
    date: string; // ISO string format
}
  
export interface RawTeam {
    name: string;
    types: string[]; // Could be "tenant" or other types
}
  
export interface RawLog {
    date: string; // ISO string format
    event: string;
}
  
export interface RawEscalation {
// Define escalation properties if needed
}
  
export interface RawTicket {
    id: number;
    query: RawQuery;
    status: string; // Example: "opened" | "closed" | "stale"
    escalated: boolean;
    team: RawTeam | null; // Can be null
    impact: string | null; // Can be null, example: "productionBlocking" | "bauBlocking" | "abnormalBehaviour"
    tags: string[];
    logs: RawLog[];
    escalations: RawEscalation[];
}

export interface RawTicketResponse {
  items: RawTicket[];
}
