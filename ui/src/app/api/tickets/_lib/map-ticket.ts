interface BackendTeam {
  label?: string;
  code?: string;
  types?: string[];
}

export function mapTicket(ticket: Record<string, unknown>) {
  const team = ticket.team as BackendTeam | null;
  const escalations = ticket.escalations as
    | Array<{ team?: BackendTeam; [key: string]: unknown }>
    | undefined;

  return {
    ...ticket,
    summary: (ticket.summary as string) ?? null,
    id: String(ticket.id),
    team: team ? { name: team.code || team.label || "" } : null,
    escalations:
      escalations?.map((esc) => ({
        ...esc,
        id: String(esc.id),
        team: esc.team ? { name: esc.team.code || esc.team.label || "" } : null,
      })) ?? [],
  };
}
