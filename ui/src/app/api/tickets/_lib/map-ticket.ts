interface BackendTeam {
  label?: string;
  code?: string;
  types?: string[];
  active?: boolean;
}

export function mapTicket(ticket: Record<string, unknown>) {
  const team = ticket.team as BackendTeam | null;
  const escalations = ticket.escalations as Array<{ team?: BackendTeam; [key: string]: unknown }> | undefined;

  return {
    ...ticket,
    summary: (ticket.summary as string) ?? null,
    id: String(ticket.id),
    team: team ? { name: team.code || team.label || "", label: team.label || team.code || "", active: team.active } : null,
    escalations:
      escalations?.map((esc) => ({
        ...esc,
        id: String(esc.id),
        team: esc.team
          ? { name: esc.team.code || esc.team.label || "", label: esc.team.label || esc.team.code || "", active: esc.team.active }
          : null,
      })) ?? [],
  };
}
